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
 * 分类被 {@code video_info.category_id} 引用，而同库不等于可以直连：那张表归 video-service 写，
 * 跨服务外键会把服务边界变成部署耦合，所以删除只能拦住「还有子分类」这一种情况，
 * 挡不住视频侧的悬空引用（见 ARCHITECTURE 的内容服务一节）。
 * </p>
 */
@Data
@TableName("content_category")
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 一级分类的 parent_id 取值；用 0 而不是 NULL，这样 {@code idx_parent_id} 上不会有 NULL 参与等值比较 */
    public static final long ROOT_PARENT_ID = 0L;

    /** 主键，数据库自增；新建时不接受调用方指定 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类 id，同一父级下分类名不能重复（{@code uk_parent_name}） */
    private Long parentId;

    /** 分类名，只在同一父级下唯一；列是 {@code utf8mb4_unicode_ci}，所以「动画」和大小写变体算同一个名字 */
    private String name;

    /** 图标地址，可为空；服务层会把纯空白收敛成 null 再落库 */
    private String iconUrl;

    /** 展示顺序，越小越靠前；同值时按 id 升序兜底，否则分页翻页时顺序会抖 */
    private Integer sortOrder;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后更新时间，插入与更新时都自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
