package cn.wolfcode.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;


@Setter
@Getter
public class RefundLog implements Serializable {
    public static final int REFUND_TYPE_ALIPAY = 0;
    public static final int REFUND_TYPE_INTERGRAL = 1;

    public static final int REFUND_STATUS_PENDING = 0;
    public static final int REFUND_STATUS_SUCCESS = 1;
    public static final int REFUND_STATUS_REJECT = 2;

    private String outTradeNo;//商品订单号
    private String refundAmount;//退款金额退款积分
    private String refundReason;//退款原因
    private int refundType;//退款类型 0-在线支付 1-积分支付
    private Date refundTime;//退款时间
    private int status;//退款状态 0=退款中, 1=退款成功, 2=拒绝退款
}
