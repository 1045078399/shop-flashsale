package cn.wolfcode.handler;

import cn.wolfcode.domain.OperateIntegralVo;
import cn.wolfcode.domain.RefundVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

import static cn.wolfcode.mq.MQConstant.INTEGRAL_REFUND_TOPIC;
import static cn.wolfcode.mq.MQConstant.INTEGRAL_REFUND_TX_GROUP;

@Slf4j
@Component("integralRefund")
public class IntegralRefundHandler extends AbstractRefundHandler implements Consumer<RefundVo> {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void accept(RefundVo vo) {
        log.info("[订单退款] 开始对 {}, 退款类型={}, 订单进行退款...", vo.getOutTradeNo(), vo.getType());
        // 构建退款流水
        // super.insertRefundLog(vo.getOutTradeNo(), vo.getRefundAmount(), vo.getType(), vo.getRefundReason());

        // 调用积分服务进行退款
        OperateIntegralVo operateIntegralVo =
                new OperateIntegralVo(vo.getOutTradeNo(), vo.getRefundIntegral(), vo.getRefundReason(), vo.getUserId());
        // Result<Boolean> result = integralFeignApi.refund(operateIntegralVo);
        // 基于事务消息来实现
        Message<OperateIntegralVo> message = MessageBuilder.withPayload(operateIntegralVo).build();
        // RocketMQ 发送事务消息
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(INTEGRAL_REFUND_TX_GROUP,
                INTEGRAL_REFUND_TOPIC, message, vo.getOutTradeNo());
        log.info("[订单退款] 积分退款 >>>> send status={}, localTransactionState={} <<<<",
                result.getSendStatus().name(), result.getLocalTransactionState().name());

        // 判断支付宝退款结果
        // if (result.data()) {
        //     orderInfoService.refundStockRollback(vo.getOutTradeNo());
        // }
    }
}
