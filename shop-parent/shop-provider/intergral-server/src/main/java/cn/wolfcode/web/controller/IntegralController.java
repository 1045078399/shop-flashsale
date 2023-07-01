package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntegralVo;
import cn.wolfcode.service.IUsableIntegralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/integral")
public class IntegralController {

    @Autowired
    private IUsableIntegralService usableIntegralService;

    @RequestMapping("/decr")
    public Result<Boolean> decrIntegral(@RequestBody OperateIntegralVo vo) {
        // 扣除积分 && 增加积分操作流水记录
        boolean ret = usableIntegralService.decrIntegral(vo, null);
        return Result.success(ret);
    }

    @RequestMapping("/refund")
    public Result<Boolean> refund(@RequestBody OperateIntegralVo vo) {
        boolean ret = usableIntegralService.incrIntegral(vo);
        return Result.success(ret);
    }
}
