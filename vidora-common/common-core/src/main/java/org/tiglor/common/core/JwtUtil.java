package org.tiglor.common.core;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与解析。
 * <p>
 * Token 中携带：
 * - userId：用户ID
 * - roles：角色编码，逗号分隔（如 ROLE_ADMIN,ROLE_USER）
 * - perms：权限标识，逗号分隔（如 video:upload,comment:list）——用于接口级鉴权
 * </p>
 * <p>密钥与有效期由 {@link JwtProperties} 注入；仅当 {@code jwt.secret} 存在时才装配。</p>
 */
@Component
@ConditionalOnProperty(prefix = "jwt", name = "secret")
public class JwtUtil {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMS = "perms";

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expireMillis = properties.getExpireSeconds() * 1000L;
    }

    /** 签发 Token（带角色与权限） */
    public String generateToken(Long userId, String roles, String perms) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ROLES, roles == null ? "" : roles);
        claims.put(CLAIM_PERMS, perms == null ? "" : perms);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签 Token。
     *
     * @throws io.jsonwebtoken.JwtException 签名无效、格式错误或已过期
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 读取字符串型 claim，缺失时返回空串 */
    public static String claimString(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? "" : String.valueOf(value);
    }
}
