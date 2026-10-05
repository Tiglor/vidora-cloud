package org.tiglor.content.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.content.dto.HotSearchRequest;
import org.tiglor.content.entity.HotSearch;

import java.time.LocalDate;
import java.util.List;

public interface HotSearchService extends IService<HotSearch> {

    /**
     * 某一天的榜单，只含上线的词，按 rank 升序。
     *
     * @param date 必须已解析成具体日期：它同时是缓存 key，null 会让 Spring Cache 直接抛异常
     */
    List<HotSearch> board(LocalDate date);

    /**
     * 管理端看板：某天全部词条，含下线的。
     * <p>
     * 不能复用 {@link #board}——它按 {@code status=1} 过滤，敏感词一下线就从界面上消失，
     * 而重新上线需要带 {@code id} 调 {@link #setStatus}，那个 id 已经没有任何地方能查到了。
     * </p>
     *
     * @param status null 表示上下线都要
     */
    List<HotSearch> adminBoard(LocalDate date, Integer status);

    /**
     * 按热度重算某天的排名，返回真正发生变化的行数。
     * <p>
     * 排序键是 {@code heat_score DESC, search_count DESC, id ASC}——最后那个 id 是必需的：
     * 热度相同的词如果没有稳定的第三排序键，每次重算都会得到一份不同的名次，
     * 榜单在两次刷新之间反复横跳。
     * </p>
     */
    int rebuild(LocalDate date);

    /** 加词或刷新热度；不接受外部指定 rank */
    HotSearch upsert(HotSearchRequest request);

    /** 上线 / 下线（下线敏感词），重复设成同一个值是幂等的 */
    void setStatus(Long id, int status);
}
