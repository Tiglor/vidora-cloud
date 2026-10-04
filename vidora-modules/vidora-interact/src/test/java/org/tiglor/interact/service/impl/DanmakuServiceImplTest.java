package org.tiglor.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tiglor.common.core.BizException;
import org.tiglor.common.test.MpTestSupport;
import org.tiglor.interact.dto.DanmakuSendRequest;
import org.tiglor.interact.entity.Danmaku;
import org.tiglor.interact.mapper.DanmakuMapper;
import org.tiglor.interact.service.DanmakuService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 弹幕的默认值填充、时间窗口校验与拉取上限。
 * <p>
 * mock 的 Mapper 不会执行任何条件，所以「过滤和排序有没有写对」只能靠
 * 把条件构造器的 SQL 片段抓出来断言（见 {@link #listSql}）。
 * </p>
 */
class DanmakuServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long VIDEO_ID = 7L;

    DanmakuMapper mapper;
    DanmakuServiceImpl service;

    /** 记录传给 selectList 的条件构造器，用于断言 SQL 片段 */
    private final List<Wrapper<Danmaku>> listWrappers = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        MpTestSupport.initTableInfo(Danmaku.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(DanmakuMapper.class);
        service = new DanmakuServiceImpl();
        MpTestSupport.injectMapper(service, mapper, DanmakuMapper.class);

        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            listWrappers.add(invocation.getArgument(0));
            return List.of();
        });
    }

    private String listSql() {
        assertThat(listWrappers).as("应该恰好发起一次列表查询").hasSize(1);
        return listWrappers.get(0).getCustomSqlSegment();
    }

    private static DanmakuSendRequest request(BigDecimal appearTime) {
        DanmakuSendRequest request = new DanmakuSendRequest();
        request.setVideoId(VIDEO_ID);
        request.setContent("  前方高能  ");
        request.setAppearTime(appearTime);
        return request;
    }

    @Test
    @DisplayName("未登录不能发弹幕")
    void sendRequiresLogin() {
        assertThatThrownBy(() -> service.send(request(new BigDecimal("1.000")), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    @DisplayName("样式字段缺省时补上 DDL 里的列默认值，内容去掉首尾空白")
    void sendFillsDefaultsMatchingTheDdl() {
        Danmaku saved = service.send(request(new BigDecimal("12.5")), USER_ID);

        assertThat(saved.getContent()).isEqualTo("前方高能");
        assertThat(saved.getColor()).isEqualTo("#FFFFFF");
        assertThat(saved.getFontSize()).isEqualTo(25);
        assertThat(saved.getPosition()).isZero();
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getVideoId()).isEqualTo(VIDEO_ID);
        verify(mapper).insert(saved);
    }

    @Test
    @DisplayName("颜色统一成大写，避免同一颜色在库里存出两种写法")
    void sendUppercasesColor() {
        DanmakuSendRequest request = request(new BigDecimal("1"));
        request.setColor("#ab12cd");

        assertThat(service.send(request, USER_ID).getColor()).isEqualTo("#AB12CD");
    }

    @Test
    @DisplayName("appearTime 对齐到 DECIMAL(10,3)，多出来的位按四舍五入")
    void sendScalesAppearTimeToThreeDecimals() {
        assertThat(service.send(request(new BigDecimal("12.3456")), USER_ID).getAppearTime())
                .isEqualByComparingTo(new BigDecimal("12.346"));
        assertThat(service.send(request(new BigDecimal("12.3454")), USER_ID).getAppearTime())
                .isEqualByComparingTo(new BigDecimal("12.345"));
        assertThat(service.send(request(new BigDecimal("12")), USER_ID).getAppearTime().scale())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("时间窗口起点晚于终点时直接拒绝，不去查库")
    void listRejectsInvertedTimeWindow() {
        assertThatThrownBy(() -> service.listByVideo(VIDEO_ID,
                new BigDecimal("30"), new BigDecimal("10"), 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("起点不能晚于终点");
        assertThat(listWrappers).isEmpty();
    }

    @Test
    @DisplayName("videoId 为空时拒绝查询")
    void listRequiresVideoId() {
        assertThatThrownBy(() -> service.listByVideo(null, null, null, 100))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("videoId");
    }

    @Test
    @DisplayName("拉取条数被夹在 [1, 3000]，一个 limit=1000000 的请求拉不走整张表")
    void listClampsLimit() {
        service.listByVideo(VIDEO_ID, null, null, 1_000_000);
        assertThat(listSql()).contains("LIMIT " + DanmakuService.MAX_PER_LOAD);

        listWrappers.clear();
        service.listByVideo(VIDEO_ID, null, null, 0);
        assertThat(listSql()).contains("LIMIT 1");
    }

    @Test
    @DisplayName("查询只取未屏蔽的弹幕，按出现时间正序，且过滤与排序共用 idx_video_time")
    void listFiltersAndOrdersByTheIndexedColumns() {
        service.listByVideo(VIDEO_ID, new BigDecimal("10"), new BigDecimal("30"), 500);

        assertThat(listSql())
                .contains("video_id =")
                .contains("status =")
                .contains("appear_time >=")
                .contains("appear_time <=")
                .contains("ORDER BY appear_time ASC,id ASC");
    }

    @Test
    @DisplayName("时间窗口可省略，省略时不生成对应的条件")
    void listOmitsOpenEndedBounds() {
        service.listByVideo(VIDEO_ID, null, null, 500);

        assertThat(listSql()).doesNotContain("appear_time >=").doesNotContain("appear_time <=");
    }

    @Test
    @DisplayName("屏蔽一条不存在的弹幕返回 404")
    void setStatusOnMissingDanmakuIsNotFound() {
        when(mapper.update(any(), any())).thenReturn(0);
        when(mapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.setStatus(99L, 0))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("弹幕不存在");
    }

    @Test
    @DisplayName("重复设置成同一个状态是幂等的：条件更新影响 0 行，但记录存在就不报 404")
    void setStatusToTheSameValueIsIdempotent() {
        when(mapper.update(any(), any())).thenReturn(0);
        when(mapper.selectById(9L)).thenReturn(new Danmaku());

        service.setStatus(9L, 0);

        verify(mapper).selectById(9L);
    }

    @Test
    @DisplayName("status 只接受 0/1")
    void setStatusRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.setStatus(9L, 5))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("status 只能是");
    }
}
