package org.tiglor.interact.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * 互动动作类型，对应 {@code interact_action.action_type}。
 * <p>
 * 用枚举而不是裸 int：这三个值会同时出现在接口参数、查询条件和计数聚合里，
 * 散落魔法数字的话改一处漏一处。
 * </p>
 */
@Getter
public enum ActionType {

    /** 点赞，可取消 */
    LIKE(1),
    /** 收藏，可取消 */
    FAVORITE(2),
    /** 分享，见 {@code InteractActionService} 里对「分享不可取消」的说明 */
    SHARE(3);

    private final int code;

    ActionType(int code) {
        this.code = code;
    }

    public static ActionType of(Integer code) {
        if (code != null) {
            for (ActionType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的动作类型：" + code);
    }
}
