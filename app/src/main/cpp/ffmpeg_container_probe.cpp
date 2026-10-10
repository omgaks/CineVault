#include <jni.h>
#include <stdint.h>
#include <string>
#include <algorithm>

// Native MKV probe/demux foundation. Compiled only when a verified FFmpeg
// Android SDK is provided; no dependency is silently downloaded at build time.
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/error.h>
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
