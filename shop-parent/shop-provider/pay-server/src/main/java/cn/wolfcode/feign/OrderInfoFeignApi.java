package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "seckill-service")
public interface OrderInfoFeignApi {

    @RequestMapping("/order/findById")
    Result<OrderInfo> findById(@RequestParam("orderNo") String orderNo);

    @RequestMapping("/orderPay/success")
    Result<Boolean> paySuccess(@RequestParam("orderNo") String orderNo, @RequestParam("type") Integer type);
}
