package org.tiglor.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「需要留审计痕迹」的接口。
 * <p>
 * 判定标准不是「是不是写操作」，而是「出了事要不要追责到人」：
 * 删除、授权、审核、改配置这类要标；用户自己发评论、点赞不标 ——
 * 那是业务流水，量级差两个数量级，混进审计表只会把真正要查的淹掉。
 * <p>
 * 由 {@code OperLogAspect} 环绕处理，异步上报到 system-service 落库，
 * 同时写一份 audit.log；上报失败不会影响业务调用本身。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 模块名，管理端按它筛选，如「用户管理」 */
    String title();

    BusinessType type() default BusinessType.OTHER;

    /**
     * 是否记录请求参数。
     * <p>
     * 登录、改密这类接口的入参里躺着明文凭据，必须置 false；
     * 审计表是给人查的，不该变成密码明文仓库。
     */
    boolean saveParam() default true;

    /** 是否记录返回值。默认不开：列表接口的返回值能把审计表撑爆 */
    boolean saveResult() default false;
}
