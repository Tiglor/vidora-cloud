package org.tiglor.search.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搜索建议词，{@code uk_keyword} 唯一。
 * <p>
 * 和 {@code content_tag} 是两回事：标签是视频身上挂的属性，这里的词只是搜索框的联想候选，
 * 可以是一个标签名，也可以是一个专题、一个人名、一句运营想推的话。
 * 两边不做外键也不做同步，运营想让某个标签出现在联想框里就手工加一条（{@code source = 1}），
 * 或者等 {@code SearchSuggestService.mine} 从搜索统计里挖出来（{@code source = 2}）。
 * </p>
 * <p>
 * {@code weight} 只影响排序，不参与任何计算，所以运营可以随手填一个量级
 * （100 / 50 / 10）而不必担心算错。
 * </p>
 * <p>
 * 表上没有 {@code update_time}，也没有 {@code is_deleted}——下架走 {@code status = 0}，
 * 删除是物理删。所以改过一个词的权重之后无法知道是什么时候改的。
 * </p>
 */
@Data
@TableName("search_suggest")
public class SearchSuggest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 启用 */
    public static final int STATUS_ENABLED = 1;

    /** 禁用：不再出现在联想框里，但词还留着，随时能改回来 */
    public static final int STATUS_DISABLED = 0;

    /** 自增主键，管理接口按它定位某一条建议词 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 建议词本身，入库前只去首尾空白。全表唯一（大小写不敏感），
     * 所以新增或改名撞上已有的词会直接报错而不是合并计数。
     */
    private String keyword;

    /**
     * 排序权重，降序使用；运营不填就是 0。
     * <p>自动挖掘进来的词一律是 0，因此能被前缀匹配到，但排在所有人工词之后。</p>
     */
    private Integer weight;

    /** 来源，取值见 {@link org.tiglor.search.enums.SuggestSource} */
    private Integer source;

    /**
     * 状态：0-禁用 1-启用。新建即启用，且只有单独的状态接口会改它——
     * 编辑词、改权重都不会顺手把一个已禁用的词放回去。
     */
    private Integer status;

    /** 收录时间，只写一次；改过权重也看不出是什么时候改的，因为表上没有 update_time */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
