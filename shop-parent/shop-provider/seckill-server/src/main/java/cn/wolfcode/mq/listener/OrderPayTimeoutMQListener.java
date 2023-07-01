package cn.wolfcode.mq.listener;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderTimeoutMessage;
import cn.wolfcode.service.IOrderInfoService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = MQConstant.ORDER_PAY_TIMEOUT_CONSUMER_GROUP,
        topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC
)
public class OrderPayTimeoutMQListener implements RocketMQListener<OrderTimeoutMessage> {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void onMessage(OrderTimeoutMessage message) {
        log.info("[订单超时检查] 收到订单超时检查消息, 准备检查订单是否已超时:{}", JSON.toJSONString(message));
        orderInfoService.checkOrderPayTimeout(message);
    }
}
