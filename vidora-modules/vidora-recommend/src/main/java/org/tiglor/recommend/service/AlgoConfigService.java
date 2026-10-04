package org.tiglor.recommend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.recommend.dto.AlgoConfigRequest;
import org.tiglor.recommend.entity.AlgoConfig;
import org.tiglor.recommend.enums.AlgoType;
import org.tiglor.recommend.enums.RecommendScene;

import java.util.List;
import java.util.Map;

public interface AlgoConfigService extends IService<AlgoConfig> {

    /**
     * 某个场景下某个算法的启用配置，摊成 key → value 的 Map 给算法侧直接读。
     * <p>
     * 收枚举而不是字符串：{@code scene} 和 {@code algoType} 一起构成缓存 key，
     * 收字符串的话 {@code "HOME"} 和 {@code "home"} 会各占一个内容相同的条目。
     * 让调用方先 {@code RecommendScene.of(...)} 归一，key 才是规范的。
     * </p>
     * <p>
     * 值全为 null 的项不会出现在 Map 里——{@code Collectors.toMap} 不接受 null 值，
     * 而且「没配」和「配了个 null」对算法侧本来就没区别。
     * </p>
     */
    Map<String, String> configsOf(RecommendScene scene, AlgoType algoType);

    /** 某个场景下全部启用的配置项，按算法、再按 key 排序 */
    List<AlgoConfig> listEnabled(RecommendScene scene);

    /** 管理端分页，三个过滤条件都可选；scene / algoType 传字符串是为了允许「不过滤」 */
    Page<AlgoConfig> page(long current, long size, String scene, String algoType, Integer status);

    /** 按 (scene, algoType, configKey) 建或改；不会改动 status */
    AlgoConfig upsert(AlgoConfigRequest request);

    /** 禁用(0) / 启用(1)。禁用意味着算法回落到默认参数，不是删除 */
    void setStatus(Long id, int status);

    void delete(Long id);
}
