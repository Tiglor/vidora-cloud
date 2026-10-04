package org.tiglor.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视频标签，一行 = 一个全站唯一的标签名。
 * <p>
 * {@code uk_name} 建在 {@code utf8mb4_unicode_ci} 列上，所以唯一性是**大小写不敏感**的：
 * "iOS" 和 "ios" 在数据库看来是同一个标签。服务层查重时必须按同样的规则比，
 * 否则前置检查放过、真正插入时撞唯一键，用户拿到的就是一个 500。
 * </p>
 * <p>
 * {@code useCount} 的写入方在 video-service（视频打标签时 +1、删标签时 -1），
 * 本模块只读它做热门排序，不提供任意加减的接口——那等于把计数开放给任何能调到这里的人。
 * </p>
 */
@Data
@TableName("content_tag")
public class Tag implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long useCount;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
