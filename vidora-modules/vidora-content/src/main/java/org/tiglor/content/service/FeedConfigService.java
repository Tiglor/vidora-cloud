package org.tiglor.content.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.content.dto.FeedConfigRequest;
import org.tiglor.content.entity.FeedConfig;

import java.util.List;
import java.util.Map;

public interface FeedConfigService extends IService<FeedConfig> {

    /**
     * 某个流的全部配置，摊成 key → value 的 Map 给推荐服务直接读。
     * <p>
     * 值全为 null 的项不会出现在 Map 里——{@code Map.of} / {@code Collectors.toMap}
     * 都不接受 null 值，而且「没配」和「配了个 null」对调用方本来就没区别。
     * </p>
     */
    Map<String, String> configsOf(String feedType);

    /** 管理端视图，保留 description 与 id */
    List<FeedConfig> listByFeedType(String feedType);

    /** 按 (feedType, configKey) 建或改 */
    FeedConfig upsert(FeedConfigRequest request);

    void delete(Long id);
}
