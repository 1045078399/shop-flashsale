package cn.wolfcode.mq;


public class MQConstant {
    // 秒杀订单消费者组
    public static final String SECKILL_ORDER_CONSUMER_GROUP = "SECKILL_ORDER_CONSUMER_GROUP";
    // 订单结果消费者组
    public static final String SECKILL_ORDER_RESULT_CONSUMER_GROUP = "SECKILL_ORDER_RESULT_CONSUMER_GROUP";
    // 取消本地标识消费者组
    public static final String CANCEL_SECKILL_OVER_SIGN_CONSUMER_GROUP = "CANCEL_SECKILL_OVER_SIGN_CONSUMER_GROUP";
    // 订单支付超时消费者组
    public static final String ORDER_PAY_TIMEOUT_CONSUMER_GROUP = "ORDER_PAY_TIMEOUT_CONSUMER_GROUP";
    // 积分退款事务分组
    public static final String INTEGRAL_REFUND_TX_GROUP = "INTEGRAL_REFUND_TX_GROUP";

    //积分退款主题
    public static final String INTEGRAL_REFUND_TOPIC = "INTEGRAL_REFUND_TOPIC";
    //订单队列
    public static final String ORDER_PENDING_TOPIC = "ORDER_PENDING_TOPIC";
    //订单结果
    public static final String ORDER_RESULT_TOPIC = "ORDER_RESULT_TOPIC";
    //订单超时取消
    public static final String ORDER_PAY_TIMEOUT_TOPIC = "ORDER_PAY_TIMEOUT_TOPIC";
    //取消本地标识
    public static final String CANCEL_SECKILL_OVER_SIGN_TOPIC = "CANCEL_SECKILL_OVER_SIGN_TOPIC";
    //订单创建成功Tag
    public static final String ORDER_RESULT_SUCCESS_TAG = "SUCCESS";
    //订单创建成功目的地
    public static final String ORDER_RESULT_SUCCESS_DEST = ORDER_RESULT_TOPIC + ":" + ORDER_RESULT_SUCCESS_TAG;
    //订单创建成失败Tag
    public static final String ORDER_RESULT_FAIL_TAG = "FAIL";
    //订单创建失败目的地
    public static final String ORDER_RESULT_FAIL_DEST = ORDER_RESULT_TOPIC + ":" + ORDER_RESULT_FAIL_TAG;
    //延迟消息等级
    public static final int ORDER_PAY_TIMEOUT_DELAY_LEVEL = 13;
}
