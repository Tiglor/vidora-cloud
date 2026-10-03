package org.tiglor.video.client;

import org.tiglor.common.core.security.SecurityHeaders;
import org.tiglor.common.core.security.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务间调用时透传当前请求的身份上下文。
 * <p>
 * 网关只负责校验一次 JWT，Feign 调用下游服务时继续携带标准身份头，
 * 下游服务即可复用 HeaderAuthenticationFilter 完成接口授权。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
public class FeignSecurityConfiguration {

    @Bean
    public RequestInterceptor securityContextRequestInterceptor() {
        return template -> {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                template.header(SecurityHeaders.USER_ID, userId.toString());
            }
            addHeader(template, SecurityHeaders.ROLES, UserContext.getRoles());
            addHeader(template, SecurityHeaders.PERMISSIONS, UserContext.getPermissions());
        };
    }

    private static void addHeader(feign.RequestTemplate template, String name,
                                  java.util.List<String> values) {
        if (values != null && !values.isEmpty()) {
            template.header(name, String.join(",", values));
        }
    }
}
