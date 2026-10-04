package org.tiglor.search.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.search.dto.SuggestRequest;
import org.tiglor.search.entity.SearchSuggest;

import java.time.LocalDate;
import java.util.List;

/**
 * 搜索建议词。
 * <p>
 * 联想框是搜索链路里请求量最大的接口——每敲一个字符就是一次——所以它必须便宜。
 * 但只有「未输入时的热门词」这一个视图进了缓存，带前缀的联想查询不缓存，见 {@link #top}。
 * </p>
 */
public interface SearchSuggestService extends IService<SearchSuggest> {

    /**
     * {@link #top} / {@link #suggest} 的条数上限。
     * <p>
     * 调用方必须先把入参夹到这个范围内再传进来：{@code top} 的 limit 同时是缓存 key，
     * 不设上限的话外部随便传几个不同的整数就能往 Redis 里塞任意多条缓存。
     * </p>
     */
    int MAX_LIMIT = 20;

    /** 搜索框未输入时展示的热门建议词，按权重倒序，走缓存 */
    List<SearchSuggest> top(int limit);

    /**
     * 按前缀联想。
     * <p>
     * <b>刻意不缓存</b>：前缀是用户输入的任意字符串，拿它当缓存 key 等于让外部决定
     * Redis 里有多少条记录。查询走 {@code likeRight}，{@code uk_keyword} 上的前缀范围扫描，本身够便宜。
     * </p>
     *
     * @param prefix 空或空白直接返回空列表——「列出全部建议词」由 {@link #top} 覆盖，
     *               放任空前缀走 {@code LIKE '%...'} 会白白全表扫一遍
     */
    List<SearchSuggest> suggest(String prefix, int limit);

    Page<SearchSuggest> page(long current, long size, String keyword, Integer status, Integer source);

    /** 新增。词全站唯一，唯一键是大小写不敏感的 */
    SearchSuggest create(SuggestRequest request);

    /**
     * 改一条建议词。可以改词本身，撞上别的行会报错。
     * <p>不动 {@code status}，启停走 {@link #setStatus}。</p>
     */
    SearchSuggest update(Long id, SuggestRequest request);

    void setStatus(Long id, int status);

    void delete(Long id);

    /**
     * 从某一天的搜索统计里挖词，把还没被收录的热词批量加成建议词（{@code source = 2}）。
     * <p>
     * 这是 {@code search_suggest.source} 里「自动挖掘」那一档的写入方。
     * 挖掘是**运营触发**的动作而不是定时任务：挖出来的词没人审过就可能直接进联想框，
     * 得有人看过 {@code minCount} 和结果再决定要不要放出去。
     * </p>
     *
     * @param date     从哪一天的统计里挖，null 表示今天
     * @param minCount 搜索次数门槛，低于它的不考虑；必须 &ge; 1
     * @param limit    最多挖多少个
     * @return 真正新增的条数（已存在的会被跳过，不计入）
     */
    int mine(LocalDate date, long minCount, int limit);
}
