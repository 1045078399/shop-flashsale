package cn.wolfcode.handler;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.feign.AlipayFeignApi;
import cn.wolfcode.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component("alipayRefund")
public class AlipayRefundHandler extends AbstractRefundHandler implements Consumer<RefundVo> {

    @Autowired
    private AlipayFeignApi alipayFeignApi;
    @Autowired
    private IOrderInfoService orderInfoService;

    @Override
    public void accept(RefundVo vo) {
        log.info("[订单退款] 开始对 {}, 退款类型={}, 订单进行退款...", vo.getOutTradeNo(), vo.getType());
        // 构建退款流水
        super.insertRefundLog(vo.getOutTradeNo(), vo.getRefundAmount(), vo.getType(), vo.getRefundReason());

        // 调用积分服务进行退款
        Result<Boolean> result = alipayFeignApi.refund(vo);

        // 判断支付宝退款结果
        if (result.data()) {
            orderInfoService.refundStockRollback(vo.getOutTradeNo());
        }
    }

}
