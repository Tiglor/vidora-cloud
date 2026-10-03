package com.video.platform.interactservice.entity;

import com.video.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interact_comment")
public class Comment extends BaseEntity {

    private Long videoId;
    private Long userId;
    private Long parentId;
    private Long rootId;
    private String content;
    private Long likeCount;
    private Integer replyCount;
    private Integer status;
}
