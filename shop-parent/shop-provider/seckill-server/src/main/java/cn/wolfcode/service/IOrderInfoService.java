package cn.wolfcode.service;


import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.OrderTimeoutMessage;

/**
 * Created by wolfcode
 */
public interface IOrderInfoService {

    OrderInfo selectByUserIdAndSeckillId(Long phone, Long seckillId, Integer time);

    String doSeckill(Long userId, SeckillProductVo vo);

    OrderInfo findById(String orderNo);

    /**
     * 用户下单失败, 回滚 Redis
     *
     * @param userId
     * @param seckillId
     * @param time
     */
    void rollbackRedis(Long userId, Long seckillId, Integer time);

    void checkOrderPayTimeout(OrderTimeoutMessage message);

    String alipay(String orderNo, Long userId);

    /**
     * 支付成功
     *
     * @param orderNo 订单编号
     * @param type    {0=线上支付, 1=积分支付}
     */
    void paySuccess(String orderNo, Integer type);

    /**
     * 支付宝退款
     *
     * @param orderInfo
     */
    void refundByAlipay(OrderInfo orderInfo);


    void integralPay(String orderNo, Long phone);

    void refundByIntegral(OrderInfo orderInfo);

    void refund(String orderNo, String reason);

    void refundStockRollback(String orderNo);

    void integralRefund(String orderNo);
}
