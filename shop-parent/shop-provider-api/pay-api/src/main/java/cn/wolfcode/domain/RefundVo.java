package cn.wolfcode.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RefundVo implements Serializable {
    private String outTradeNo;//商户订单号
    private String refundAmount;//退款金额
    private Long refundIntegral;//退款积分
    private Long userId;
    private int type;
    private String refundReason;//退款原因

    public RefundVo(String outTradeNo, String refundAmount, String reason) {
        this.outTradeNo = outTradeNo;
        this.refundAmount = refundAmount;
        this.refundReason = reason;
    }
}
