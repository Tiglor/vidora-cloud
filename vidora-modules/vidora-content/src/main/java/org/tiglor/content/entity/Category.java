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
 * 视频分类，{@code parent_id = 0} 表示一级分类。
 * <p>
 * 不继承 {@code BaseEntity}：表上没有 {@code is_deleted}，下架靠 {@code status}。
 * 分类被 {@code video_info.category_id} 引用，而那是另一个库的另一张表，
 * 所以删除只能拦住「还有子分类」这一种情况，挡不住视频侧的悬空引用（见 ARCHITECTURE 的内容服务一节）。
 * </p>
 */
@Data
@TableName("content_category")
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 一级分类的 parent_id 取值；用 0 而不是 NULL，这样 {@code idx_parent_id} 上不会有 NULL 参与等值比较 */
    public static final long ROOT_PARENT_ID = 0L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    private String iconUrl;

    private Integer sortOrder;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
