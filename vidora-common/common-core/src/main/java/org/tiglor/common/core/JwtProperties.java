package org.tiglor.common.core;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项。密钥来自配置文件 / Nacos / 环境变量，不在代码中硬编码。
 * <p>仅当 {@code jwt.secret} 存在时才装配，未使用 JWT 的服务无需配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
@ConditionalOnProperty(prefix = "jwt", name = "secret")
public class JwtProperties {

    /** HMAC-SHA256 签名密钥，长度必须 >= 32 字节 */
    private String secret;

    /** Token 有效期（秒），默认 7 天 */
    private long expireSeconds = 604800L;
}
