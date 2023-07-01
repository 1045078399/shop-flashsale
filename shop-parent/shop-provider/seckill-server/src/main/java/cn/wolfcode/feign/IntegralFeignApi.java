package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntegralVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(name = "integral-service")
public interface IntegralFeignApi {

    @RequestMapping("/integral/decr")
    Result<Boolean> decrIntegral(@RequestBody OperateIntegralVo vo);

    @RequestMapping("/integral/refund")
    Result<Boolean> refund(@RequestBody OperateIntegralVo vo);
}
