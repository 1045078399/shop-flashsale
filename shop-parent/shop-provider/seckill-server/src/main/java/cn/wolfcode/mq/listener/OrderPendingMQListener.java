package cn.wolfcode.mq.listener;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMQResult;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.mq.OrderTimeoutMessage;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@RocketMQMessageListener(
        consumerGroup = MQConstant.SECKILL_ORDER_CONSUMER_GROUP,
        topic = MQConstant.ORDER_PENDING_TOPIC
)
@Component
public class OrderPendingMQListener implements RocketMQListener<OrderMessage> {

    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(OrderMessage message) {
        log.info("[订单消息] 收到订单消息, 准备开始创建订单, 消息内容={}", JSON.toJSONString(message));
        OrderMQResult result = new OrderMQResult();
        BeanUtils.copyProperties(message, result);
        String dest = MQConstant.ORDER_RESULT_SUCCESS_DEST;

        try {
            SeckillProductVo vo = seckillProductService.selectByIdAndTime(message.getSeckillId(), message.getTime());
            // 调用创建秒杀订单接口
            String orderNo = orderInfoService.doSeckill(message.getUserPhone(), vo);
            log.info("[订单消息] 订单创建完成, 订单编号={}", orderNo);
            // 订单创建成功
            result.setOrderNo(orderNo);
            result.setMsg(Result.SUCCESS_MESSAGE);
            result.setCode(Result.SUCCESS_CODE);

            // 订单创建成功后, 发送延迟消息, 10 分钟后检查订单是否已支付, 如果未支付, 需要回滚订单
            // delayLevel = 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
            OrderTimeoutMessage timeoutMessage = new OrderTimeoutMessage();
            BeanUtils.copyProperties(message, timeoutMessage);
            timeoutMessage.setOrderNo(orderNo);
            rocketMQTemplate.syncSend(MQConstant.ORDER_PAY_TIMEOUT_TOPIC,
                    MessageBuilder.withPayload(timeoutMessage).build(),
                    1000, MQConstant.ORDER_PAY_TIMEOUT_DELAY_LEVEL);
        } catch (BusinessException be) {
            log.error("[订单消息] 创建订单失败, 出现业务异常.", be);
            dest = MQConstant.ORDER_RESULT_FAIL_DEST;
            result.setMsg(be.getCodeMsg().getMsg());
            result.setCode(be.getCodeMsg().getCode());

            // 回滚 redis 的操作
            orderInfoService.rollbackRedis(message.getUserPhone(), message.getSeckillId(), message.getTime());
        } catch (Exception e) {
            log.error("[订单消息] 订单创建失败, 出现系统异常.", e);
            // TODO 订单创建失败
            dest = MQConstant.ORDER_RESULT_FAIL_DEST;
            result.setMsg(Result.ERROR_MESSAGE);
            result.setCode(Result.ERROR_CODE);

            // 回滚 redis 的操作
            orderInfoService.rollbackRedis(message.getUserPhone(), message.getSeckillId(), message.getTime());
        }

        // 发送失败消息
        rocketMQTemplate.syncSend(dest, result);
    }
}
