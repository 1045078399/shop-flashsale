package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntegralVo;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * 分支事务:
 * 1. 接口上贴 LocalTCC 注解
 * 2. 对需要进行事务操作的方法, 将其作为 try 阶段的方法, 并且增加 commit 和 cancel 接口
 */
@LocalTCC
public interface IUsableIntegralService {

    @TwoPhaseBusinessAction(
            name = "decrIntegral", // try 方法
            commitMethod = "decrIntegralCommit", // commit 方法
            rollbackMethod = "decrIntegralCancel" // cancel 方法
    )
    boolean decrIntegral(
            /* 这个注解会将当前标注的参数对象, 在第二个阶段传递给 commit 或 cancel 方法 */
            @BusinessActionContextParameter(paramName = "vo") OperateIntegralVo vo,
            BusinessActionContext ctx);

    void decrIntegralCommit(BusinessActionContext ctx);

    void decrIntegralCancel(BusinessActionContext ctx);

    boolean incrIntegral(OperateIntegralVo vo);
}
