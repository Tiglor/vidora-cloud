package org.tiglor.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 修改分类。
 * <p>
 * {@code id} 不在这里：新增时由数据库分配，修改时取路径上的 {@code {id}}。
 * 放进请求体的话调用方就能通过改 id 把一次「修改」变成对另一行的覆写。
 * </p>
 */
@Data
public class CategoryRequest {

    /** 父分类 id，一级分类传 null 或 0 */
    @PositiveOrZero(message = "parentId 不能为负")
    private Long parentId;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名不能超过 50 字")
    private String name;

    @Size(max = 500, message = "iconUrl 不能超过 500 字")
    private String iconUrl;

    @PositiveOrZero(message = "sortOrder 不能为负")
    private Integer sortOrder;

    /** 状态：0-禁用 1-启用，不传按启用处理 */
    @Min(value = 0, message = "status 只能是 0 或 1")
    @Max(value = 1, message = "status 只能是 0 或 1")
    private Integer status;
}
