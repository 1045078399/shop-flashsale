package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.enums.RefundEnum;
import cn.wolfcode.feign.AlipayFeignApi;
import cn.wolfcode.feign.IntegralFeignApi;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderTimeoutMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.function.Function;

/**
 * Created by wolfcode
 */
@Slf4j
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IntegralFeignApi integralFeignApi;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private ApplicationContext ctx;

    @Override
    public OrderInfo selectByUserIdAndSeckillId(Long userId, Long seckillId, Integer time) {
        return orderInfoMapper.selectByUserIdAndSeckillId(userId, seckillId, time);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String doSeckill(Long userId, SeckillProductVo vo) {
        // 1. 扣除秒杀商品库存
        int row = seckillProductService.decrStockCount(vo.getTime(), vo.getId());
        if (row == 0) {
            // 库存不足
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        OrderInfo orderInfo = null;
        try {
            // 2. 创建秒杀订单并保存
            orderInfo = this.buildOrderInfo(userId, vo);
            orderInfoMapper.insert(orderInfo);
        } catch (Exception e) {
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        }
        // 3. 返回订单编号
        return orderInfo.getOrderNo();
    }

    @Override
    public OrderInfo findById(String orderNo) {
        String json = redisTemplate.opsForValue().get(SeckillRedisKey.SECKILL_ORDER_CACHE.join(orderNo));
        if (!StringUtils.isEmpty(json)) {
            return JSON.parseObject(json, OrderInfo.class);
        }
        return orderInfoMapper.selectById(orderNo);
    }

    @Override
    public void rollbackRedis(Long userId, Long seckillId, Integer time) {
        // 1. 回补 Redis 的库存
        this.rollbackRedisStock(userId, seckillId, time);
        // 2. 重置用户重复下单标识
        this.cancelUserOrderFlag(userId, seckillId);

        // 3. 取消本地标识 => 基于广播消息, 实现缓存同步
        this.sendCancelLocalStockFlag(seckillId);
    }

    private void sendCancelLocalStockFlag(Long seckillId) {
        rocketMQTemplate.syncSend(MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC, seckillId);
        log.info("[订单服务] 发送取消本地标识消息: {}", seckillId);
    }

    private void cancelUserOrderFlag(Long userId, Long seckillId) {
        String key = SeckillRedisKey.SECKILL_ORDER_HASH.join(userId + "");
        redisTemplate.opsForHash().put(key, seckillId + "", "0");
    }

    private void rollbackRedisStock(Long userId, Long seckillId, Integer time) {
        SeckillProductVo vo = seckillProductService.selectByIdAndTime(seckillId, time);
        log.info("[订单服务] 订单创建失败, 回滚 Redis. userId={}, seckillId={}, time={}, stockCoutn={}", userId, seckillId, time, vo.getStockCount());
        redisTemplate.opsForHash().put(
                SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(time + ""),
                seckillId + "",
                vo.getStockCount() + "");
    }

    @Override
    public void checkOrderPayTimeout(OrderTimeoutMessage message) {
        // 1. 基于订单id 查询订单对象
        // 2. 判断订单状态是否为未支付, 如果是未支付
        // 3. 订单状态修改为超时取消
        int ret = orderInfoMapper.updateCancelStatus(message.getOrderNo(), OrderInfo.STATUS_TIMEOUT);
        if (ret > 0) {
            log.info("[订单超时检查] 订单:{}, 已经超时, 即将进行回滚库存操作...", message.getOrderNo());
            // 4. MySQL 秒杀商品库存 +1
            seckillProductService.incrStockCount(message.getTime(), message.getSeckillId());
            // 5. 回补 Redis 的库存
            this.rollbackRedisStock(message.getUserPhone(), message.getSeckillId(), message.getTime());
            // 6. 重置用户重复下单标识
            this.cancelUserOrderFlag(message.getUserPhone(), message.getSeckillId());

            // 7. 取消本地标识 => 基于广播消息, 实现缓存同步
            this.sendCancelLocalStockFlag(message.getSeckillId());
        }
    }

    @Autowired
    private AlipayFeignApi alipayFeignApi;

    @Override
    public String alipay(String orderNo, Long userId) {
        // 1. 根据订单编号获取到订单信息
        OrderInfo order = this.findById(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        // 2. 封装支付请求参数
        PayVo pay = new PayVo();
        pay.setOutTradeNo(orderNo);
        pay.setSubject("[限时抢购] " + order.getProductName());
        pay.setTotalAmount(order.getSeckillPrice().toString());
        // 3. 调用阿里支付服务, 得到支付接口的响应, 最终响应给前端
        return alipayFeignApi.doPay(pay).data();
    }

    @Override
    public void paySuccess(String orderNo, Integer type) {
        if (type == OrderInfo.PAY_TYPE_ONLINE) {
            // 支付宝支付
            int ret = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, type);
            if (ret <= 0) {
                throw new BusinessException(SeckillCodeMsg.ORDER_CHANGE_PAY_STATUS_ERROR);
            }
            return;
        }
    }

    @Override
    public void refundByIntegral(OrderInfo orderInfo) {
        log.info("[积分退款] 开始对 {} 订单进行退款...", orderInfo.getOrderNo());
        // 退款流水记录
        RefundLog refundLog = new RefundLog();
        refundLog.setOutTradeNo(orderInfo.getOrderNo());
        refundLog.setRefundAmount(orderInfo.getIntergral().toString());
        String reason = "不想要了...";
        refundLog.setRefundReason(reason);
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(RefundLog.REFUND_TYPE_INTERGRAL);
        refundLog.setStatus(RefundLog.REFUND_STATUS_PENDING);
        refundLogMapper.insert(refundLog);

        // 调用积分服务进行退款
        OperateIntegralVo vo = new OperateIntegralVo(orderInfo.getOrderNo(), orderInfo.getIntergral(), reason, orderInfo.getUserId());
        Result<Boolean> result = integralFeignApi.refund(vo);

        // 判断支付宝退款结果
        if (result.data()) {
            // 退款成功
            // 1. 更新退款流水状态
            int ret = refundLogMapper.updateStatus(orderInfo.getOrderNo(), RefundLog.REFUND_STATUS_SUCCESS);
            if (ret > 0) {

                // 2. 更新订单状态
                ret = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
                if (ret > 0) {
                    // 3. 回补库存 + 取消重复下单标识 + 取消本地标识
                    seckillProductService.incrStockCount(orderInfo.getSeckillTime(), orderInfo.getSeckillId());
                    this.rollbackRedisStock(orderInfo.getUserId(), orderInfo.getSeckillId(), orderInfo.getSeckillTime());

                    // 重置用户重复下单标识
                    this.cancelUserOrderFlag(orderInfo.getUserId(), orderInfo.getSeckillId());

                    // 取消本地标识 => 基于广播消息, 实现缓存同步
                    this.sendCancelLocalStockFlag(orderInfo.getSeckillId());
                    log.info("[支付宝退款] 退款流程结束, 已经回补库存与本地标识...");
                }
            }
        }
    }

    @Override
    public void refund(String orderNo, String reason) {
        // 先基于订单编号查询订单对象
        OrderInfo orderInfo = this.findById(orderNo);
        int payType = orderInfo.getPayType();
        String payTypeName, refundAmount;
        RefundEnum refundEnum = RefundEnum.getByType(payType);

        if (refundEnum == null) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }

        // 获取进行远程调用
        Function<Object, Boolean> handler = ctx.getBean(refundEnum.getName(), Function.class);
        Object vo;

        if (payType == 0) {
            payTypeName = "支付宝";
            refundAmount = orderInfo.getSeckillPrice().toString();
            vo = new RefundVo(orderNo, refundAmount, reason);
        } else {
            payTypeName = "积分";
            refundAmount = orderInfo.getIntergral().toString();
            vo = new OperateIntegralVo(orderNo, Long.parseLong(refundAmount), reason, orderInfo.getUserId());
        }

        log.info("[订单退款] 开始对 {} 订单进行 {} 退款...", orderInfo.getOrderNo(), payTypeName);
        // 退款流水记录
        RefundLog refundLog = new RefundLog();
        refundLog.setOutTradeNo(orderInfo.getOrderNo());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundReason(reason);
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(payType);
        refundLog.setStatus(RefundLog.REFUND_STATUS_PENDING);
        refundLogMapper.insert(refundLog);

        // 调用积分服务进行退款
        Boolean result = handler.apply(vo);

        // 判断支付宝退款结果
        if (result) {
            // 退款成功
            // 1. 更新退款流水状态
            int ret = refundLogMapper.updateStatus(orderInfo.getOrderNo(), RefundLog.REFUND_STATUS_SUCCESS);
            if (ret > 0) {

                // 2. 更新订单状态
                ret = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
                if (ret > 0) {
                    // 3. 回补库存 + 取消重复下单标识 + 取消本地标识
                    seckillProductService.incrStockCount(orderInfo.getSeckillTime(), orderInfo.getSeckillId());
                    this.rollbackRedisStock(orderInfo.getUserId(), orderInfo.getSeckillId(), orderInfo.getSeckillTime());

                    // 重置用户重复下单标识
                    this.cancelUserOrderFlag(orderInfo.getUserId(), orderInfo.getSeckillId());

                    // 取消本地标识 => 基于广播消息, 实现缓存同步
                    this.sendCancelLocalStockFlag(orderInfo.getSeckillId());
                    log.info("[订单退款] {} 退款流程结束, 已经回补库存与本地标识...", payTypeName);
                }
            }
        }
    }

    @Override
    public void refundStockRollback(String orderNo) {
        OrderInfo orderInfo = this.findById(orderNo);
        // 退款成功
        // 1. 更新退款流水状态
        int ret = refundLogMapper.updateStatus(orderNo, RefundLog.REFUND_STATUS_SUCCESS);
        if (ret > 0) {

            // 2. 更新订单状态
            ret = orderInfoMapper.changeRefundStatus(orderNo, OrderInfo.STATUS_REFUND);
            if (ret > 0) {
                // 3. 回补库存 + 取消重复下单标识 + 取消本地标识
                seckillProductService.incrStockCount(orderInfo.getSeckillTime(), orderInfo.getSeckillId());
                this.rollbackRedisStock(orderInfo.getUserId(), orderInfo.getSeckillId(), orderInfo.getSeckillTime());

                // 重置用户重复下单标识
                this.cancelUserOrderFlag(orderInfo.getUserId(), orderInfo.getSeckillId());

                // 取消本地标识 => 基于广播消息, 实现缓存同步
                this.sendCancelLocalStockFlag(orderInfo.getSeckillId());
                log.info("[订单退款] 退款流程结束, 已经回补库存与本地标识...");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void integralRefund(String orderNo) {
        OrderInfo orderInfo = this.findById(orderNo);
        // 退款中日志记录
        this.insertRefundLog(orderInfo.getOrderNo(), orderInfo.getIntergral() + "", orderInfo.getPayType(), "不要了...");
        if (orderInfo != null) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        // 退款后执行的业务
        this.refundStockRollback(orderNo);
    }

    protected void insertRefundLog(String tradeNo, String refundAmount, Integer type, String reason) {
        // 退款流水记录
        RefundLog refundLog = new RefundLog();
        refundLog.setOutTradeNo(tradeNo);
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundReason(reason);
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(type);
        refundLog.setStatus(RefundLog.REFUND_STATUS_PENDING);
        refundLogMapper.insert(refundLog);
    }

    @Override
    public void refundByAlipay(OrderInfo orderInfo) {
        log.info("[支付宝退款] 开始对 {} 订单进行退款...", orderInfo.getOrderNo());
        // 退款流水记录
        RefundLog refundLog = new RefundLog();
        refundLog.setOutTradeNo(orderInfo.getOrderNo());
        refundLog.setRefundAmount(orderInfo.getSeckillPrice().toString());
        String reason = "不想要了...";
        refundLog.setRefundReason(reason);
        refundLog.setRefundTime(new Date());
        refundLog.setRefundType(RefundLog.REFUND_TYPE_ALIPAY);
        refundLog.setStatus(RefundLog.REFUND_STATUS_PENDING);
        refundLogMapper.insert(refundLog);

        // 远程调用支付服务, 进行退款
        RefundVo refundVo = new RefundVo(orderInfo.getOrderNo(), orderInfo.getSeckillPrice().toString(), reason);
        Result<Boolean> result = alipayFeignApi.refund(refundVo);

        // 判断支付宝退款结果
        if (result.data()) {
            // 退款成功
            // 1. 更新退款流水状态
            int ret = refundLogMapper.updateStatus(orderInfo.getOrderNo(), RefundLog.REFUND_STATUS_SUCCESS);
            if (ret > 0) {

                // 2. 更新订单状态
                ret = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
                if (ret > 0) {
                    // 3. 回补库存 + 取消重复下单标识 + 取消本地标识
                    seckillProductService.incrStockCount(orderInfo.getSeckillTime(), orderInfo.getSeckillId());
                    this.rollbackRedisStock(orderInfo.getUserId(), orderInfo.getSeckillId(), orderInfo.getSeckillTime());

                    // 重置用户重复下单标识
                    this.cancelUserOrderFlag(orderInfo.getUserId(), orderInfo.getSeckillId());

                    // 取消本地标识 => 基于广播消息, 实现缓存同步
                    this.sendCancelLocalStockFlag(orderInfo.getSeckillId());
                    log.info("[支付宝退款] 退款流程结束, 已经回补库存与本地标识...");
                }
            }
        }
    }

    @GlobalTransactional(rollbackFor = Exception.class)
    @Override
    public void integralPay(String orderNo, Long userId) {
        OrderInfo orderInfo = this.findById(orderNo);
        // 1. 远程调用积分服务, 扣除用户积分, 增加积分账户流水记录
        OperateIntegralVo vo = new OperateIntegralVo(orderNo, orderInfo.getIntergral(),
                "积分购买商品[" + orderInfo.getProductName() + "]", userId);
        Result<Boolean> result = integralFeignApi.decrIntegral(vo);
        if (!result.data()) {
            throw new BusinessException(SeckillCodeMsg.INTEGRAL_SERVER_ERROR);
        }
        log.info("[分布式事务测试] 远程执行完成, 本地准备抛出异常, 事务 xid={}", RootContext.getXID());
        // 2. 如果积分扣除成功, 积分余额足够, 修改订单状态为已支付
        orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAY_TYPE_INTEGRAL);
        // 3. 往积分支付日志表中增加一条流水记录
        PayLog payLog = new PayLog("", orderNo, new Date().getTime() + "",
                orderInfo.getIntergral() + "", PayLog.PAY_TYPE_INTERGRAL, "SUCCESS");
        payLogMapper.insert(payLog);
    }

    private OrderInfo buildOrderInfo(Long userId, SeckillProductVo vo) {
        Date now = new Date();
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCreateDate(now);
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setIntergral(vo.getIntergral());
        orderInfo.setOrderNo(IdGenerateUtil.get().nextId() + "");
        orderInfo.setPayType(OrderInfo.PAY_TYPE_ONLINE);
        orderInfo.setProductCount(1);
        orderInfo.setProductId(vo.getProductId());
        orderInfo.setProductImg(vo.getProductImg());
        orderInfo.setProductName(vo.getProductName());
        orderInfo.setProductPrice(vo.getProductPrice());
        orderInfo.setSeckillDate(now);
        orderInfo.setSeckillId(vo.getId());
        orderInfo.setSeckillPrice(vo.getSeckillPrice());
        orderInfo.setSeckillTime(vo.getTime());
        orderInfo.setStatus(OrderInfo.STATUS_ARREARAGE);
        orderInfo.setUserId(userId);
        return orderInfo;
    }
}
