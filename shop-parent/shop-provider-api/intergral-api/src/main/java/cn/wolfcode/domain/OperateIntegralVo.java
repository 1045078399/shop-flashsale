package cn.wolfcode.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 用户调用积分接口需要传输的数据对象
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OperateIntegralVo implements Serializable {
    private String pk;//业务主键
    private Long value;//此次积分变动数值
    private String info;//备注
    private Long userId;//用户ID
}
