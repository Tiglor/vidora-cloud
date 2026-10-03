package org.tiglor.common.core;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：Token 签发与解析
 * <p>
 * Token 中携带：
 * - userId：用户ID
 * - roles：角色编码，逗号分隔（如 ROLE_ADMIN,ROLE_USER）
 * - perms：权限标识，逗号分隔（如 video:upload,comment:list）——用于接口级鉴权
 * </p>
 */
public class JwtUtil {

    /** 默认密钥（生产环境应从配置中心获取，长度需 >= 32 字节） */
    private static final String SECRET = "vidora-cloud-secret-key-2026-very-long-string-for-hs256";
    private static final long EXPIRE_MILLIS = 7 * 24 * 60 * 60 * 1000L;

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMS = "perms";

    /** 签发 Token（带角色与权限） */
    public static String generateToken(Long userId, String roles, String perms) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ROLES, roles == null ? "" : roles);
        claims.put(CLAIM_PERMS, perms == null ? "" : perms);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE_MILLIS))
                .signWith(KEY)
                .compact();
    }

    /** 兼容旧签名：仅带角色 */
    public static String generateToken(Long userId, String roles) {
        return generateToken(userId, roles, "");
    }

    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Long getUserId(String token) {
        return parseToken(token).get(CLAIM_USER_ID, Long.class);
    }

    /** 取角色串（逗号分隔） */
    public static String getRoles(String token) {
        Object v = parseToken(token).get(CLAIM_ROLES);
        return v == null ? "" : String.valueOf(v);
    }

    /** 取权限串（逗号分隔） */
    public static String getPerms(String token) {
        Object v = parseToken(token).get(CLAIM_PERMS);
        return v == null ? "" : String.valueOf(v);
    }

    public static boolean isExpired(String token) {
        try {
            Date exp = parseToken(token).getExpiration();
            return exp.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
