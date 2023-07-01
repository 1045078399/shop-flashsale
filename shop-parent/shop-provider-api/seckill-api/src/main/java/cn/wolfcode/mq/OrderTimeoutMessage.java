package cn.wolfcode.mq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Created by wolfcode
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {
    private Integer time;//秒杀场次
    private Long seckillId;//秒杀商品ID
    private Long userPhone;//用户手机号码
    private String orderNo;//订单编号
}
