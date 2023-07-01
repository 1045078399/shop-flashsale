package cn.wolfcode.web.controller;


import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.enums.RefundEnum;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Consumer;


@RestController
@RequestMapping("/orderPay")
public class OrderPayController {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private ApplicationContext ctx;

    @RequireLogin
    @RequestMapping("/pay")
    public Result<String> pay(String orderNo, Integer type, @RequestHeader("token") String token) {
        UserInfo user = this.getUserByToken(token);
        String result = "";
        if (OrderInfo.PAY_TYPE_ONLINE.equals(type)) {
            // 支付宝支付
            result = orderInfoService.alipay(orderNo, user.getPhone());
        } else {
            // 积分支付
            orderInfoService.integralPay(orderNo, user.getPhone());
        }
        return Result.success(result);
    }

    @RequestMapping("/success")
    public Result<Boolean> paySuccess(String orderNo, Integer type) {
        try {
            orderInfoService.paySuccess(orderNo, type);
        } catch (Exception e) {
            return Result.success(false);
        }

        return Result.success(true);
    }

    @RequestMapping("/refund")
    public Result<?> refund(String orderNo) {
        OrderInfo orderInfo = orderInfoService.findById(orderNo);
        int payType = orderInfo.getPayType();
        String reason = orderInfo.getProductName() + " 不想要了...";
        RefundEnum refundEnum = RefundEnum.getByType(payType);

        if (refundEnum == null) {
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }

        // 退款处理器
        Consumer<RefundVo> handler = ctx.getBean(refundEnum.getName(), Consumer.class);
        handler.accept(new RefundVo(orderNo,
                orderInfo.getSeckillPrice().toString(),
                orderInfo.getIntergral(),
                orderInfo.getUserId(),
                payType,
                reason));

        return Result.success();
    }

    private UserInfo getUserByToken(String token) {
        return JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)), UserInfo.class);
    }
}
