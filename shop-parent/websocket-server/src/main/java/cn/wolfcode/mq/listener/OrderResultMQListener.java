package cn.wolfcode.mq.listener;

import cn.wolfcode.core.SeckillWSEndPointer;
import cn.wolfcode.mq.OrderMQResult;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.websocket.Session;

import java.io.IOException;

import static cn.wolfcode.mq.MQConstant.*;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = SECKILL_ORDER_RESULT_CONSUMER_GROUP,
        topic = ORDER_RESULT_TOPIC,
        selectorExpression = ORDER_RESULT_SUCCESS_TAG + " || " + ORDER_RESULT_FAIL_TAG
)
public class OrderResultMQListener implements RocketMQListener<OrderMQResult> {

    @Override
    public void onMessage(OrderMQResult result) {
        String json = JSON.toJSONString(result);
        log.info("[订单结果消息] 收到订单创建结果消息, 消息内容={}", json);
        int count = 0;
        // 基于 token 得到用户的 session
        Session session = null;
        do {
            session = SeckillWSEndPointer.SESSION_MAP.get(result.getToken());
            if (session == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (session == null && count++ < 3);
        // 将 result 对象发送给前端
        if (session == null) {
            log.warn("[订单结果消息] 收到订单创建结果消息, 通知客户端失败, 因为无法获取[{}]客户端的 session", result.getToken());
            return;
        }

        try {
            session.getBasicRemote().sendText(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
