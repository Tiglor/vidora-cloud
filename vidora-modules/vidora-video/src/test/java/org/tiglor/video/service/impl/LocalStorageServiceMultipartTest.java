package org.tiglor.video.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tiglor.common.core.BizException;
import org.tiglor.video.config.StorageProperties;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地存储的分片上传支撑能力。
 * <p>
 * 合并顺序一旦错了，产出的就是一个静默损坏的视频文件——不报错、能落库、播放时才炸。
 * 所以这里用真实文件系统跑一遍完整往返，而不是 mock。
 * </p>
 */
class LocalStorageServiceMultipartTest {

    private static final String OBJECT_KEY = "video/2026-10-03/abc123.mp4";
    private static final String PARTS_PREFIX = OBJECT_KEY + ".parts/";

    @TempDir
    Path tempDir;

    LocalStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setType("local");
        properties.setLocalDir(tempDir.toString());
        storage = new LocalStorageService(properties);
    }

    @Test
    @DisplayName("合并后字节与分片按序拼接的结果完全一致")
    void composeConcatenatesChunksInGivenOrder() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String name = PARTS_PREFIX + String.format("%05d", i);
            storage.upload(name, stream("chunk-" + i + ";"), "application/octet-stream", 9);
            parts.add(name);
        }

        storage.compose(OBJECT_KEY, parts);

        assertThat(read(OBJECT_KEY)).isEqualTo("chunk-0;chunk-1;chunk-2;");
    }

    @Test
    @DisplayName("合并只依赖调用方给的顺序，不依赖文件系统遍历顺序")
    void composeHonoursSourceOrderNotListingOrder() {
        storage.upload(PARTS_PREFIX + "00000", stream("A"), "application/octet-stream", 1);
        storage.upload(PARTS_PREFIX + "00001", stream("B"), "application/octet-stream", 1);

        storage.compose(OBJECT_KEY, List.of(PARTS_PREFIX + "00001", PARTS_PREFIX + "00000"));

        assertThat(read(OBJECT_KEY)).isEqualTo("BA");
    }

    @Test
    @DisplayName("零填充的分片名按字典序排列即为数值序")
    void zeroPaddedPartNamesSortNumerically() {
        for (int i : new int[]{10, 2, 0, 11, 1}) {
            storage.upload(PARTS_PREFIX + String.format("%05d", i), stream("x"), "application/octet-stream", 1);
        }

        assertThat(storage.listObjectNames(PARTS_PREFIX))
                .containsExactly(
                        PARTS_PREFIX + "00000",
                        PARTS_PREFIX + "00001",
                        PARTS_PREFIX + "00002",
                        PARTS_PREFIX + "00010",
                        PARTS_PREFIX + "00011");
    }

    @Test
    @DisplayName("列举只返回该前缀下的对象，不串到同目录的其它会话")
    void listObjectNamesIsScopedToPrefix() {
        storage.upload(PARTS_PREFIX + "00000", stream("mine"), "application/octet-stream", 4);
        storage.upload(OBJECT_KEY + ".parts.bak/00000", stream("not-a-part"), "application/octet-stream", 10);
        storage.upload("video/2026-10-03/other.mp4.parts/00000", stream("other"), "application/octet-stream", 5);
        storage.upload(OBJECT_KEY, stream("merged"), "video/mp4", 6);

        assertThat(storage.listObjectNames(PARTS_PREFIX)).containsExactly(PARTS_PREFIX + "00000");
    }

    @Test
    @DisplayName("前缀目录还不存在时返回空列表，而不是抛异常")
    void listObjectNamesOnMissingPrefixReturnsEmpty() {
        assertThat(storage.listObjectNames("video/1999-01-01/nope.mp4.parts/")).isEmpty();
    }

    @Test
    @DisplayName("exists 区分「不存在」与「存在」，目录不算对象")
    void existsReflectsRegularFilesOnly() {
        assertThat(storage.exists(OBJECT_KEY)).isFalse();

        storage.upload(OBJECT_KEY, stream("data"), "video/mp4", 4);

        assertThat(storage.exists(OBJECT_KEY)).isTrue();
        assertThat(storage.exists(OBJECT_KEY + ".parts/")).isFalse();
    }

    @Test
    @DisplayName("合并后批量清理分片，目标对象保留")
    void deleteAllRemovesPartsAndKeepsTarget() {
        List<String> parts = List.of(PARTS_PREFIX + "00000", PARTS_PREFIX + "00001");
        parts.forEach(name -> storage.upload(name, stream("x"), "application/octet-stream", 1));
        storage.compose(OBJECT_KEY, parts);

        storage.deleteAll(parts);

        assertThat(parts).allMatch(name -> !storage.exists(name));
        assertThat(storage.exists(OBJECT_KEY)).isTrue();
    }

    @Test
    @DisplayName("缺少分片时合并失败并指明是哪一片，不会产出半个文件")
    void composeFailsWhenASourcePartIsMissing() {
        storage.upload(PARTS_PREFIX + "00000", stream("only-one"), "application/octet-stream", 8);

        assertThatThrownBy(() -> storage.compose(OBJECT_KEY,
                List.of(PARTS_PREFIX + "00000", PARTS_PREFIX + "00001")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("00001");

        assertThat(storage.exists(OBJECT_KEY)).isFalse();
    }

    @Test
    @DisplayName("空的分片清单直接拒绝，避免生成 0 字节视频")
    void composeRejectsEmptySourceList() {
        assertThatThrownBy(() -> storage.compose(OBJECT_KEY, List.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有可合并的分片");
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private String read(String objectName) {
        try {
            return Files.readString(storage.fetchToTempFile(objectName));
        } catch (Exception e) {
            throw new AssertionError("读取合并结果失败", e);
        }
    }
}
