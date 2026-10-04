package org.tiglor.recommend.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.util.Locale;

/**
 * 推荐场景，对应 {@code recommend_result.scene} / {@code recommend_algo_config.scene}。
 * <p>
 * 场景是唯一键的一段（{@code uk_user_video_scene}），所以取值必须收敛成枚举：
 * 自由字符串会让 "home" / "Home" / "首页" 各占一行，同一个视频在同一屏里被推荐三次。
 * </p>
 */
@Getter
public enum RecommendScene {

    /** 首页信息流 */
    HOME("home"),
    /** 关注页：只看已关注作者的新视频 */
    FOLLOW("follow"),
    /** 话题/专题页 */
    TOPIC("topic");

    private final String code;

    RecommendScene(String code) {
        this.code = code;
    }

    public static RecommendScene of(String code) {
        if (code != null) {
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            for (RecommendScene scene : values()) {
                if (scene.code.equals(normalized)) {
                    return scene;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的推荐场景：" + code + "，可选 home / follow / topic");
    }
}
