package org.tiglor.message.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * 消息类型，对应 {@code message_record.msg_type}。
 * <p>
 * 三类消息共用一张表和一套收件箱查询，区别只在「谁有资格发」：
 * {@link #PRIVATE} 任何登录用户都能发，{@link #SYSTEM} / {@link #INTERACT} 只允许内部调用方发，
 * 否则随便谁都能给别人伪造一条「你的视频被举报了」。
 * </p>
 */
@Getter
public enum MsgType {

    /** 系统通知：站内公告、审核结果、账号变更 */
    SYSTEM(1),
    /** 互动消息：被点赞、被评论、被关注 */
    INTERACT(2),
    /** 私信：用户之间的一对一会话 */
    PRIVATE(3);

    private final int code;

    MsgType(int code) {
        this.code = code;
    }

    public static MsgType of(Integer code) {
        if (code != null) {
            for (MsgType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的消息类型：" + code + "，可选 1-系统 2-互动 3-私信");
    }

    /** 只有私信需要会话、需要区分收发双方，另两类是单向投递 */
    public boolean isPrivate() {
        return this == PRIVATE;
    }
}
