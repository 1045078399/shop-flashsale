package cn.wolfcode.mq.listener;

import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.web.controller.OrderInfoController;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = MQConstant.CANCEL_SECKILL_OVER_SIGN_CONSUMER_GROUP,
        topic = MQConstant.CANCEL_SECKILL_OVER_SIGN_TOPIC,
        /* 将消息模式修改为广播模式 */
        messageModel = MessageModel.BROADCASTING
)
@Component
@Slf4j
public class CancelLocalStockSignMQListener implements RocketMQListener<Long> {

    @Override
    public void onMessage(Long seckillId) {
        log.info("[取消本地标识监听器] 收到取消本地标识消息: {}", seckillId);
        OrderInfoController.STOCK_COUNT_FLAG.put(seckillId, false);
    }
}
