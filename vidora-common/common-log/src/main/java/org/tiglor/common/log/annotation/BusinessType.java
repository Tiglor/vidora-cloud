package org.tiglor.common.log.annotation;

/**
 * 操作类型。落库存 int，管理端按值翻译文案。
 * <p>
 * 只列这个项目里真实存在的动作，不照抄 RuoYi 的全量枚举 ——
 * 多出来的值没人用，只会变成查询条件里的死选项。
 */
public enum BusinessType {

    /** 其它：不好归类的写操作，例如刷新缓存 */
    OTHER(0),
    INSERT(1),
    UPDATE(2),
    DELETE(3),
    /** 授权类：改角色菜单、改用户角色 */
    GRANT(4),
    EXPORT(5),
    IMPORT(6),
    /** 清空：批量删掉一整类数据，风险高于普通 DELETE */
    CLEAN(7),
    /** 内容审核：过审 / 驳回 */
    AUDIT(8),
    /** 上下架、状态流转 */
    CHANGE_STATUS(9);

    private final int code;

    BusinessType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
