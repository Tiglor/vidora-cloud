package org.tiglor.system.vo;

import lombok.Getter;
import lombok.Setter;
import org.tiglor.common.log.entity.OperLogEntity;

/**
 * 操作日志的展示对象：审计表只存 oper_user_id，管理端要看到「谁干的」。
 * <p>
 * 昵称在查询时现补，而不是写入时冗余一份 —— sys_oper_log 与 sys_user 在同一个库里，
 * 一次批量查就拿到最新昵称，改备注后历史列表也跟着对得上；
 * 写入时冗余的话，用户改名后审计表里就永远是他当年的旧名字。
 * <p>
 * 继承实体而不是重抄一遍字段：这两份必须逐字段对齐，抄一份就是留一处漂移。
 */
@Getter
@Setter
public class OperLogVO extends OperLogEntity {

    private String operUserName;
}
