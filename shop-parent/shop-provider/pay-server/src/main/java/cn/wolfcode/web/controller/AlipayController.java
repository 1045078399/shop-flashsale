package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.config.AlipayProperties;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.PayLog;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.feign.OrderInfoFeignApi;
import cn.wolfcode.mapper.PayLogMapper;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import static cn.wolfcode.domain.PayLog.PAY_TYPE_ONLINE;

@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController {

    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties properties;
    @Autowired
    private OrderInfoFeignApi orderInfoFeignApi;
    @Autowired
    private PayLogMapper payLogMapper;

    @RequestMapping("/refund")
    public Result<Boolean> refund(@RequestBody RefundVo refund) throws AlipayApiException {
        // 创建退款请求对象
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        // 封装退款请求参数
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", refund.getOutTradeNo());
        bizContent.put("refund_amount", refund.getRefundAmount());
        bizContent.put("refund_reason", refund.getRefundReason());
        bizContent.put("out_request_no", null);

        // 将退款参数设置到请求对象中
        request.setBizContent(bizContent.toString());

        // 发起请求到支付宝, 获得退款响应结果
        AlipayTradeRefundResponse response = alipayClient.execute(request);
        log.info("[支付宝退款] 收到支付宝退款响应数据: {}", JSON.toJSONString(response));
        return Result.success(response.isSuccess());
    }

    @RequestMapping("/pay")
    public Result<Object> doPay(@RequestBody PayVo pay) throws AlipayApiException {
        // 设置请求参数
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(properties.getReturnUrl());
        alipayRequest.setNotifyUrl(properties.getNotifyUrl());

        //商户订单号，商户网站订单系统中唯一订单号，必填
        String out_trade_no = pay.getOutTradeNo();
        //付款金额，必填
        String total_amount = pay.getTotalAmount();
        //订单名称，必填
        String subject = pay.getSubject();
        //商品描述，可空
        String body = pay.getBody();

        alipayRequest.setBizContent("{\"out_trade_no\":\"" + out_trade_no + "\","
                + "\"total_amount\":\"" + total_amount + "\","
                + "\"subject\":\"" + subject + "\","
                + "\"body\":\"" + body + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");

        //请求
        String result = alipayClient.pageExecute(alipayRequest).getBody();
        return Result.success(result);
    }

    // https://shop.gateway.wolfcode.cn/pay/alipay/return_url?sign=xxx&out_trade_no=xxxx
    @GetMapping("/return_url")
    public void returnUrl(@RequestParam Map<String, String> params, HttpServletResponse resp) throws Exception {
        log.info("[支付宝支付] 收到同步回调通知, 参数内容如下: {}", params);
        // 验证签名
        boolean signVerified = AlipaySignature.rsaCheckV1(params,
                properties.getAlipayPublicKey(), properties.getCharset(), properties.getSignType()); //调用SDK验证签名

        //——请在这里编写您的程序（以下代码仅作参考）——
        if (signVerified) {
            //商户订单号
            String orderNo = params.get("out_trade_no");
            resp.sendRedirect(properties.getFrontendPayUrl() + orderNo);
        } else {
            resp.sendRedirect("https://www.wolfcode.cn");
        }
        //——请在这里编写您的程序（以上代码仅作参考）——
    }

    @PostMapping("/notify_url")
    public String notifyUrl(@RequestParam Map<String, String> params, HttpServletResponse resp) throws Exception {
        log.info("[支付宝支付] 收到异步回调通知, 参数内容如下: {}", params);
        boolean signVerified = AlipaySignature.rsaCheckV1(params,
                properties.getAlipayPublicKey(), properties.getCharset(), properties.getSignType()); //调用SDK验证签名

        //——请在这里编写您的程序（以下代码仅作参考）——
        /* 实际验证过程建议商户务必添加以下校验：
            1、需要验证该通知数据中的 out_trade_no 是否为商户系统中创建的订单号
            2、判断 total_amount 是否确实为该订单的实际金额（即商户订单创建时的金额）
            3、校验通知中的 seller_id（或者 seller_email) 是否为 out_trade_no 这笔单据的对应的操作方（有的时候，一个商户可能有多个 seller_id/seller_email）
            4、验证 app_id 是否为该商户本身。
        */
        if (signVerified) {//验证成功
            // 商户订单号
            String orderNo = params.get("out_trade_no");
            OrderInfo orderInfo = orderInfoFeignApi.findById(orderNo).data();

            if (orderInfo == null) {
                log.warn("[支付宝支付] 异步回调通知, 支付宝通知订单异常, 查询不到订单信息: {}", orderNo);
                // 说明该订单不存在, out_trade_no 不属于我们系统中的订单
                return "error";
            }

            // 支付金额
            String total_amount = params.get("total_amount");
            if (orderInfo.getSeckillPrice().compareTo(new BigDecimal(total_amount)) != 0) {
                log.warn("[支付宝支付] 异步回调通知, 订单金额与支付金额不匹配: orderAmount: {}, payAmount: {}", orderInfo.getSeckillPrice(), total_amount);
                return "error";
            }

            //支付宝交易号
            String trade_no = params.get("trade_no");
            //交易状态
            String trade_status = params.get("trade_status");

            // 保存支付流水信息 => 幂等性保证
            PayLog log = new PayLog(trade_no, orderNo, new Date().getTime() + "", total_amount, PAY_TYPE_ONLINE, trade_status);
            payLogMapper.insert(log);

            if (trade_status.equals("TRADE_SUCCESS")) {
                //判断该笔订单是否在商户网站中已经做过处理
                //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
                //如果有做过处理，不执行商户的业务程序

                /**
                 *  状态变更
                 *  积分增加 && 增加积分操作流水 > 存储订单 id
                 *  创建发货单 && 关联订单编号作为唯一标识
                 */
                orderInfoFeignApi.paySuccess(orderNo, PAY_TYPE_ONLINE);

                //注意：
                //付款完成后，支付宝系统发送该交易状态通知
            }

            return "success";
        }

        return "error";
        //——请在这里编写您的程序（以上代码仅作参考）——
    }
}

