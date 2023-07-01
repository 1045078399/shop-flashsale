package cn.wolfcode.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by wolfcode
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {
    //商品应用ID
    private String appId;
    // 商户私钥，您的PKCS8格式RSA2私钥
    private String merchantPrivateKey;
    // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    private String alipayPublicKey;
    // 签名方式
    private String signType;
    // 字符编码格式
    private String charset;
    // 支付宝网关
    private String gatewayUrl;
    private String returnUrl; // 同步回调地址
    private String notifyUrl; // 异步回调地址
    private String frontendPayUrl; // 同步回调成功重定向地址
}
