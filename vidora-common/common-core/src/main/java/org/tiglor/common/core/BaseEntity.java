package org.tiglor.common.core;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体：所有业务表通用字段
 */
@Getter
@Setter
public class BaseEntity implements Serializable {

    /** 主键，数据库自增。新增时不用传，插入后由 MyBatis-Plus 回写进实体；已落库的行不再改 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间，插入时自动填充，调用方传进来的值不算数 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后更新时间，插入与更新时都自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0-未删除 1-已删除。查询会被自动追加 {@code is_deleted = 0}，
     * 所以查出来的行永远是 0；但它没有 {@code @JsonIgnore}，仍会随实体一起序列化进响应体。
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted = 0;
}
