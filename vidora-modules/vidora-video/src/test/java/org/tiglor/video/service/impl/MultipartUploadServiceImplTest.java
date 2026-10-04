package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.tiglor.common.core.BizException;
import org.tiglor.video.config.StorageProperties;
import org.tiglor.video.dto.MultipartCompleteRequest;
import org.tiglor.video.dto.MultipartInitRequest;
import org.tiglor.video.dto.MultipartInitResult;
import org.tiglor.video.dto.MultipartProgress;
import org.tiglor.video.entity.MultipartUpload;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.mapper.MultipartUploadMapper;
import org.tiglor.video.service.StorageService;
import org.tiglor.video.service.VideoInfoService;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片上传的业务规则：归属校验、分片大小、合并顺序、秒传与断点续传。
 * <p>
 * 用 mock 的 Mapper 替掉数据库，但保留真实的 MyBatis-Plus lambda 条件构造器
 * （{@link #initTableInfo()} 手动初始化列名缓存），顺带验证实体字段到列名的映射。
 * </p>
 */
class MultipartUploadServiceImplTest {

    private static final Long OWNER = 42L;
    private static final Long INTRUDER = 43L;
    private static final String UPLOAD_ID = "abc123";
    private static final String OBJECT_KEY = "video/2026-10-03/abc123.mp4";
    private static final String PARTS_PREFIX = OBJECT_KEY + ".parts/";
    private static final String FILE_HASH = "d41d8cd98f00b204e9800998ecf8427e";
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;

    MultipartUploadMapper mapper;
    StorageService storageService;
    VideoInfoService videoInfoService;
    MultipartUploadServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 脱离 Spring/MyBatis 容器时 lambda 列名缓存是空的，条件构造器会在第一次 eq() 就抛异常
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MultipartUpload.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(MultipartUploadMapper.class);
        storageService = mock(StorageService.class);
        videoInfoService = mock(VideoInfoService.class);
        service = new MultipartUploadServiceImpl(storageService, new StorageProperties(), videoInfoService);

        setField(service, CrudRepository.class, "baseMapper", mapper);
        // MP 3.5.14 的 getEntityClass() 是从 mapper 的 MapperProxy 上反推 mapper 接口的，
        // mock 的 mapper 没有这层代理，只能把已解析好的类型直接塞进去
        setField(service, AbstractRepository.class, "mapperClass", MultipartUploadMapper.class);

        when(mapper.insert(any(MultipartUpload.class))).thenReturn(1);
        when(mapper.updateById(any(MultipartUpload.class))).thenReturn(1);
        when(mapper.update(any(), any())).thenReturn(1);
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ---------- 归属与分片校验 ----------

    @Test
    @DisplayName("不能拿别人的 uploadId 传分片")
    void rejectsChunkUploadedByAnotherUser() {
        givenSingleSession(session(OWNER, 3, 0));

        assertThatThrownBy(() -> service.uploadChunk(UPLOAD_ID, 0, part(CHUNK_SIZE), INTRUDER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权操作他人的上传任务");

        verify(storageService, never()).upload(anyString(), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("分片下标越界直接拒绝")
    void rejectsOutOfRangeChunkIndex() {
        givenSingleSession(session(OWNER, 3, 0));

        assertThatThrownBy(() -> service.uploadChunk(UPLOAD_ID, 3, part(CHUNK_SIZE), OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("越界");
        assertThatThrownBy(() -> service.uploadChunk(UPLOAD_ID, -1, part(CHUNK_SIZE), OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("越界");
    }

    @Test
    @DisplayName("非末片大小必须正好等于 chunkSize，否则合并出来一定是坏文件")
    void rejectsChunkWithUnexpectedSize() {
        givenSingleSession(session(OWNER, 3, 0));

        assertThatThrownBy(() -> service.uploadChunk(UPLOAD_ID, 0, part(CHUNK_SIZE - 1), OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分片大小不符");
    }

    @Test
    @DisplayName("末片允许不足 chunkSize")
    void acceptsShortFinalChunk() {
        givenSingleSession(session(OWNER, 3, 0, CHUNK_SIZE * 2L + 1024));

        service.uploadChunk(UPLOAD_ID, 2, part(1024), OWNER);

        verify(storageService).upload(eq(PARTS_PREFIX + "00002"), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("已合并的会话不能再传分片")
    void rejectsChunkAfterMerge() {
        MultipartUpload merged = session(OWNER, 3, 3);
        merged.setStatus(2);
        givenSingleSession(merged);

        assertThatThrownBy(() -> service.uploadChunk(UPLOAD_ID, 0, part(CHUNK_SIZE), OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上传已完成");
    }

    @Test
    @DisplayName("重传同一下标只覆盖写，不重复计数")
    void reuploadedChunkIsNotCountedTwice() {
        givenSingleSession(session(OWNER, 3, 1));
        when(storageService.exists(PARTS_PREFIX + "00001")).thenReturn(true);

        service.uploadChunk(UPLOAD_ID, 1, part(CHUNK_SIZE), OWNER);

        verify(storageService).upload(eq(PARTS_PREFIX + "00001"), any(), anyString(), anyLong());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("首次收到分片时原子自增计数")
    void firstChunkUploadIncrementsCounter() {
        givenSingleSession(session(OWNER, 3, 0));

        service.uploadChunk(UPLOAD_ID, 0, part(CHUNK_SIZE), OWNER);

        verify(mapper, atLeastOnce()).update(any(), any());
    }

    // ---------- 合并 ----------

    @Test
    @DisplayName("分片未收齐时拒绝合并，并报出缺失的下标")
    void completeRejectsWhenChunksAreMissing() {
        givenSingleSession(session(OWNER, 3, 2));
        when(storageService.listObjectNames(PARTS_PREFIX))
                .thenReturn(List.of(PARTS_PREFIX + "00000", PARTS_PREFIX + "00002"));

        assertThatThrownBy(() -> service.complete(request(), OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分片未收齐")
                .hasMessageContaining("缺失下标：1");

        verify(storageService, never()).compose(anyString(), anyList());
    }

    @Test
    @DisplayName("合并按下标顺序取分片，不依赖存储的列举顺序")
    void completeComposesPartsInIndexOrder() {
        givenSingleSession(session(OWNER, 3, 3));
        when(storageService.listObjectNames(PARTS_PREFIX)).thenReturn(List.of(
                PARTS_PREFIX + "00002",
                PARTS_PREFIX + "00000",
                PARTS_PREFIX + "00001"));
        givenRegisteredVideo(7L);

        VideoInfo result = service.complete(request(), OWNER);

        ArgumentCaptor<List<String>> captor = listOfStrings();
        verify(storageService).compose(eq(OBJECT_KEY), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                PARTS_PREFIX + "00000", PARTS_PREFIX + "00001", PARTS_PREFIX + "00002");
        verify(storageService).deleteAll(captor.getValue());
        assertThat(result.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("合并成功后把视频 ID 回写到会话，重复调用 complete 不会重复建视频")
    void completeIsIdempotentOnceVideoIsLinked() {
        MultipartUpload session = session(OWNER, 3, 3);
        session.setStatus(2);
        session.setVideoId(7L);
        givenSingleSession(session);
        VideoInfo existing = new VideoInfo();
        existing.setId(7L);
        when(videoInfoService.getById(7L)).thenReturn(existing);

        VideoInfo result = service.complete(request(), OWNER);

        assertThat(result.getId()).isEqualTo(7L);
        verify(videoInfoService, never()).registerStoredVideo(any());
        verify(storageService, never()).compose(anyString(), anyList());
    }

    @Test
    @DisplayName("合并后视频记录带上源文件的大小与哈希，没传标题时退回文件名")
    void completeCarriesFileMetadataOntoTheVideo() {
        long fileSize = CHUNK_SIZE * 2L + 1024;
        MultipartUpload session = session(OWNER, 3, 3, fileSize);
        session.setFileName("我的视频.mp4");
        givenSingleSession(session);
        when(storageService.listObjectNames(PARTS_PREFIX)).thenReturn(List.of(
                PARTS_PREFIX + "00000", PARTS_PREFIX + "00001", PARTS_PREFIX + "00002"));
        givenRegisteredVideo(9L);

        MultipartCompleteRequest request = request();
        request.setTitle(null);
        service.complete(request, OWNER);

        ArgumentCaptor<VideoInfo> captor = ArgumentCaptor.forClass(VideoInfo.class);
        verify(videoInfoService).registerStoredVideo(captor.capture());
        VideoInfo draft = captor.getValue();
        assertThat(draft.getStoragePath()).isEqualTo(OBJECT_KEY);
        assertThat(draft.getFileSize()).isEqualTo(fileSize);
        assertThat(draft.getFileHash()).isEqualTo(FILE_HASH);
        assertThat(draft.getUserId()).isEqualTo(OWNER);
        assertThat(draft.getTitle()).isEqualTo("我的视频.mp4");
    }

    // ---------- 秒传与断点续传 ----------

    @Test
    @DisplayName("同 hash 同大小且对象还在时命中秒传，不用再传任何分片")
    void initReturnsInstantWhenReusableObjectExists() {
        String sharedObjectKey = "video/2026-01-01/someone-else.mp4";
        MultipartUpload mergedElsewhere = session(999L, 3, 3);
        mergedElsewhere.setStatus(2);
        mergedElsewhere.setObjectKey(sharedObjectKey);
        givenMergedCandidates(mergedElsewhere);
        when(storageService.exists(sharedObjectKey)).thenReturn(true);

        MultipartInitResult result = service.init(initRequest(), OWNER);

        assertThat(result.isInstant()).isTrue();
        assertThat(result.getUploadedIndexes()).isEmpty();
        MultipartUpload saved = captureInsertedSession();
        assertThat(saved.getObjectKey()).isEqualTo(sharedObjectKey);
        assertThat(saved.getStatus()).isEqualTo(2);
        assertThat(saved.getUserId()).isEqualTo(OWNER);
        assertThat(saved.getCompletedChunks()).isEqualTo(3);
        // 秒传是另开一行指向已有对象，绝不能改写别人那条会话
        verify(mapper, never()).updateById(any(MultipartUpload.class));
    }

    @Test
    @DisplayName("最新那条已合并记录的对象被清理时，回退到更旧但仍存在的一条")
    void initFallsBackToOlderCandidateWhoseObjectStillExists() {
        MultipartUpload stale = session(999L, 3, 3);
        stale.setStatus(2);
        MultipartUpload alive = session(888L, 3, 3);
        alive.setStatus(2);
        alive.setObjectKey("video/2026-01-01/older.mp4");
        givenMergedCandidates(stale, alive);
        when(storageService.exists(OBJECT_KEY)).thenReturn(false);
        when(storageService.exists("video/2026-01-01/older.mp4")).thenReturn(true);

        MultipartInitResult result = service.init(initRequest(), OWNER);

        assertThat(result.isInstant()).isTrue();
        assertThat(captureInsertedSession().getObjectKey()).isEqualTo("video/2026-01-01/older.mp4");
    }

    @Test
    @DisplayName("库里标记已合并但对象全没了时不命中秒传，重新开一个会话")
    void initFallsBackToNewSessionWhenReusableObjectIsGone() {
        MultipartUpload mergedElsewhere = session(999L, 3, 3);
        mergedElsewhere.setStatus(2);
        givenMergedCandidates(mergedElsewhere);
        when(storageService.exists(OBJECT_KEY)).thenReturn(false);

        MultipartInitResult result = service.init(initRequest(), OWNER);

        assertThat(result.isInstant()).isFalse();
        MultipartUpload saved = captureInsertedSession();
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getUploadId()).isNotBlank();
        assertThat(saved.getObjectKey()).contains(saved.getUploadId());
    }

    @Test
    @DisplayName("自己传了一半的会话直接续传，返回服务端实测已收到的下标")
    void initResumesOwnSessionWithUploadedIndexes() {
        when(mapper.selectOne(any())).thenReturn(session(OWNER, 3, 1));
        when(storageService.listObjectNames(PARTS_PREFIX)).thenReturn(List.of(PARTS_PREFIX + "00001"));

        MultipartInitResult result = service.init(initRequest(), OWNER);

        assertThat(result.isInstant()).isFalse();
        assertThat(result.getUploadId()).isEqualTo(UPLOAD_ID);
        assertThat(result.getUploadedIndexes()).containsExactly(1);
        verify(mapper, never()).insert(any(MultipartUpload.class));
    }

    @Test
    @DisplayName("分片大小变了就作废旧会话重开一条，旧分片的边界对不上没法用")
    void initStartsNewSessionWhenChunkSizeChanged() {
        when(mapper.selectOne(any())).thenReturn(session(OWNER, 3, 1));
        MultipartInitRequest request = initRequest();
        request.setChunkSize(CHUNK_SIZE * 2);

        MultipartInitResult result = service.init(request, OWNER);

        assertThat(result.getUploadId()).isNotEqualTo(UPLOAD_ID);
        assertThat(result.getUploadedIndexes()).isEmpty();
        MultipartUpload saved = captureInsertedSession();
        assertThat(saved.getChunkSize()).isEqualTo(CHUNK_SIZE * 2);
        assertThat(saved.getTotalChunks()).isEqualTo(2);
        assertThat(saved.getStatus()).isZero();
    }

    @Test
    @DisplayName("分片数超过单次 compose 的 10000 片上限时拒绝，让前端调大 chunkSize")
    void initRejectsTooManyChunks() {
        MultipartInitRequest request = initRequest();
        request.setFileSize(CHUNK_SIZE * 10_001L);

        assertThatThrownBy(() -> service.init(request, OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过上限");

        verify(mapper, never()).selectList(any());
        verify(mapper, never()).insert(any(MultipartUpload.class));
    }

    // ---------- 秒传会话的收尾 ----------

    @Test
    @DisplayName("秒传会话 complete 时直接复用对象，不再走合并")
    void completeOnInstantSessionReusesObjectWithoutMerging() {
        MultipartUpload instant = session(OWNER, 3, 3);
        instant.setStatus(2);
        givenSingleSession(instant);
        when(storageService.exists(OBJECT_KEY)).thenReturn(true);
        givenRegisteredVideo(11L);

        VideoInfo result = service.complete(request(), OWNER);

        assertThat(result.getId()).isEqualTo(11L);
        verify(storageService, never()).compose(anyString(), anyList());
        verify(storageService, never()).listObjectNames(anyString());
        ArgumentCaptor<VideoInfo> captor = ArgumentCaptor.forClass(VideoInfo.class);
        verify(videoInfoService).registerStoredVideo(captor.capture());
        assertThat(captor.getValue().getStoragePath()).isEqualTo(OBJECT_KEY);
    }

    @Test
    @DisplayName("秒传命中的对象在 complete 前被清掉时拒绝，不能登记出指向空气的视频")
    void completeRejectsWhenTheReusedObjectIsGone() {
        MultipartUpload instant = session(OWNER, 3, 3);
        instant.setStatus(2);
        givenSingleSession(instant);
        when(storageService.exists(OBJECT_KEY)).thenReturn(false);

        assertThatThrownBy(() -> service.complete(request(), OWNER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("源文件对象已不存在");

        verify(videoInfoService, never()).registerStoredVideo(any());
    }

    // ---------- 进度 ----------

    @Test
    @DisplayName("进度以下标清单为准，不用可能虚高的 completed_chunks")
    void progressReportsIndexesActuallyPresentInStorage() {
        givenSingleSession(session(OWNER, 3, 3));
        when(storageService.listObjectNames(PARTS_PREFIX))
                .thenReturn(List.of(PARTS_PREFIX + "00000", PARTS_PREFIX + "00002"));

        MultipartProgress progress = service.progress(UPLOAD_ID, OWNER);

        assertThat(progress.getUploadedCount()).isEqualTo(2);
        assertThat(progress.getUploadedIndexes()).containsExactly(0, 2);
        assertThat(progress.getTotalChunks()).isEqualTo(3);
    }

    // ---------- 夹具 ----------

    private void givenSingleSession(MultipartUpload session) {
        when(mapper.selectOne(any())).thenReturn(session);
    }

    /** 秒传扫描已合并候选走的是 selectList（要看多条，最新那条的对象可能已被清理） */
    private void givenMergedCandidates(MultipartUpload... rows) {
        when(mapper.selectList(any())).thenReturn(List.of(rows));
    }

    private MultipartUpload captureInsertedSession() {
        ArgumentCaptor<MultipartUpload> captor = ArgumentCaptor.forClass(MultipartUpload.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    private void givenRegisteredVideo(long videoId) {
        VideoInfo saved = new VideoInfo();
        saved.setId(videoId);
        when(videoInfoService.registerStoredVideo(any(VideoInfo.class))).thenReturn(saved);
    }

    private static MultipartUpload session(Long userId, int totalChunks, int completedChunks) {
        return session(userId, totalChunks, completedChunks, CHUNK_SIZE * (long) totalChunks);
    }

    private static MultipartUpload session(Long userId, int totalChunks, int completedChunks, long fileSize) {
        MultipartUpload session = new MultipartUpload();
        session.setId(1L);
        session.setUploadId(UPLOAD_ID);
        session.setUserId(userId);
        session.setFileName("test.mp4");
        session.setFileHash(FILE_HASH);
        session.setFileSize(fileSize);
        session.setChunkSize(CHUNK_SIZE);
        session.setTotalChunks(totalChunks);
        session.setCompletedChunks(completedChunks);
        session.setBucket("videos");
        session.setObjectKey(OBJECT_KEY);
        session.setStatus(0);
        return session;
    }

    private static MultipartCompleteRequest request() {
        MultipartCompleteRequest request = new MultipartCompleteRequest();
        request.setUploadId(UPLOAD_ID);
        request.setTitle("标题");
        return request;
    }

    private static MultipartInitRequest initRequest() {
        MultipartInitRequest request = new MultipartInitRequest();
        request.setFileName("test.mp4");
        request.setFileHash(FILE_HASH);
        request.setChunkSize(CHUNK_SIZE);
        request.setFileSize(CHUNK_SIZE * 3L);
        return request;
    }

    private static MockMultipartFile part(long size) {
        return new MockMultipartFile("file", "chunk.bin", "application/octet-stream", new byte[(int) size]);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<String>> listOfStrings() {
        return ArgumentCaptor.forClass(List.class);
    }
}
