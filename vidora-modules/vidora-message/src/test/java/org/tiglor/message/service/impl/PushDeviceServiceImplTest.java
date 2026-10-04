package org.tiglor.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.tiglor.common.core.BizException;
import org.tiglor.common.test.MpTestSupport;
import org.tiglor.message.dto.PushDeviceRequest;
import org.tiglor.message.entity.PushDevice;
import org.tiglor.message.mapper.PushDeviceMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 推送设备绑定的两件事：vendor 绝不能是 NULL，以及换绑时必须先失效别人名下的同一条 token。
 * <p>
 * 前者是 MySQL 唯一索引把 NULL 当互不相等导致的——漏了的话同一台设备每次重绑都多一行；
 * 后者是隐私问题，A 的私信推给正在用这台设备的 B。
 * </p>
 */
class PushDeviceServiceImplTest {

    private static final long ME = 42L;
    private static final String TOKEN = " token-abc ";

    PushDeviceMapper mapper;
    PushDeviceServiceImpl service;

    private final List<Wrapper<PushDevice>> oneWrappers = new ArrayList<>();
    private final List<Wrapper<PushDevice>> listWrappers = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        MpTestSupport.initTableInfo(PushDevice.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(PushDeviceMapper.class);
        service = new PushDeviceServiceImpl();
        MpTestSupport.injectMapper(service, mapper, PushDeviceMapper.class);

        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            oneWrappers.add(invocation.getArgument(0));
            return null;
        });
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            listWrappers.add(invocation.getArgument(0));
            return List.of();
        });
    }

    private static PushDeviceRequest request(String deviceType, String vendor) {
        PushDeviceRequest request = new PushDeviceRequest();
        request.setDeviceType(deviceType);
        request.setPushToken(TOKEN);
        request.setVendor(vendor);
        return request;
    }

    /** 回读那条绑定时用的查询条件 */
    private String readBackSql() {
        assertThat(oneWrappers).as("应该恰好回读一次").hasSize(1);
        return oneWrappers.get(0).getCustomSqlSegment();
    }

    /** 列表查询的条件值 */
    private Object[] listValues() {
        assertThat(listWrappers).as("应该恰好发起一次列表查询").hasSize(1);
        return params(listWrappers.get(0));
    }

    private static Object[] params(Wrapper<PushDevice> wrapper) {
        return ((AbstractWrapper<PushDevice, ?, ?>) wrapper).getParamNameValuePairs().values().toArray();
    }

    // ---------- bind ----------

    @Test
    @DisplayName("不传 vendor 时按 deviceType 推断：唯一索引把 NULL 当互不相等，真写 NULL 这个键就形同虚设")
    void bindInfersTheVendorWhenItIsOmitted() {
        service.bind(request("ios", null), ME);
        verify(mapper).upsert(ME, "ios", "token-abc", "apns");

        service.bind(request("android", null), ME);
        verify(mapper).upsert(ME, "android", "token-abc", "fcm");

        service.bind(request("harmony", null), ME);
        verify(mapper).upsert(ME, "harmony", "token-abc", "huawei");
    }

    @Test
    @DisplayName("空白字符串等同于没传，一样走推断")
    void bindTreatsABlankVendorAsOmitted() {
        service.bind(request("ios", "   "), ME);

        verify(mapper).upsert(ME, "ios", "token-abc", "apns");
    }

    @Test
    @DisplayName("deviceType / vendor / token 一律去空格并转小写：大小写不同会被唯一索引当成两台设备")
    void bindNormalizesCaseAndWhitespace() {
        service.bind(request(" Android ", " FCM "), ME);

        verify(mapper).upsert(ME, "android", "token-abc", "fcm");
        assertThat(readBackSql())
                .contains("user_id =")
                .contains("device_type =")
                .contains("vendor =");
    }

    @Test
    @DisplayName("推断不出厂商时直接报错，而不是写一个 NULL 进去")
    void bindRejectsADeviceTypeItCannotInferFor() {
        assertThatThrownBy(() -> service.bind(request("windows", null), ME))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法为 deviceType 推断推送厂商");
        verify(mapper, never()).upsert(any(), any(), any(), any());
    }

    @Test
    @DisplayName("先失效别人名下的同一条 token 再绑到自己名下，顺序反了推送会串号")
    void bindInvalidatesOtherUsersTokensBeforeUpserting() {
        when(mapper.invalidateTokenOfOtherUsers("token-abc", ME)).thenReturn(2);

        service.bind(request("ios", null), ME);

        InOrder order = inOrder(mapper);
        order.verify(mapper).invalidateTokenOfOtherUsers("token-abc", ME);
        order.verify(mapper).upsert(ME, "ios", "token-abc", "apns");
    }

    @Test
    @DisplayName("upsert 的返回值不是行数（1-插入 2-更新 0-没变），任何一种都算绑定成功")
    void bindDoesNotTreatTheUpsertReturnValueAsARowCount() {
        when(mapper.upsert(any(), any(), any(), any())).thenReturn(0);
        assertThat(service.bind(request("ios", null), ME)).isNull();

        when(mapper.upsert(any(), any(), any(), any())).thenReturn(2);
        PushDevice stored = new PushDevice();
        stored.setId(1L);
        stored.setUserId(ME);
        doReturn(stored).when(mapper).selectOne(any());
        assertThat(service.bind(request("ios", null), ME)).isSameAs(stored);
    }

    @Test
    @DisplayName("绑定要求登录")
    void bindRequiresLogin() {
        assertThatThrownBy(() -> service.bind(request("ios", null), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        verifyNoInteractions(mapper);
    }

    // ---------- unbind ----------

    @Test
    @DisplayName("不带过滤条件就是解绑我的全部通道")
    void unbindWithoutFiltersUnbindsEverything() {
        when(mapper.unbind(ME, null, null)).thenReturn(3);

        assertThat(service.unbind(ME, null, null)).isEqualTo(3);

        verify(mapper).unbind(ME, null, null);
    }

    @Test
    @DisplayName("解绑的过滤条件同样归一化，否则「IOS」解不掉「ios」那一行")
    void unbindNormalizesItsFilters() {
        service.unbind(ME, " IOS ", " APNS ");

        verify(mapper).unbind(ME, "ios", "apns");
    }

    @Test
    @DisplayName("空白字符串当作没传，而不是拿去等值匹配一个不存在的空串")
    void unbindTreatsBlankFiltersAsNoFilter() {
        service.unbind(ME, "  ", "");

        verify(mapper).unbind(ME, null, null);
    }

    @Test
    @DisplayName("解绑要求登录")
    void unbindRequiresLogin() {
        assertThatThrownBy(() -> service.unbind(null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        verifyNoInteractions(mapper);
    }

    // ---------- listMine ----------

    @Test
    @DisplayName("只列出我自己的、还有效的绑定")
    void listMineOnlyReturnsMyActiveBindings() {
        service.listMine(ME);

        assertThat(listWrappers).hasSize(1);
        assertThat(listWrappers.get(0).getCustomSqlSegment())
                .contains("user_id =")
                .contains("status =")
                .contains("ORDER BY update_time DESC");
        assertThat(listValues()).containsExactlyInAnyOrder(ME, 1);
    }

    @Test
    @DisplayName("列自己的设备要求登录")
    void listMineRequiresLogin() {
        assertThatThrownBy(() -> service.listMine(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        verifyNoInteractions(mapper);
    }
}
