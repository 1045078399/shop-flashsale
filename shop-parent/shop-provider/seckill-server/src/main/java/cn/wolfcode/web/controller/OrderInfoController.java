package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    public static final Map<Long, Boolean> STOCK_COUNT_FLAG = new ConcurrentHashMap<>();

    @RequireLogin
    @RequestMapping("/find")
    public Result<OrderInfo> findById(String orderNo, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        UserInfo userInfo = this.getUserByToken(token);
        OrderInfo orderInfo = orderInfoService.findById(orderNo);
        if (!userInfo.getPhone().equals(orderInfo.getUserId())) {
            // 如果查询到的订单中的用户 id != 当前用户, 说明用户在查询一个不属于自己的订单
            throw new BusinessException(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        return Result.success(orderInfo);
    }

    @RequestMapping("/findById")
    public Result<OrderInfo> findByIdForFeignApi(String orderNo) {
        OrderInfo orderInfo = orderInfoService.findById(orderNo);
        return Result.success(orderInfo);
    }

    @RequireLogin
    @RequestMapping("/doSeckill")
    public Result<String> doSeckill(Integer time, Long seckillId, @RequestHeader(CommonConstants.TOKEN_NAME) String token) {
        Boolean flag = STOCK_COUNT_FLAG.get(seckillId); // 本地库存售完标识, JVM 内存
        if (flag != null && flag) {
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }

        // 1. 基于 token 获取到用户信息(必须登录)
        UserInfo userInfo = this.getUserByToken(token); // 获取当前用户信息, 从 Redis 中获取
        // 2. 基于场次+秒杀id获取到秒杀商品对象
        SeckillProductVo vo = seckillProductService.selectByIdAndTime(seckillId, time); // 获取秒杀商品对象, 第一次查数据库, 后面查 Redis
        if (vo == null) {
            throw new BusinessException(SeckillCodeMsg.REMOTE_DATA_ERROR);
        }
        // 3. 判断时间是否大于开始时间 && 小于 开始时间+2小时
        // TODO 测试时关闭校验功能
//        if (!DateUtil.isLegalTime(vo.getStartDate(), time)) { // 内存中判断时间
//            throw new BusinessException(SeckillCodeMsg.OUT_OF_SECKILL_TIME_ERROR);
//        }
        // 4. 判断用户是否重复下单
        // 基于用户 + 秒杀 id + 场次查询订单, 如果存在订单, 说明用户已经下过单
        String key = SeckillRedisKey.SECKILL_ORDER_HASH.join(userInfo.getPhone() + "");
        long ret = redisTemplate.opsForHash().increment(key, seckillId + "", 1); // 用户重复下单标识, 操作 Redis
        if (ret > 1) {
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        }
        // 5. 判断库存是否充足
        ret = redisTemplate.opsForHash().increment(
                SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(time + ""),
                seckillId + "",
                -1); // 库存预减, 操作 Redis
        if (ret < 0) {
            // 当库存不足以后, 直接在本地缓存中, 标识为库存已经不足
            STOCK_COUNT_FLAG.put(seckillId, true);
            // 如果当前已经没有库存, 需要将 redis 重复下单标记还原
            redisTemplate.opsForHash().increment(key, seckillId + "", -1);
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        // 6. 执行下单操作(减少库存, 创建订单)
        // Redis: 发布/订阅
        // 自定义队列
        // String orderNo = orderInfoService.doSeckill(userInfo.getPhone(), vo); // 下单和减库存, 操作 MySQL
        // 异步下单: 发送 MQ 消息
        // topic: ORDER_PENDING_TOPIC
        // message: {userId, time, seckillId} => Map => VO
        OrderMessage message = new OrderMessage(time, seckillId, token, userInfo.getPhone());
        rocketMQTemplate.syncSend(MQConstant.ORDER_PENDING_TOPIC, message);
        return Result.success("正在排队中...");
    }

    private UserInfo getUserByToken(String token) {
        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
    }
}
