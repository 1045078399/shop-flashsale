package cn.wolfcode.handler;

import cn.wolfcode.domain.RefundLog;
import cn.wolfcode.mapper.RefundLogMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

abstract public class AbstractRefundHandler {

    @Autowired
    protected RefundLogMapper refundLogMapper;

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

}
