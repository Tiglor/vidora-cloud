package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.video.config.StorageProperties;
import org.tiglor.video.dto.MultipartCompleteRequest;
import org.tiglor.video.dto.MultipartInitRequest;
import org.tiglor.video.dto.MultipartInitResult;
import org.tiglor.video.dto.MultipartProgress;
import org.tiglor.video.entity.MultipartUpload;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.mapper.MultipartUploadMapper;
import org.tiglor.video.service.MultipartUploadService;
import org.tiglor.video.service.StorageService;
import org.tiglor.video.service.VideoInfoService;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadServiceImpl extends ServiceImpl<MultipartUploadMapper, MultipartUpload>
        implements MultipartUploadService {

    /** 状态：0-上传中 1-分片已收齐 2-已合并 */
    private static final int STATUS_UPLOADING = 0;
    private static final int STATUS_CHUNKS_READY = 1;
    private static final int STATUS_MERGED = 2;

    /** 分片对象统一放在目标对象名加此后缀的「目录」下，便于按前缀一次性列举 */
    private static final String PARTS_SUFFIX = ".parts/";

    /** 后缀会拼进对象名，只放行字母数字，杜绝 ../ 之类的路径注入 */
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[A-Za-z0-9]{1,8}$");
    private static final int FILE_NAME_MAX = 200;

    /** 秒传候选扫描条数：够容忍最新几行的对象被清掉，又不至于为一个 hash 拉回整张表 */
    private static final int REUSABLE_SCAN_LIMIT = 20;

    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final VideoInfoService videoInfoService;

    @Override
    public MultipartInitResult init(MultipartInitRequest request, Long userId) {
        requireUserId(userId);
        long fileSize = request.getFileSize();
        int chunkSize = request.getChunkSize();
        int totalChunks = (int) ((fileSize + chunkSize - 1) / chunkSize);
        if (totalChunks > MultipartInitRequest.MAX_CHUNKS) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "分片数 " + totalChunks + " 超过上限 " + MultipartInitRequest.MAX_CHUNKS + "，请增大 chunkSize");
        }
        String fileHash = request.getFileHash().toLowerCase(Locale.ROOT);

        // 秒传：任何一条已合并会话的对象还在，就直接复用它的 objectKey，一片都不用传。
        // 这里刻意跨用户查——秒传的价值就在于别人传过的文件不用再传一遍。
        // 命中的是「存储层 blob」，不是那条会话记录：必须另开一行，
        // 否则同一文件第二次投稿会被 complete 的幂等判断挡回第一个视频。
        // 代价是多条视频共享同一个对象——真要加「删除源片」的入口，必须先做引用计数。
        String reusable = findReusableObjectKey(fileHash, fileSize);
        if (reusable != null) {
            MultipartUpload session = newSession(userId, request, fileHash, totalChunks, reusable, STATUS_MERGED);
            save(session);
            log.info("秒传命中：userId={}, fileHash={}, objectKey={}", userId, fileHash, reusable);
            return initResult(session, true, List.of());
        }

        // 断点续传：接着传本用户同文件那条还没合并的会话
        MultipartUpload pending = findResumableSession(userId, fileHash, chunkSize, fileSize);
        if (pending != null) {
            return initResult(pending, false, listUploadedIndexes(pending.getObjectKey()));
        }

        MultipartUpload session = newSession(userId, request, fileHash, totalChunks, null, STATUS_UPLOADING);
        save(session);
        return initResult(session, false, List.of());
    }

    @Override
    public void uploadChunk(String uploadId, int chunkIndex, MultipartFile file, Long userId) {
        requireUserId(userId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "分片内容为空");
        }
        MultipartUpload session = requireOwnSession(uploadId, userId);
        if (session.getStatus() != null && session.getStatus() == STATUS_MERGED) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "上传已完成，无法继续上传分片");
        }
        int total = session.getTotalChunks();
        if (chunkIndex < 0 || chunkIndex >= total) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "分片下标越界：" + chunkIndex + "，有效范围 0-" + (total - 1));
        }
        long expected = expectedChunkSize(session, chunkIndex);
        if (file.getSize() != expected) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "分片大小不符：下标 " + chunkIndex + " 期望 " + expected + " 字节，实际 " + file.getSize());
        }

        String partName = partObjectName(session.getObjectKey(), chunkIndex);
        // 客户端超时重试很常见，覆盖写本身幂等；但计数只能在首次写入时加，否则进度会虚高
        boolean firstWrite = !storageService.exists(partName);
        try (InputStream in = file.getInputStream()) {
            storageService.upload(partName, in, "application/octet-stream", file.getSize());
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL, "读取分片内容失败：" + e.getMessage());
        }
        if (firstWrite) {
            markChunkReceived(session);
        }
    }

    @Override
    public MultipartProgress progress(String uploadId, Long userId) {
        requireUserId(userId);
        MultipartUpload session = requireOwnSession(uploadId, userId);
        int total = session.getTotalChunks();
        // 秒传会话根本没有分片对象，列举会得到空清单，直接按「全齐」返回
        List<Integer> uploaded = isMerged(session)
                ? IntStream.range(0, total).boxed().toList()
                : listUploadedIndexes(session.getObjectKey());

        MultipartProgress result = new MultipartProgress();
        result.setUploadId(session.getUploadId());
        result.setStatus(session.getStatus() == null ? STATUS_UPLOADING : session.getStatus());
        result.setTotalChunks(total);
        result.setChunkSize(session.getChunkSize());
        result.setUploadedCount(uploaded.size());
        result.setUploadedIndexes(uploaded);
        return result;
    }

    @Override
    public VideoInfo complete(MultipartCompleteRequest request, Long userId) {
        requireUserId(userId);
        MultipartUpload session = requireOwnSession(request.getUploadId(), userId);

        // 合并和 ffprobe 都是分钟级操作，不能包在数据库事务里长时间占着连接
        if (session.getVideoId() != null) {
            VideoInfo existing = videoInfoService.getById(session.getVideoId());
            if (existing != null) {
                return existing;
            }
        }
        if (!isMerged(session)) {
            mergeChunks(session);
        } else if (!storageService.exists(session.getObjectKey())) {
            // 秒传会话没有任何分片对象，合并这一步是跳过的，对象没了就只能让用户重传，
            // 否则这里会登记出一个 storagePath 指向空气的视频
            throw new BizException(ResultCode.NOT_FOUND, "源文件对象已不存在，请重新上传：" + session.getObjectKey());
        }

        VideoInfo draft = new VideoInfo();
        draft.setUserId(userId);
        draft.setTitle(isBlank(request.getTitle()) ? defaultTitle(session.getFileName()) : request.getTitle().trim());
        draft.setDescription(request.getDescription());
        draft.setCategoryId(request.getCategoryId());
        draft.setFileSize(session.getFileSize());
        draft.setFileHash(session.getFileHash());
        draft.setStoragePath(session.getObjectKey());
        VideoInfo video = videoInfoService.registerStoredVideo(draft);

        session.setVideoId(video.getId());
        updateById(session);
        return video;
    }

    // ---------- 会话建立 ----------

    /**
     * 找一个还能复用的已合并对象。
     * <p>
     * 库里的状态可能过期（对象被清理、bucket 换了），必须实测存在性——否则 init 会返回
     * instant=true 指向一个不存在的对象，complete 再把它登记成视频，播放时才发现是空的。
     * 多扫几条而不是只看最新一条，就是为了容忍「最新那行对应的对象刚被清掉」。
     * </p>
     */
    private String findReusableObjectKey(String fileHash, long fileSize) {
        List<MultipartUpload> candidates = lambdaQuery()
                .eq(MultipartUpload::getFileHash, fileHash)
                .eq(MultipartUpload::getFileSize, fileSize)
                .eq(MultipartUpload::getStatus, STATUS_MERGED)
                .orderByDesc(MultipartUpload::getId)
                .last("LIMIT " + REUSABLE_SCAN_LIMIT)
                .list();
        for (MultipartUpload candidate : candidates) {
            String objectKey = candidate.getObjectKey();
            if (objectKey != null && storageService.exists(objectKey)) {
                return objectKey;
            }
        }
        return null;
    }

    /**
     * 找一条能接着传的会话：同一用户、同一文件、还没合并。
     * <p>
     * 分片参数变了就作废重传而不是报错——旧分片的边界和新 chunkSize 对不上，
     * 无论如何都用不了，把用户卡在这里没有任何好处。作废的行连同分片留给清理任务处理。
     * </p>
     */
    private MultipartUpload findResumableSession(Long userId, String fileHash, int chunkSize, long fileSize) {
        MultipartUpload latest = lambdaQuery()
                .eq(MultipartUpload::getUserId, userId)
                .eq(MultipartUpload::getFileHash, fileHash)
                .ne(MultipartUpload::getStatus, STATUS_MERGED)
                .orderByDesc(MultipartUpload::getId)
                .last("LIMIT 1")
                .one();
        if (latest == null) {
            return null;
        }
        if (!Objects.equals(latest.getChunkSize(), chunkSize) || !Objects.equals(latest.getFileSize(), fileSize)) {
            log.warn("历史会话的分片参数已变更，作废并新开一条：uploadId={}, 原 chunkSize={}, 原 fileSize={}",
                    latest.getUploadId(), latest.getChunkSize(), latest.getFileSize());
            return null;
        }
        return latest;
    }

    /**
     * @param reusedObjectKey 秒传命中的已有对象名；为 null 时按新的 uploadId 生成
     */
    private MultipartUpload newSession(Long userId, MultipartInitRequest request, String fileHash,
                                       int totalChunks, String reusedObjectKey, int status) {
        String uploadId = newUploadId();
        MultipartUpload session = new MultipartUpload();
        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setFileName(truncate(request.getFileName()));
        session.setFileHash(fileHash);
        session.setFileSize(request.getFileSize());
        session.setChunkSize(request.getChunkSize());
        session.setTotalChunks(totalChunks);
        session.setCompletedChunks(status == STATUS_MERGED ? totalChunks : 0);
        session.setBucket(storageProperties.getBucket());
        session.setObjectKey(reusedObjectKey != null
                ? reusedObjectKey
                : objectKeyName(uploadId, request.getFileName()));
        session.setStatus(status);
        return session;
    }

    /** 对象名按天分目录、以 uploadId 命名：天然不冲突，也便于按日期做生命周期清理 */
    private static String objectKeyName(String uploadId, String fileName) {
        String extension = safeExtension(fileName);
        return "video/" + LocalDate.now() + "/" + uploadId + (extension.isEmpty() ? "" : "." + extension);
    }

    private MultipartInitResult initResult(MultipartUpload session, boolean instant, List<Integer> uploadedIndexes) {
        MultipartInitResult result = new MultipartInitResult();
        result.setUploadId(session.getUploadId());
        result.setTotalChunks(session.getTotalChunks());
        result.setChunkSize(session.getChunkSize());
        result.setInstant(instant);
        result.setUploadedIndexes(uploadedIndexes);
        return result;
    }

    // ---------- 合并 ----------

    private void mergeChunks(MultipartUpload session) {
        int total = session.getTotalChunks();
        Set<Integer> received = new HashSet<>(listUploadedIndexes(session.getObjectKey()));
        List<Integer> missing = IntStream.range(0, total)
                .filter(index -> !received.contains(index))
                .boxed()
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "分片未收齐（" + received.size() + "/" + total + "），缺失下标：" + describe(missing));
        }

        List<String> parts = listPartObjectNames(session.getObjectKey(),
                IntStream.range(0, total).boxed().toList());
        storageService.compose(session.getObjectKey(), parts);
        storageService.deleteAll(parts);

        session.setStatus(STATUS_MERGED);
        session.setCompletedChunks(total);
        updateById(session);
        log.info("分片合并完成：uploadId={}, objectKey={}, parts={}",
                session.getUploadId(), session.getObjectKey(), parts.size());
    }

    private void markChunkReceived(MultipartUpload session) {
        boolean counted = lambdaUpdate()
                .setSql("completed_chunks = completed_chunks + 1")
                .eq(MultipartUpload::getId, session.getId())
                .eq(MultipartUpload::getStatus, STATUS_UPLOADING)
                .update();
        if (!counted) {
            return;
        }
        // 收齐就推进到「待合并」，前端不用再拉一次进度接口
        lambdaUpdate()
                .set(MultipartUpload::getStatus, STATUS_CHUNKS_READY)
                .eq(MultipartUpload::getId, session.getId())
                .eq(MultipartUpload::getStatus, STATUS_UPLOADING)
                .apply("completed_chunks >= total_chunks")
                .update();
    }

    // ---------- 分片定位与校验 ----------

    private MultipartUpload requireOwnSession(String uploadId, Long userId) {
        MultipartUpload session = lambdaQuery()
                .eq(MultipartUpload::getUploadId, uploadId)
                .one();
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "上传任务不存在：" + uploadId);
        }
        // uploadId 是 UUID 猜不到，但不能只靠这个：日志、代理、浏览器历史都可能把它泄露出去
        if (!userId.equals(session.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作他人的上传任务");
        }
        return session;
    }

    /** 分片对象名：5 位零填充，使字典序等于数值序，列举结果可以直接按顺序合并 */
    private static String partObjectName(String objectKey, int index) {
        return objectKey + PARTS_SUFFIX + String.format("%05d", index);
    }

    private static List<String> listPartObjectNames(String objectKey, List<Integer> indexes) {
        return indexes.stream().map(index -> partObjectName(objectKey, index)).toList();
    }

    private List<Integer> listUploadedIndexes(String objectKey) {
        String prefix = objectKey + PARTS_SUFFIX;
        return storageService.listObjectNames(prefix).stream()
                .map(name -> parseIndex(name, prefix))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private static Integer parseIndex(String objectName, String prefix) {
        try {
            return Integer.valueOf(objectName.substring(prefix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** 最后一片允许不足 chunkSize，其余必须正好等于 chunkSize */
    private static long expectedChunkSize(MultipartUpload session, int chunkIndex) {
        long chunkSize = session.getChunkSize();
        if (chunkIndex == session.getTotalChunks() - 1) {
            return session.getFileSize() - chunkSize * chunkIndex;
        }
        return chunkSize;
    }

    private static boolean isMerged(MultipartUpload session) {
        return session.getStatus() != null && session.getStatus() == STATUS_MERGED;
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }

    private static String newUploadId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String safeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(idx + 1);
        return SAFE_EXTENSION.matcher(extension).matches() ? extension.toLowerCase(Locale.ROOT) : "";
    }

    private static String truncate(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.length() <= FILE_NAME_MAX ? fileName : fileName.substring(0, FILE_NAME_MAX);
    }

    private static String defaultTitle(String fileName) {
        return isBlank(fileName) ? "未命名视频" : fileName;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 缺失清单可能很长，只列前 10 个下标，够前端定位问题即可 */
    private static String describe(List<Integer> missing) {
        String head = missing.stream().limit(10).map(String::valueOf).collect(Collectors.joining(","));
        return missing.size() > 10 ? head + " 等共 " + missing.size() + " 片" : head;
    }
}
