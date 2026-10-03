package org.tiglor.common.core;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records;
    /** 总记录数 */
    private Long total;
    /** 每页大小 */
    private Long size;
    /** 当前页 */
    private Long current;
    /** 总页数 */
    private Long pages;

    public static <T> PageResult<T> of(List<T> records, Long total, Long size, Long current, Long pages) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setSize(size);
        result.setCurrent(current);
        result.setPages(pages);
        return result;
    }
}
