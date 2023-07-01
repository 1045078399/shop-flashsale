package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(name = "pay-service")
public interface AlipayFeignApi {

    @RequestMapping("/alipay/pay")
    Result<String> doPay(@RequestBody PayVo pay);

    @RequestMapping("/alipay/refund")
    Result<Boolean> refund(@RequestBody RefundVo refundVo);
}
