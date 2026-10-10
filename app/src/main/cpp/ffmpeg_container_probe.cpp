#include <jni.h>
#include <stdint.h>
#include <string>
#include <algorithm>

// Native MKV probe/demux foundation. Compiled only when a verified FFmpeg
// Android SDK is provided; no dependency is silently downloaded at build time.
extern "C" {
#include <libavformat/avformat.h>
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
