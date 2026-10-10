#include <jni.h>
#include <stdint.h>
#include <string>
#include <algorithm>
#include <vector>

// Native MKV probe/demux foundation. Compiled only when a verified FFmpeg
// Android SDK is provided; no dependency is silently downloaded at build time.
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/error.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
}

namespace {
std::string avError(int code) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {};
    av_strerror(code, buffer, sizeof(buffer));
    return std::string(buffer);
}
void throwIo(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/io/IOException");
    if (type) env->ThrowNew(type, message.c_str());
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegContainerProbe_probeLocalFile(
    JNIEnv* env, jobject /* instance */, jstring pathString) {
    if (!pathString) {
        throwIo(env, "Missing file path");
        return nullptr;
    }
    const char* chars = env->GetStringUTFChars(pathString, nullptr);
    if (!chars) return nullptr;
    std::string path(chars);
    env->ReleaseStringUTFChars(pathString, chars);
    if (path.empty()) {
        throwIo(env, "Empty file path");
        return nullptr;
    }

    AVFormatContext* ctx = nullptr;
    int result = avformat_open_input(&ctx, path.c_str(), nullptr, nullptr);
    if (result < 0) {
        throwIo(env, "FFmpeg open failed: " + avError(result));
        return nullptr;
    }
    // Incomplete MKV files may still expose usable track headers even if
    // scanning later packets fails. Report that limitation rather than
    // throwing away the successfully opened container.
    result = avformat_find_stream_info(ctx, nullptr);
    const bool streamInfoComplete = result >= 0;
    const std::string streamInfoError =
        streamInfoComplete ? "" : avError(result);

    int video = av_find_best_stream(ctx, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    int audio = av_find_best_stream(ctx, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    int64_t duration = ctx->duration == AV_NOPTS_VALUE ? -1 : ctx->duration / 1000;
    std::string report = "video=" + std::to_string(video) +
        ";audio=" + std::to_string(audio) +
        ";durationMs=" + std::to_string(duration) +
        ";format=" + (ctx->iformat && ctx->iformat->name ? ctx->iformat->name : "unknown") +
        ";streamInfoComplete=" + (streamInfoComplete ? "true" : "false") +
        ";streamInfoError=" + streamInfoError;
    avformat_close_input(&ctx);
    return env->NewStringUTF(report.c_str());
}

/**
 * Decode the first available video frame using libavformat + libavcodec.
 * This is an isolated decoder smoke-test, not a video renderer/player.
 * Bound packet reads prevent hangs on damaged or incomplete containers.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegContainerProbe_decodeFirstVideoFrame(
    JNIEnv* env, jobject, jstring pathString) {
    if (!pathString) { throwIo(env, "Missing file path"); return nullptr; }
    const char* chars = env->GetStringUTFChars(pathString, nullptr);
    if (!chars) return nullptr;
    std::string path(chars);
    env->ReleaseStringUTFChars(pathString, chars);
    if (path.empty()) { throwIo(env, "Empty file path"); return nullptr; }

    AVFormatContext* format = nullptr;
    AVCodecContext* decoder = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    std::string outcome;
    int err = avformat_open_input(&format, path.c_str(), nullptr, nullptr);
    if (err < 0) { throwIo(env, "Open failed: " + avError(err)); return nullptr; }

    // Stream-info errors are tolerated if the container still exposes a
    // usable video stream, as can happen with partially downloaded MKVs.
    avformat_find_stream_info(format, nullptr);
    const int stream = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (stream < 0) { outcome = "No usable video stream: " + avError(stream); goto cleanup; }
    {
        const AVCodecParameters* params = format->streams[stream]->codecpar;
        const AVCodec* codec = avcodec_find_decoder(params->codec_id);
        if (!codec) { outcome = "No FFmpeg decoder for video codec"; goto cleanup; }
        decoder = avcodec_alloc_context3(codec);
        if (!decoder) { outcome = "Unable to allocate decoder"; goto cleanup; }
        err = avcodec_parameters_to_context(decoder, params);
        if (err < 0) { outcome = "Decoder parameters: " + avError(err); goto cleanup; }
        err = avcodec_open2(decoder, codec, nullptr);
        if (err < 0) { outcome = "Decoder open: " + avError(err); goto cleanup; }
    }
    packet = av_packet_alloc();
    frame = av_frame_alloc();
    if (!packet || !frame) { outcome = "Unable to allocate frame or packet"; goto cleanup; }

    for (int count = 0; count < 2048; ++count) {
        err = av_read_frame(format, packet);
        if (err < 0) { outcome = "Packet read stopped: " + avError(err); break; }
        if (packet->stream_index == stream) {
            err = avcodec_send_packet(decoder, packet);
            if (err >= 0 || err == AVERROR(EAGAIN)) {
                err = avcodec_receive_frame(decoder, frame);
                if (err == 0) {
                    outcome = "decoded=true;width=" + std::to_string(frame->width) +
                        ";height=" + std::to_string(frame->height) +
                        ";pts=" + std::to_string(frame->pts);
                    av_packet_unref(packet);
                    break;
                }
            }
        }
        av_packet_unref(packet);
    }
    if (outcome.empty()) outcome = "No decoded video frame within packet limit";

cleanup:
    av_frame_free(&frame);
    av_packet_free(&packet);
    avcodec_free_context(&decoder);
    avformat_close_input(&format);
    return env->NewStringUTF(outcome.c_str());
}

/**
 * Decode a bounded sequence of video frames with FFmpeg.
 * Reports decoded frame count and dimensions. No pixels are rendered yet.
 * Bounded packet reads protect diagnostics against malformed inputs.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegContainerProbe_decodeVideoFrames(
    JNIEnv* env, jobject, jstring pathString, jint requestedFrames) {
    if (!pathString || requestedFrames < 1 || requestedFrames > 300) {
        throwIo(env, "Path required and frame limit must be 1..300");
        return nullptr;
    }
    const char* chars = env->GetStringUTFChars(pathString, nullptr);
    if (!chars) return nullptr;
    std::string path(chars);
    env->ReleaseStringUTFChars(pathString, chars);
    if (path.empty()) { throwIo(env, "Empty file path"); return nullptr; }

    AVFormatContext* format = nullptr;
    AVCodecContext* decoder = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    std::string outcome;
    int decoded = 0;
    int width = 0, height = 0;
    int64_t lastPts = AV_NOPTS_VALUE;
    int err = avformat_open_input(&format, path.c_str(), nullptr, nullptr);
    if (err < 0) { throwIo(env, "Open failed: " + avError(err)); return nullptr; }
    avformat_find_stream_info(format, nullptr);
    {
        int stream = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
        if (stream < 0) { outcome = "No usable video stream: " + avError(stream); goto cleanup; }
        const AVCodecParameters* params = format->streams[stream]->codecpar;
        const AVCodec* codec = avcodec_find_decoder(params->codec_id);
        if (!codec) { outcome = "No FFmpeg video decoder"; goto cleanup; }
        decoder = avcodec_alloc_context3(codec);
        if (!decoder) { outcome = "Decoder allocation failed"; goto cleanup; }
        err = avcodec_parameters_to_context(decoder, params);
        if (err < 0) { outcome = "Decoder configuration: " + avError(err); goto cleanup; }
        err = avcodec_open2(decoder, codec, nullptr);
        if (err < 0) { outcome = "Decoder open: " + avError(err); goto cleanup; }
        packet = av_packet_alloc();
        frame = av_frame_alloc();
        if (!packet || !frame) { outcome = "Packet/frame allocation failed"; goto cleanup; }

        // Drain all ready frames per packet; codecs can output several frames
        // from a single packet and may buffer frames internally.
        auto drain = [&]() {
            while (decoded < requestedFrames) {
                int result = avcodec_receive_frame(decoder, frame);
                if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) return;
                if (result < 0) { outcome = "Decode error: " + avError(result); return; }
                ++decoded;
                width = frame->width;
                height = frame->height;
                lastPts = frame->best_effort_timestamp;
                av_frame_unref(frame);
            }
        };
        for (int read = 0; read < 8192 && decoded < requestedFrames && outcome.empty(); ++read) {
            err = av_read_frame(format, packet);
            if (err < 0) {
                if (err == AVERROR_EOF) {
                    avcodec_send_packet(decoder, nullptr);
                    drain();
                } else {
                    outcome = "Packet read error: " + avError(err);
                }
                break;
            }
            if (packet->stream_index == stream) {
                err = avcodec_send_packet(decoder, packet);
                if (err == AVERROR(EAGAIN)) {
                    drain();
                    if (outcome.empty()) err = avcodec_send_packet(decoder, packet);
                }
                if (err < 0 && err != AVERROR(EAGAIN)) {
                    outcome = "Packet decode error: " + avError(err);
                } else if (outcome.empty()) {
                    drain();
                }
            }
            av_packet_unref(packet);
        }
    }
    if (outcome.empty()) outcome = "ok";
    outcome += ";decodedFrames=" + std::to_string(decoded) +
        ";width=" + std::to_string(width) +
        ";height=" + std::to_string(height) +
        ";lastPts=" + std::to_string(lastPts);

cleanup:
    av_frame_free(&frame);
    av_packet_free(&packet);
    avcodec_free_context(&decoder);
    avformat_close_input(&format);
    return env->NewStringUTF(outcome.c_str());
}

// Decode a single frame into a tightly packed RGBA buffer for the Kotlin
// rendering bridge. This is not a continuous playback loop.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegContainerProbe_decodeFirstRgbaFrame(
    JNIEnv* env, jobject, jstring pathString, jintArray dimensions) {
    if (!pathString || !dimensions || env->GetArrayLength(dimensions) < 2) {
        throwIo(env, "Path and two-element dimensions array required");
        return nullptr;
    }
    const char* chars = env->GetStringUTFChars(pathString, nullptr);
    if (!chars) return nullptr;
    std::string path(chars);
    env->ReleaseStringUTFChars(pathString, chars);
    if (path.empty()) { throwIo(env, "Empty path"); return nullptr; }

    AVFormatContext* format = nullptr;
    AVCodecContext* decoder = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    SwsContext* scaler = nullptr;
    jbyteArray pixels = nullptr;
    std::string error;
    int result = avformat_open_input(&format, path.c_str(), nullptr, nullptr);
    if (result < 0) { throwIo(env, "Open failed: " + avError(result)); return nullptr; }
    avformat_find_stream_info(format, nullptr);
    {
        const int stream = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
        if (stream < 0) { error = "No video stream"; goto done; }
        const AVCodec* codec = avcodec_find_decoder(format->streams[stream]->codecpar->codec_id);
        if (!codec) { error = "Video decoder unavailable"; goto done; }
        decoder = avcodec_alloc_context3(codec);
        if (!decoder) { error = "Decoder allocation failed"; goto done; }
        result = avcodec_parameters_to_context(decoder, format->streams[stream]->codecpar);
        if (result < 0) { error = avError(result); goto done; }
        result = avcodec_open2(decoder, codec, nullptr);
        if (result < 0) { error = avError(result); goto done; }
        packet = av_packet_alloc();
        frame = av_frame_alloc();
        if (!packet || !frame) { error = "Frame allocation failed"; goto done; }

        for (int read = 0; read < 2048 && !pixels; ++read) {
            result = av_read_frame(format, packet);
            if (result < 0) {
                if (result == AVERROR_EOF) {
                    avcodec_send_packet(decoder, nullptr);
                    result = avcodec_receive_frame(decoder, frame);
                    if (result != 0) break;
                } else {
                    error = "Packet read failed: " + avError(result);
                    break;
                }
            } else {
                if (packet->stream_index == stream) {
                    result = avcodec_send_packet(decoder, packet);
                    if (result == AVERROR(EAGAIN)) {
                        avcodec_receive_frame(decoder, frame);
                        result = avcodec_send_packet(decoder, packet);
                    }
                    if (result >= 0) result = avcodec_receive_frame(decoder, frame);
                } else {
                    result = AVERROR(EAGAIN);
                }
                av_packet_unref(packet);
                if (result != 0) continue;
            }
            const int width = frame->width;
            const int height = frame->height;
            // Limit allocation to 64 MiB, even for malformed dimensions.
            if (width <= 0 || height <= 0 || width > 4096 || height > 4096 ||
                static_cast<int64_t>(width) * height * 4 > 64 * 1024 * 1024) {
                error = "Decoded frame exceeds safe RGBA limits";
                break;
            }
            scaler = sws_getContext(width, height, static_cast<AVPixelFormat>(frame->format),
                                    width, height, AV_PIX_FMT_RGBA, SWS_BILINEAR,
                                    nullptr, nullptr, nullptr);
            if (!scaler) { error = "RGBA converter unavailable"; break; }
            std::vector<uint8_t> rgba(static_cast<size_t>(width) * height * 4);
            uint8_t* dst[4] = {rgba.data(), nullptr, nullptr, nullptr};
            int lines[4] = {width * 4, 0, 0, 0};
            result = sws_scale(scaler, frame->data, frame->linesize, 0, height, dst, lines);
            if (result != height) { error = "RGBA conversion failed"; break; }
            pixels = env->NewByteArray(static_cast<jsize>(rgba.size()));
            if (!pixels) break;
            env->SetByteArrayRegion(pixels, 0, static_cast<jsize>(rgba.size()),
                                    reinterpret_cast<const jbyte*>(rgba.data()));
            if (env->ExceptionCheck()) { pixels = nullptr; break; }
            jint size[2] = {width, height};
            env->SetIntArrayRegion(dimensions, 0, 2, size);
            break;
        }
    }
    if (!pixels && error.empty()) error = "No decodable frame within packet limit";
done:
    sws_freeContext(scaler);
    av_frame_free(&frame);
    av_packet_free(&packet);
    avcodec_free_context(&decoder);
    avformat_close_input(&format);
    if (!pixels && !env->ExceptionCheck()) throwIo(env, error);
    return pixels;
}

/**
 * Decode a bounded batch into RGBA frames with presentation timestamps.
 * JNI returns Object[] of long[3] metadata and byte[] pixels in alternating
 * slots. This is a bridge, not yet a persistent streaming decoder.
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegContainerProbe_decodeRgbaFrameBatch(
    JNIEnv* env, jobject, jstring pathString, jint requestedFrames) {
    if (!pathString || requestedFrames < 1 || requestedFrames > 8) {
        throwIo(env, "Path required and frame batch must be 1..8");
        return nullptr;
    }
    const char* chars = env->GetStringUTFChars(pathString, nullptr);
    if (!chars) return nullptr;
    std::string path(chars);
    env->ReleaseStringUTFChars(pathString, chars);
    AVFormatContext* format = nullptr;
    AVCodecContext* decoder = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    SwsContext* scaler = nullptr;
    jobjectArray output = nullptr;
    std::string error;
    int result = avformat_open_input(&format, path.c_str(), nullptr, nullptr);
    if (result < 0) { throwIo(env, "Open failed: " + avError(result)); return nullptr; }
    avformat_find_stream_info(format, nullptr);
    {
        int stream = av_find_best_stream(format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
        if (stream < 0) { error = "No video stream"; goto done_batch; }
        const AVCodec* codec = avcodec_find_decoder(format->streams[stream]->codecpar->codec_id);
        if (!codec) { error = "Video decoder unavailable"; goto done_batch; }
        decoder = avcodec_alloc_context3(codec);
        if (!decoder) { error = "Decoder allocation failed"; goto done_batch; }
        result = avcodec_parameters_to_context(decoder, format->streams[stream]->codecpar);
        if (result < 0) { error = avError(result); goto done_batch; }
        result = avcodec_open2(decoder, codec, nullptr);
        if (result < 0) { error = avError(result); goto done_batch; }
        packet = av_packet_alloc();
        frame = av_frame_alloc();
        if (!packet || !frame) { error = "Frame allocation failed"; goto done_batch; }
        jclass objectClass = env->FindClass("java/lang/Object");
        if (!objectClass) goto done_batch;
        output = env->NewObjectArray(requestedFrames * 2, objectClass, nullptr);
        env->DeleteLocalRef(objectClass);
        if (!output) goto done_batch;
        int decoded = 0;
        int64_t totalBytes = 0;
        bool flushing = false;
        // Drain all ready frames after each packet; bounded reads and output.
        for (int reads = 0; reads < 8192 && decoded < requestedFrames && error.empty(); ++reads) {
            if (!flushing) {
                result = av_read_frame(format, packet);
                if (result == AVERROR_EOF) {
                    flushing = true;
                    avcodec_send_packet(decoder, nullptr);
                } else if (result < 0) {
                    error = "Packet read failed: " + avError(result);
                    break;
                } else {
                    if (packet->stream_index == stream) {
                        result = avcodec_send_packet(decoder, packet);
                        if (result == AVERROR(EAGAIN)) {
                            // Drain below; never silently discard packet.
                            error = "Decoder backpressure requires persistent packet queue";
                        } else if (result < 0) {
                            error = "Send packet failed: " + avError(result);
                        }
                    }
                    av_packet_unref(packet);
                }
            }
            while (error.empty() && decoded < requestedFrames) {
                result = avcodec_receive_frame(decoder, frame);
                if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) break;
                if (result < 0) { error = "Receive frame failed: " + avError(result); break; }
                const int w = frame->width, h = frame->height;
                const int64_t bytes = static_cast<int64_t>(w) * h * 4;
                if (w <= 0 || h <= 0 || w > 4096 || h > 4096 ||
                    bytes > 64 * 1024 * 1024 || totalBytes + bytes > 64 * 1024 * 1024) {
                    error = "RGBA batch exceeds 64 MiB safety limit";
                    break;
                }
                scaler = sws_getCachedContext(scaler, w, h,
                    static_cast<AVPixelFormat>(frame->format),
                    w, h, AV_PIX_FMT_RGBA, SWS_BILINEAR, nullptr, nullptr, nullptr);
                if (!scaler) { error = "RGBA scaler unavailable"; break; }
                std::vector<uint8_t> rgba(static_cast<size_t>(bytes));
                uint8_t* dst[4] = {rgba.data(), nullptr, nullptr, nullptr};
                int lines[4] = {w * 4, 0, 0, 0};
                if (sws_scale(scaler, frame->data, frame->linesize, 0, h, dst, lines) != h) {
                    error = "RGBA conversion failed";
                    break;
                }
                int64_t pts = frame->best_effort_timestamp;
                if (pts == AV_NOPTS_VALUE) pts = 0;
                int64_t ptsMs = av_rescale_q(pts, format->streams[stream]->time_base, AVRational{1, 1000});
                jlong metadata[3] = {w, h, std::max<int64_t>(0, ptsMs)};
                jlongArray meta = env->NewLongArray(3);
                jbyteArray data = env->NewByteArray(static_cast<jsize>(bytes));
                if (!meta || !data) {
                    if (meta) env->DeleteLocalRef(meta);
                    if (data) env->DeleteLocalRef(data);
                    break;
                }
                env->SetLongArrayRegion(meta, 0, 3, metadata);
                env->SetByteArrayRegion(data, 0, static_cast<jsize>(bytes),
                    reinterpret_cast<const jbyte*>(rgba.data()));
                if (!env->ExceptionCheck()) {
                    env->SetObjectArrayElement(output, decoded * 2, meta);
                    env->SetObjectArrayElement(output, decoded * 2 + 1, data);
                }
                env->DeleteLocalRef(meta);
                env->DeleteLocalRef(data);
                if (env->ExceptionCheck()) break;
                totalBytes += bytes;
                ++decoded;
                av_frame_unref(frame);
            }
            if (flushing || env->ExceptionCheck()) break;
        }
        if (decoded == 0 && error.empty() && !env->ExceptionCheck()) {
            error = "No decodable video frames";
        }
    }
done_batch:
    sws_freeContext(scaler);
    av_frame_free(&frame);
    av_packet_free(&packet);
    avcodec_free_context(&decoder);
    avformat_close_input(&format);
    if (!error.empty() && !env->ExceptionCheck()) {
        throwIo(env, error);
        return nullptr;
    }
    return output;
}
