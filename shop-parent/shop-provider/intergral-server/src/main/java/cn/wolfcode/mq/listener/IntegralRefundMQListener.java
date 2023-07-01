package cn.wolfcode.mq.listener;

import cn.wolfcode.domain.OperateIntegralVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.service.IUsableIntegralService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = MQConstant.INTEGRAL_REFUND_TX_GROUP,
        topic = MQConstant.INTEGRAL_REFUND_TOPIC
)
public class IntegralRefundMQListener implements RocketMQListener<OperateIntegralVo> {

    @Autowired
    private IUsableIntegralService usableIntegralService;

    @Override
    public void onMessage(OperateIntegralVo vo) {
        boolean ret = usableIntegralService.incrIntegral(vo);
        log.info("[订单退款] 执行订单退款逻辑, 退款结果={}, msg={}", ret, JSON.toJSONString(vo));
    }
}
