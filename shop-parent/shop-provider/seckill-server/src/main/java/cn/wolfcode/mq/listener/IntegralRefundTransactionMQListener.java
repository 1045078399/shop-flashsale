package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.OperateIntegralVo;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.service.IOrderInfoService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;

@Slf4j
@RocketMQTransactionListener(txProducerGroup = MQConstant.INTEGRAL_REFUND_TX_GROUP)
public class IntegralRefundTransactionMQListener implements RocketMQLocalTransactionListener {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(final Message msg, final Object arg) {
        log.info("[订单退款] 退款事务消息监听器, 执行本地事务 {}", arg);
        RocketMQLocalTransactionState result = RocketMQLocalTransactionState.COMMIT;
        try {
            String orderNo = (String) arg;
            orderInfoService.integralRefund(orderNo);
            log.info("[订单退款] 本地事务执行成功, 提交事务, 积分服务进行退款...");
        } catch (Exception e) {
            result = RocketMQLocalTransactionState.UNKNOWN;
            log.info("[订单退款] 本地事务执行失败, 返回中间状态, 等待 MQ 检查本地事务是否成功", e);
        }
        return result;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(final Message msg) {
        log.info("[订单退款] 收到 MQ 调用本地事务检查方法, 检查本地事务是否执行成功: {}", JSON.toJSONString(msg.getPayload()));
        OperateIntegralVo vo = (OperateIntegralVo) msg.getPayload();
        RocketMQLocalTransactionState result = RocketMQLocalTransactionState.COMMIT;
        try {
            // RocketMQ 对 payload(OperateIntegralVo 对象) 进行加密
            // 基于消息查询本地订单, 是否已经退款
            OrderInfo orderInfo = orderInfoService.findById(vo.getPk());
            // 判断如果订单状态不是已退款状态, 就返回回滚操作, 积分服务就不会收到退款消息
            if (!OrderInfo.STATUS_REFUND.equals(orderInfo.getStatus())) {
                log.info("[订单退款] 本地事务检查为回滚状态, 删除之前发送的消息, 不进行退款操作...");
                result = RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = RocketMQLocalTransactionState.ROLLBACK;
        }
        return result;
    }
}