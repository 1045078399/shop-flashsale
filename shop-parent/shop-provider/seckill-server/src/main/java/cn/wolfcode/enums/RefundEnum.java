package cn.wolfcode.enums;

import lombok.Getter;

@Getter
public enum RefundEnum {
    ALI_PAY(0, "alipayRefund"), INTEGRAL(1, "integralRefund");

    private Integer type;
    private String name;

    RefundEnum(Integer type, String name) {
        this.type = type;
        this.name = name;
    }

    public static RefundEnum getByType(Integer type) {
        for (RefundEnum instance : values()) {
            if (instance.getType().equals(type)) {
                return instance;
            }
        }

        return null;
    }
}
