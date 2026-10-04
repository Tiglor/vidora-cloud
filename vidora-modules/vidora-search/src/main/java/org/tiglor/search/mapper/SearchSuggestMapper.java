package org.tiglor.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.search.entity.SearchSuggest;

import java.util.List;

@Mapper
public interface SearchSuggestMapper extends BaseMapper<SearchSuggest> {

    /**
     * 批量收录建议词，撞上 {@code uk_keyword} 的行**跳过**而不是报错。
     * <p>
     * 给自动挖掘用的：候选来自 {@code SearchKeywordStatMapper.mineCandidates}，
     * 那个查询已经排掉了库里已有的词，但「查候选」和「写候选」之间隔着一次网络往返，
     * 并发的两次挖掘、或者期间运营手工加了同一个词，都会让普通的批量 INSERT 整批失败。
     * {@code INSERT IGNORE} 把重复键降级成警告，返回的是**真正插进去的行数**。
     * </p>
     * <p>
     * 代价是它同时会吞掉别的错误（比如数据被截断）也变成警告。这里能接受：
     * 关键词来自本库的统计表、长度天然在 200 以内，{@code weight} 和 {@code source} 都是整数。
     * </p>
     * <p>
     * 单条语句最多拼 {@code SearchSuggestServiceImpl.BATCH_CHUNK} 行，超了会撞 {@code max_allowed_packet}。
     * </p>
     */
    @Insert("""
            <script>
            INSERT IGNORE INTO search_suggest (keyword, weight, source)
            VALUES
            <foreach collection="items" item="it" separator=",">
                (#{it.keyword}, #{it.weight}, #{it.source})
            </foreach>
            </script>
            """)
    int batchInsertIgnore(@Param("items") List<SearchSuggest> items);
}
