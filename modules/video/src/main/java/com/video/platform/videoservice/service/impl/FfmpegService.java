package com.video.platform.videoservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.video.platform.common.BizException;
import com.video.platform.common.ResultCode;
import com.video.platform.videoservice.config.FfmpegProperties;
import com.video.platform.videoservice.config.TranscodeProperties;
import com.video.platform.videoservice.service.MediaInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * FFmpeg 多媒体处理：探测媒体信息、转码为多清晰度自适应 HLS、抽取封面。
 * <p>
 * 通过 ProcessBuilder 调用本机 ffmpeg / ffprobe 命令行（需预先安装并配置路径），
 * 好处是不引入数百 MB 的 JNI 依赖；二进制缺失时给出明确报错，便于定位。
 * </p>
 * <p>
 * 多清晰度 HLS 采用单命令 {@code -filter_complex split+scale} + {@code -var_stream_map} + {@code -master_pl_name}，
 * 一步生成 ABR 阶梯（参考 ffmpeg 生产配方）；支持 none/cuda/qsv 三档硬件加速矩阵（参考 Jellyfin）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegService {

    private final FfmpegProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 探测媒体信息：时长 / 分辨率 / 编码 */
    public MediaInfo probe(Path file) {
        try {
            String json = exec(List.of(
                    properties.getFfprobePath(),
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    file.toString()
            ));

            JsonNode root = objectMapper.readTree(json);
            MediaInfo info = new MediaInfo();

            JsonNode format = root.get("format");
            if (format != null) {
                if (format.has("duration")) {
                    info.setDurationSec((int) Math.round(format.get("duration").asDouble()));
                }
                if (format.has("bit_rate")) {
                    info.setBitrate(format.get("bit_rate").asLong());
                }
            }

            for (JsonNode stream : root.withArray("streams")) {
                String codecType = stream.path("codec_type").asText("");
                if ("video".equals(codecType) && info.getWidth() == null) {
                    info.setWidth(stream.path("width").asInt(0));
                    info.setHeight(stream.path("height").asInt(0));
                    info.setVideoCodec(stream.path("codec_name").asText(null));
                } else if ("audio".equals(codecType) && info.getAudioCodec() == null) {
                    info.setAudioCodec(stream.path("codec_name").asText(null));
                }
            }
            return info;
        } catch (IOException e) {
            log.error("ffprobe 解析失败", e);
            throw new BizException(ResultCode.FAIL, "媒体信息探测失败：" + e.getMessage());
        }
    }

    /**
     * 转码为多清晰度自适应 HLS（master.m3u8 + 各档位 playlist + 切片）。
     *
     * @param input        源片本地路径
     * @param outDir       输出目录（master.m3u8 与其余产物均落于此）
     * @param renditions   清晰度阶梯
     * @param hwaccel      none / cuda / qsv
     * @param segmentType  ts / fmp4(CMAF)
     * @param hlsTime      单分片时长（秒）
     * @param masterName   主播放列表文件名
     * @return master.m3u8 的路径
     */
    public Path transcodeToAdaptiveHls(Path input, Path outDir,
                                       List<TranscodeProperties.Rendition> renditions,
                                       String hwaccel, String segmentType,
                                       int hlsTime, String masterName) {
        prepareOutDir(outDir);

        String scaleFilter = scaleFilterFor(hwaccel);
        String videoCodec = videoCodecFor(hwaccel);
        String preset = presetFor(hwaccel);
        int n = renditions.size();

        List<String> cmd = new ArrayList<>();
        cmd.add(properties.getFfmpegPath());
        cmd.add("-y");
        if ("cuda".equals(hwaccel)) {
            cmd.add("-hwaccel"); cmd.add("cuda");
            cmd.add("-hwaccel_output_format"); cmd.add("cuda");
        } else if ("qsv".equals(hwaccel)) {
            cmd.add("-hwaccel"); cmd.add("qsv");
            cmd.add("-hwaccel_output_format"); cmd.add("qsv");
        }
        cmd.add("-i"); cmd.add(input.toString());

        // filter_complex：split 出 N 路后再各自 scale
        StringBuilder fc = new StringBuilder("[0:v]split=").append(n);
        for (int i = 0; i < n; i++) {
            fc.append("[v").append(i).append("]");
        }
        fc.append(";");
        for (int i = 0; i < n; i++) {
            TranscodeProperties.Rendition r = renditions.get(i);
            fc.append("[v").append(i).append("]").append(scaleFilter)
              .append("=w=").append(r.getWidth()).append(":h=").append(r.getHeight())
              .append("[vout").append(i).append("]");
            if (i < n - 1) {
                fc.append(";");
            }
        }
        cmd.add("-filter_complex"); cmd.add(fc.toString());

        // 每个档位的视频流映射与编码参数（VBV 约束 + Closed-GOP）
        for (int i = 0; i < n; i++) {
            TranscodeProperties.Rendition r = renditions.get(i);
            cmd.add("-map"); cmd.add("[vout" + i + "]");
            cmd.add("-c:v:" + i); cmd.add(videoCodec);
            cmd.add("-b:v:" + i); cmd.add(r.getVideoBitrate() + "k");
            cmd.add("-maxrate:v:" + i); cmd.add(r.getMaxrate() + "k");
            cmd.add("-bufsize:v:" + i); cmd.add(r.getBufsize() + "k");
            cmd.add("-preset"); cmd.add(preset);
            if ("none".equals(hwaccel)) {
                // 软编才需要 x264 的 Closed-GOP 参数；硬件编码器忽略
                cmd.add("-x264opts"); cmd.add("keyint=48:min-keyint=48:no-scenecut");
            }
        }

        // 单路音频，所有档位复用（a:0）
        cmd.add("-map"); cmd.add("0:a:0");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add(renditions.get(0).getAudioBitrate() + "k");
        cmd.add("-ar"); cmd.add("44100");

        // HLS muxer
        cmd.add("-f"); cmd.add("hls");
        cmd.add("-hls_time"); cmd.add(String.valueOf(hlsTime));
        cmd.add("-hls_playlist_type"); cmd.add("vod");
        if ("fmp4".equalsIgnoreCase(segmentType)) {
            cmd.add("-hls_segment_type"); cmd.add("fmp4");
        }
        cmd.add("-master_pl_name"); cmd.add(masterName);
        StringBuilder vsm = new StringBuilder();
        for (int i = 0; i < n; i++) {
            vsm.append("v:").append(i).append(",a:0");
            if (i < n - 1) {
                vsm.append(" ");
            }
        }
        cmd.add("-var_stream_map"); cmd.add(vsm.toString());
        cmd.add(outDir.resolve("playlist_%v.m3u8").toString());

        exec(cmd);
        return outDir.resolve(masterName);
    }

    /** 抽取封面缩略图（取第 1 秒） */
    public Path thumbnail(Path input, String outputFileName) {
        Path outDir;
        try {
            outDir = Path.of(properties.getWorkDir());
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL, "创建封面目录失败：" + e.getMessage());
        }
        Path out = outDir.resolve(outputFileName);
        exec(List.of(
                properties.getFfmpegPath(),
                "-y",
                "-ss", "00:00:01",
                "-i", input.toString(),
                "-vframes", "1",
                "-q:v", "2",
                out.toString()
        ));
        return out;
    }

    /** 执行命令并返回标准输出 */
    private String exec(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(ResultCode.FAIL, "命令执行超时：" + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                log.error("命令执行失败: {} \n{}", String.join(" ", command), output);
                throw new BizException(ResultCode.FAIL,
                        "执行失败（请确认已安装 ffmpeg/ffprobe 且路径配置正确）：" + output);
            }
            return output;
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL,
                    "无法执行 " + command.get(0) + "，请确认已安装并配置路径：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.FAIL, "命令执行被中断");
        }
    }

    private void prepareOutDir(Path outDir) {
        try {
            if (Files.exists(outDir)) {
                try (Stream<Path> walk = Files.walk(outDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // 忽略个别文件删除失败
                            }
                        });
                }
            }
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL, "创建转码输出目录失败：" + e.getMessage());
        }
    }

    private static String scaleFilterFor(String hwaccel) {
        return switch (hwaccel) {
            case "cuda" -> "scale_cuda";
            case "qsv" -> "scale_qsv";
            default -> "scale";
        };
    }

    private static String videoCodecFor(String hwaccel) {
        return switch (hwaccel) {
            case "cuda" -> "h264_nvenc";
            case "qsv" -> "h264_qsv";
            default -> "libx264";
        };
    }

    private static String presetFor(String hwaccel) {
        return switch (hwaccel) {
            case "cuda" -> "p4";
            case "qsv" -> "veryfast";
            default -> "veryfast";
        };
    }
}
