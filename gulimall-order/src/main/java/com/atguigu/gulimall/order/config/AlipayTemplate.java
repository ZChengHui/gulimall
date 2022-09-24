package com.atguigu.gulimall.order.config;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gulimall.order.vo.PayVo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "alipay")
@Component
@Data
public class AlipayTemplate {

    //内网穿透会刷新映射地址，及时修改nginx的conf文件
    //ymwfvt3851@sandbox.com
    //在支付宝创建的应用的id
    private   String app_id = "2021000121670769";

    // 商户私钥，您的PKCS8格式RSA2私钥
    private  String merchant_private_key = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCm5yCX2pU/pP04eUPcIoiqnQVkn/3pW6ka3AsjeymWJTA1K66+sa2KEPO1g2EqxnpbC7ZI+e7DCjVgCC/CwVSVA6oCZFMpda2L65rSDCkQ+Mbf2zYDUSSGRb/S/2LeR03Mg/SzwqRdC7fCw7YdfspabrLl3/vifNCOKeacUx8u505Ykq36Y9uwspqSPmjHGc0GKTSJBq7MoZ/Juu6hbFW6P2jEDCrjTOk2aLL3VQxhRcpT9RTlX6yrSD6E8Iuy1uiI5tkcWgM5TPAZ9LJ6uvkb44snuSHelwPoO9MJVsOfSq8Kyossa4LqnLCSp7p/qGuiGU07p+nP63P7C//gFieLAgMBAAECggEAfvpwrZc63UTk8cYwfpY3a7YymC+wuY1mxmyKfOAC75wzZQBq3eHHqbbj80CU+DowdHwgnrynjfOCUCFqVL1JYGV9PnydWHmTI1SIYS2nQKZEh6L7IOLm6tC99AUgbwPIiMQLs4duDOwRTjgfoakIwmxd5s3VkKZs9Puj22LR4pgVJRGkWDoq6BhIVQEBFochrapl9usFIWd3MVBl0qoNSi/kiDD6hwUZqNVXSOblzvN5Nh5V2sYJpJ+Ve5uEHDO+uJ0kbrv+R2y+pZNqv6DZuFE26nso3fQ/RFHESIU8odzuNI589o5BQGxcza54fXSsq/3xeqNsjw2gdTilSk9JYQKBgQDWP9lSwIDBrwpdqoiEOfhx07e7P0RoYUZzZsaEKy9AF0tkHUZkG3V4wWK/M/xh6QbmC1SVE4VIulMgJwykJuTKpciBftFfnRV6t1NB6Bj/YA7A2vK2nbJccrGqpPzNwSCqc11g+h+pWMlrPpm+RAShNz5dyuzxuNCxf8IRL6Y/1QKBgQDHbVMCva8VpBJqJIPkzNYllw5clyjhVOKS96X80SNkcA4gvw+IgsvfiNjZ6lX7a0epEN61ISDfezBrjQTJVmJ7o5jCzR3xCiNsgEv2AoXqFAESsjg9vJW45H+ec0t/AFnetnR9KaIrhWRM5Ww9xL4Bk+VxZhEWRIWzdD0f5wXZ3wKBgQCH9cq+5eSPhLoKSK8LYhJUgauFyxdpOdnQX/c4ZoM8o8u3A5Do9Dfu4qH8fkv6AjvbfnM1i0a4qW0Y6U7vWLM2GlCMROMvTusTjgTqvRQn81hJcGg4FQjb+/jdNogrNhS5ti4a3X98Ixjm0roT8OwTNpCASsdkegkNquqHp3pT5QKBgGgiKN9chskJFGiCSrC1WPjl6KTmdX074teVIwJhflaawHXO7mB809IUVg9r8pvHB/M7EN+Jve2UGNOAHki3p9MhuMm3a3QKtDaaw49g/+YbrffCQeXSzGdwQUdOstRNLaBCq48Tp0+MZrRUc/HJAuI2BO/yuOdkvl/XSXeYHZEHAoGAZSQCOe7XK3IciBx9ECY237ivO6Zl6Jej/05GBKwMOM1/EeTpVwLi6k+3IkEUSqfqd2lKUHH/rlJmbFxVOFq72iH0vcFhLJxQejZsL2g8Y+1gAULt2ZhdWnpNnHJWKPovnreKfe37SXuElN0w/LqwE2TKI70/FfrTKASVHl3dy0k=";
    // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    private  String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgXhVwhrXGUl1BCJLzpQNk7God2WrSm8BBGTN3syod/tDjYHdjc8aLxi1S9Y65tesErfKjxUjR+rGvoYxchgJZtZHrUoG91Wmhgauz5By4SBvtPIvTS6Y0SurSiKXy/4JbdnPxKx7jPKtTD+kWyPTBbEmYd4ekR1q33WrMLsqz364I0Xz3puZRA9GV8DhTcGoqKwDlXg8mBdwmgE7bC/j3pWCmFKmDGbTFdbPmitLcFdECqwk7E9UmuCgA1h7L+RNeoYnrVcPbAdX/UngJI3IYp/H0U+w5RaX8Ts8yYZdaJABaFzurldBho+UrxZl/b//PRbM9ILlJQ3sMqNnFxhJIwIDAQAB";
    // 服务器[异步通知]页面路径  需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    // 支付宝会悄悄的给我们发送一个请求，告诉我们支付成功的信息
    private  String notify_url = "http://nsj7c9.natappfree.cc/payed/notify";

    // 页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    //同步通知，支付成功，一般跳转到成功页
    private  String return_url = "http://member.gulimall.com/memberOrder.html";

    // 签名方式
    private  String sign_type = "RSA2";

    // 字符编码格式
    private  String charset = "utf-8";

    //订单超时时间
    private String timeOut = "30m";

    // 支付宝网关； https://openapi.alipaydev.com/gateway.do
    private  String gatewayUrl = "https://openapi.alipaydev.com/gateway.do";

    public  String pay(PayVo vo) throws AlipayApiException {

        //AlipayClient alipayClient = new DefaultAlipayClient(AlipayTemplate.gatewayUrl, AlipayTemplate.app_id, AlipayTemplate.merchant_private_key, "json", AlipayTemplate.charset, AlipayTemplate.alipay_public_key, AlipayTemplate.sign_type);
        //1、根据支付宝的配置生成一个支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(gatewayUrl,
                app_id, merchant_private_key, "json",
                charset, alipay_public_key, sign_type);

        //2、创建一个支付请求 //设置请求参数
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(return_url);
        alipayRequest.setNotifyUrl(notify_url);

        //商户订单号，商户网站订单系统中唯一订单号，必填
        String out_trade_no = vo.getOut_trade_no();
        //付款金额，必填
        String total_amount = vo.getTotal_amount();
        //订单名称，必填
        String subject = vo.getSubject();
        //商品描述，可空
        String body = vo.getBody();

        alipayRequest.setBizContent("{\"out_trade_no\":\""+ out_trade_no +"\","
                + "\"total_amount\":\""+ total_amount +"\","
                + "\"subject\":\""+ subject +"\","
                + "\"body\":\""+ body +"\","
                + "\"timeout_express\":\""+ timeOut +"\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");

        String result = alipayClient.pageExecute(alipayRequest).getBody();

        //会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
        System.out.println("支付宝的响应："+result);

        return result;

    }
}
