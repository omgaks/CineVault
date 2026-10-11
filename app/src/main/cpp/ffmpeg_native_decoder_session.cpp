#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}
namespace {
struct Session {
    AVFormatContext* format = nullptr;
    AVCodecContext* decoder = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    SwsContext* scaler = nullptr;
    int stream = -1;
    bool pending = false;
    bool flushed = false;
    ~Session() {
        sws_freeContext(scaler);
        av_frame_free(&frame);
        av_packet_free(&packet);
        avcodec_free_context(&decoder);
        avformat_close_input(&format);
    }
};
std::mutex sessionsMutex;
std::unordered_map<jlong, std::unique_ptr<Session>> sessions;
jlong nextHandle = 1;
void fail(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) env->ThrowNew(cls, message);
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeDecoderSession_nativeOpen(
    JNIEnv* env, jobject, jstring path) {
    if (!path) { fail(env, "Missing video path"); return 0; }
    const char* utf = env->GetStringUTFChars(path, nullptr);
    if (!utf) return 0;
    std::string filename(utf);
    env->ReleaseStringUTFChars(path, utf);
    if (filename.empty()) { fail(env, "Empty video path"); return 0; }
    auto s = std::make_unique<Session>();
    if (avformat_open_input(&s->format, filename.c_str(), nullptr, nullptr) < 0) {
        fail(env, "Unable to open FFmpeg container"); return 0;
    }
    // Stream-info errors can occur on incomplete Matroska files.
    avformat_find_stream_info(s->format, nullptr);
    s->stream = av_find_best_stream(s->format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (s->stream < 0) { fail(env, "No usable video stream"); return 0; }
    const AVCodecParameters* params = s->format->streams[s->stream]->codecpar;
    const AVCodec* codec = avcodec_find_decoder(params->codec_id);
    if (!codec) { fail(env, "Video codec unsupported by FFmpeg"); return 0; }
    s->decoder = avcodec_alloc_context3(codec);
    if (!s->decoder || avcodec_parameters_to_context(s->decoder, params) < 0 ||
        avcodec_open2(s->decoder, codec, nullptr) < 0) {
        fail(env, "Unable to initialize FFmpeg decoder"); return 0;
    }
    s->packet = av_packet_alloc();
    s->frame = av_frame_alloc();
    if (!s->packet || !s->frame) { fail(env, "Unable to allocate decoder buffers"); return 0; }
    std::lock_guard<std::mutex> lock(sessionsMutex);
    const jlong id = nextHandle++;
    sessions.emplace(id, std::move(s));
    return id;
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeDecoderSession_nativeNext(
    JNIEnv* env, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(sessionsMutex);
    auto it = sessions.find(id);
    if (it == sessions.end()) { fail(env, "Decoder session is closed"); return nullptr; }
    Session& s = *it->second;
    for (int attempt = 0; attempt < 8192; ++attempt) {
        int result = avcodec_receive_frame(s.decoder, s.frame);
        if (result == 0) {
            const int w = s.frame->width, h = s.frame->height;
            const int64_t bytes = static_cast<int64_t>(w) * h * 4;
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || bytes > 64LL*1024*1024) {
                av_frame_unref(s.frame); fail(env, "Unsafe decoded frame size"); return nullptr;
            }
            s.scaler = sws_getCachedContext(s.scaler, w, h,
                static_cast<AVPixelFormat>(s.frame->format), w, h, AV_PIX_FMT_RGBA,
                SWS_BILINEAR, nullptr, nullptr, nullptr);
            if (!s.scaler) { av_frame_unref(s.frame); fail(env, "RGBA scaler failed"); return nullptr; }
            std::vector<uint8_t> pixels(static_cast<size_t>(bytes));
            uint8_t* dest[4] = {pixels.data(), nullptr, nullptr, nullptr};
            int strides[4] = {w*4, 0, 0, 0};
            if (sws_scale(s.scaler, s.frame->data, s.frame->linesize, 0, h, dest, strides) != h) {
                av_frame_unref(s.frame); fail(env, "RGBA conversion failed"); return nullptr;
            }
            int64_t pts = s.frame->best_effort_timestamp;
            if (pts == AV_NOPTS_VALUE) pts = 0;
            jlong metadata[3] = {w, h, std::max<int64_t>(0, av_rescale_q(
                pts, s.format->streams[s.stream]->time_base, AVRational{1,1000}))};
            av_frame_unref(s.frame);
            jclass cls = env->FindClass("java/lang/Object");
            if (!cls) return nullptr;
            jobjectArray output = env->NewObjectArray(2, cls, nullptr);
            env->DeleteLocalRef(cls);
            if (!output) return nullptr;
            jlongArray meta = env->NewLongArray(3);
            jbyteArray data = env->NewByteArray(static_cast<jsize>(bytes));
            if (!meta || !data) return nullptr;
            env->SetLongArrayRegion(meta, 0, 3, metadata);
            env->SetByteArrayRegion(data, 0, static_cast<jsize>(bytes),
                reinterpret_cast<const jbyte*>(pixels.data()));
            if (!env->ExceptionCheck()) {
                env->SetObjectArrayElement(output, 0, meta);
                env->SetObjectArrayElement(output, 1, data);
            }
            env->DeleteLocalRef(meta);
            env->DeleteLocalRef(data);
            return env->ExceptionCheck() ? nullptr : output;
        }
        if (result == AVERROR_EOF) return nullptr;
        if (result != AVERROR(EAGAIN)) { fail(env, "FFmpeg frame decode failed"); return nullptr; }
        if (s.pending) {
            result = avcodec_send_packet(s.decoder, s.packet);
            if (result == AVERROR(EAGAIN)) continue;
            av_packet_unref(s.packet);
            s.pending = false;
            if (result < 0) { fail(env, "FFmpeg packet rejected"); return nullptr; }
        } else if (s.flushed) {
            return nullptr;
        } else {
            result = av_read_frame(s.format, s.packet);
            if (result == AVERROR_EOF) {
                s.flushed = true;
                result = avcodec_send_packet(s.decoder, nullptr);
                if (result < 0 && result != AVERROR_EOF) {
                    fail(env, "FFmpeg flush failed"); return nullptr;
                }
            } else if (result < 0) {
                fail(env, "FFmpeg packet read failed"); return nullptr;
            } else if (s.packet->stream_index != s.stream) {
                av_packet_unref(s.packet);
            } else {
                s.pending = true;
            }
        }
    }
    fail(env, "FFmpeg decoder exceeded packet limit");
    return nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeDecoderSession_nativeClose(
    JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(sessionsMutex);
    sessions.erase(id);
}
