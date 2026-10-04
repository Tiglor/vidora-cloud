package org.tiglor.common.redis;

/**
 * 缓存名常量。
 * <p>缓存名同时决定 Redis key 前缀（见 {@link RedisCacheConfig}），
 * 跨服务共用同一个 Redis 实例时靠它避免键冲突。</p>
 */
public final class CacheNames {

    /** 某用户可见的菜单树，key = userId */
    public static final String MENU_USER_TREE = "menu:user-tree";

    /** 全量菜单树（管理端），key 固定为 all */
    public static final String MENU_ALL_TREE = "menu:all-tree";

    /** 某角色已授权的菜单ID，key = roleId */
    public static final String MENU_ROLE_IDS = "menu:role-ids";

    /** 启用状态的分类列表，key 固定为 enabled */
    public static final String CATEGORY_LIST = "content:category-list";

    /** 热门标签（标签云），key = limit */
    public static final String TAG_HOT = "content:tag-hot";

    /** 某个流类型下的全部配置项，key = feedType */
    public static final String FEED_CONFIG = "content:feed-config";

    /** 某一天的热搜榜单，key = rankDate（yyyy-MM-dd） */
    public static final String HOT_SEARCH_BOARD = "content:hot-search-board";

    /** 某个场景下某个算法的启用配置项，key = scene + ':' + algoType */
    public static final String RECOMMEND_ALGO_CONFIG = "recommend:algo-config";

    /**
     * 搜索框未输入时展示的热门建议词，key = limit。
     * <p>只缓存这一个视图：带前缀的联想查询 key 是用户输入的任意字符串，缓存它等于让外部决定 Redis 里有多少条记录。</p>
     */
    public static final String SEARCH_SUGGEST_TOP = "search:suggest-top";

    /** 视频播放地址，key = videoId */
    public static final String VIDEO_PLAY_URL = "video:play-url";

    /** 视频的累计计数（播放/点赞/评论/分享），key = videoId。计数是准实时的，靠 TTL 自然过期 */
    public static final String INTERACT_VIDEO_TOTALS = "interact:video-totals";

    private CacheNames() {
    }
}
