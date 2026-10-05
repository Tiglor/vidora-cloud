package org.tiglor.common.log.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求里取「真实来源」。
 * <p>
 * 本项目的服务永远在网关后面，{@code request.getRemoteAddr()} 拿到的只会是网关的内网地址；
 * 审计表和登录日志记 IP 是为了定位「这个动作从哪来」，全记成同一个内网 IP 等于没记。
 * <p>
 * 操作日志切面和 auth 的登录审计都用它，避免两份实现解析顺序不一致。
 */
public final class ClientAddress {

    private static final String UNKNOWN = "unknown";

    /** oper_ip / ip 列宽都是 VARCHAR(255)，超长的值会让整条审计插入失败，反而把记录丢了 */
    private static final int MAX_IP = 255;

    private ClientAddress() {
    }

    /**
     * 客户端 IP：X-Forwarded-For 的第一跳 → X-Real-IP → remoteAddr。
     * <p>
     * 只信第一跳：后面几跳是代理链自己追加的，攻击者能在第一跳里塞任意字符串，
     * 但至少它和网关看到的连接是同一份数据，不会被伪造出一串假 IP 来污染排查。
     * <p>
     * 末尾按列宽截断：请求头是外部输入，伪造一串 5KB 的 X-Forwarded-For 就能让
     * 这条审计插不进去（Data too long），审计日志被输入打断是不可接受的。
     */
    public static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (isPresent(forwarded)) {
            int comma = forwarded.indexOf(',');
            return cap(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim());
        }
        String real = request.getHeader("X-Real-IP");
        if (isPresent(real)) {
            return cap(real.trim());
        }
        return cap(request.getRemoteAddr());
    }

    /** 原样记录 User-Agent，不做解析：登录日志里要的是「哪台设备」的原始证据 */
    public static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private static String cap(String value) {
        if (value == null || value.length() <= MAX_IP) {
            return value;
        }
        return value.substring(0, MAX_IP);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value);
    }
}
