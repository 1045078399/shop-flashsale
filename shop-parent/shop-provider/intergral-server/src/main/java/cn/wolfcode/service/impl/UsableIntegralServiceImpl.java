package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.domain.AccountLog;
import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.OperateIntegralVo;
import cn.wolfcode.mapper.AccountLogMapper;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import com.alibaba.fastjson.JSONObject;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;


@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {

    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;
    @Autowired
    private AccountLogMapper accountLogMapper;

    @Override
    public boolean decrIntegral(OperateIntegralVo vo, BusinessActionContext ctx) {
        // 解决悬挂问题(二阶段 比 try 先执行)
        // 先往事务表中插入一条记录, 如果能插入成功, 则说明没有悬挂问题, 如果插入失败, 出现悬挂问题, 不能执行 try 的业务
        AccountTransaction transaction = this.buildAccountTransaction(ctx, vo, AccountLog.TYPE_DECR + "", AccountTransaction.STATE_TRY);
        accountTransactionMapper.insert(transaction); // 出现主键重复异常, 不需要处理, 用于避免悬挂问题
        // try 阶段只做资源预留操作, 检查资源是否足够, 如果不够, 直接抛出异常
        // 如果资源足够, 就在冻结金额中加上对应的值
        int ret = usableIntegralMapper.freezeIntegral(vo.getUserId(), vo.getValue());
        if (ret <= 0) {
            throw new BusinessException(new CodeMsg(506000, "用户积分余额不足!"));
        }
        return true;
    }

    private AccountTransaction buildAccountTransaction(BusinessActionContext ctx, OperateIntegralVo vo, String type, int state) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setTxId(ctx.getXid());
        transaction.setActionId(ctx.getBranchId() + "");
        transaction.setAmount(vo.getValue());
        transaction.setGmtCreated(new Date());
        transaction.setState(state);
        transaction.setType(type);
        transaction.setUserId(vo.getUserId());
        return transaction;
    }

    @Override
    public void decrIntegralCommit(BusinessActionContext ctx) {
        // 进行校验操作, 防止异常流程问题
        // 1. 根据事务 id + 分支 id 查询事务对象
        AccountTransaction transaction = accountTransactionMapper.get(ctx.getXid(), ctx.getBranchId() + "");
        // 2. 判断对象是否为空, 如果为空, 证明流程异常, 出现二阶段先于一阶段执行问题, 直接抛出异常
        if (transaction == null) {
            throw new BusinessException(new CodeMsg(506002, "业务流程异常"));
        }
        // 3. 判断状态是否为初始化状态(try), 如果不是
        if (transaction.getState() != AccountTransaction.STATE_TRY) {
            // 4. 判断状态是否为已提交, 如果是已提交, 就直接 return 结束方法(保证幂等), 否则就直接抛出异常
            if (AccountTransaction.STATE_COMMIT == transaction.getState()) {
                // 说明之前已经提交过了
                return;
            }

            throw new BusinessException(new CodeMsg(506002, "业务流程异常, 状态为已回滚"));
        }
        // 5. 如果状态为初始化状态, 就正常执行业务
        // 更新事务状态为已提交
        int ret = accountTransactionMapper.updateAccountTransactionState(
                ctx.getXid(),
                ctx.getBranchId() + "",
                AccountTransaction.STATE_COMMIT,
                AccountTransaction.STATE_TRY
        );
        if (ret > 0) {
            // ----------- BUSINESS BEGIN -----------
            OperateIntegralVo vo = ((JSONObject) ctx.getActionContext().get("vo")).toJavaObject(OperateIntegralVo.class);
            // 真正扣除用户账户余额
            usableIntegralMapper.commitChange(vo.getUserId(), vo.getValue());
            // 并且增加账户操作流水日志
            AccountLog log = new AccountLog(vo.getPk(), AccountLog.TYPE_DECR, vo.getValue(), new Date(), vo.getInfo());
            accountLogMapper.insert(log);
            // -----------  BUSINESS END  -----------
        }
    }

    @Override
    public void decrIntegralCancel(BusinessActionContext ctx) {
        OperateIntegralVo vo = ((JSONObject) ctx.getActionContext().get("vo")).toJavaObject(OperateIntegralVo.class);
        // 1. 查询事务控制记录, 根据是否为空分为两个处理流程
        AccountTransaction transaction = accountTransactionMapper.get(ctx.getXid(), ctx.getBranchId() + "");
        // 2. 如果事务记录为空: 说明需要空回滚, try 未执行
        if (transaction == null) {
            // 2.1 插入一条状态为回滚的事务记录, 判断是否成功, 如果成功, 直接结束流程, 否则抛出异常
            transaction = this.buildAccountTransaction(ctx, vo,
                    AccountLog.TYPE_DECR + "", AccountTransaction.STATE_CANCEL);
            accountTransactionMapper.insert(transaction); // 如果此时抛出异常, 就不做任何操作

            // 如果没有抛出异常, 需要执行幂等, 结束流程, 此时就是空回滚操作
            return;
        }

        // 3. 如果事务记录不为空: 说明 try 已执行, 需要进行状态判断, 保证幂等性
        if (transaction.getState() != AccountTransaction.STATE_TRY) {
            if (transaction.getState() == AccountTransaction.STATE_CANCEL) {
                return; // 说明幂等成功, 之前已经执行过 Cancel 方法
            }

            // 如果状态是 commit, 说明流程异常
            throw new BusinessException(new CodeMsg(506002, "业务流程异常, 状态为已提交"));
        }

        int ret = accountTransactionMapper.updateAccountTransactionState(
                ctx.getXid(),
                ctx.getBranchId() + "",
                AccountTransaction.STATE_CANCEL,
                AccountTransaction.STATE_TRY
        );
        if (ret > 0) {
            // 取消 try 阶段预留的冻结金额即可
            usableIntegralMapper.unFreezeIntegral(vo.getUserId(), vo.getValue());
        }
    }

    private void insertAccountLog(OperateIntegralVo vo, Integer type) {
        AccountLog log = new AccountLog(vo.getPk(), type, vo.getValue(), new Date(), vo.getInfo());
        accountLogMapper.insert(log);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean incrIntegral(OperateIntegralVo vo) {
        // 1. 为用户账户增加积分
        usableIntegralMapper.addIntegral(vo.getUserId(), vo.getValue());
        // 2. 创建流水日志
        this.insertAccountLog(vo, AccountLog.TYPE_INCR);
        // 3. 返回结果
        return true;
    }
}
