#include <vector>
#include <jni.h>
#include <memory>
#include <algorithm>
#include <cstdint>
#include <string>
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
#include <libavutil/channel_layout.h>
}
namespace {
struct AudioSession {
    AVFormatContext* format = nullptr;
    AVCodecContext* decoder = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    SwrContext* resampler = nullptr;
    int stream = -1;
    bool pending = false;
    bool flushed = false;
    ~AudioSession() {
        swr_free(&resampler);
        av_frame_free(&frame);
        av_packet_free(&packet);
        avcodec_free_context(&decoder);
        avformat_close_input(&format);
    }
};
void fail(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) env->ThrowNew(cls, message);
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeOpen(
    JNIEnv* env, jobject, jstring path, jint requestedStream) {
    if (!path) { fail(env, "Audio path missing"); return 0; }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (!chars) return 0;
    auto session = std::make_unique<AudioSession>();
    int result = avformat_open_input(&session->format, chars, nullptr, nullptr);
    env->ReleaseStringUTFChars(path, chars);
    if (result < 0) { fail(env, "Cannot open audio container"); return 0; }
    avformat_find_stream_info(session->format, nullptr);
    if (requestedStream >= 0) {
        if (static_cast<unsigned int>(requestedStream) >= session->format->nb_streams ||
            session->format->streams[requestedStream]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
            fail(env, "Invalid requested audio stream"); return 0;
        }
        session->stream = requestedStream;
    } else {
        session->stream = av_find_best_stream(session->format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    }
    if (session->stream < 0) { fail(env, "No supported audio stream"); return 0; }
    const AVCodecParameters* params = session->format->streams[session->stream]->codecpar;
    const AVCodec* codec = avcodec_find_decoder(params->codec_id);
    if (!codec) { fail(env, "Unsupported FFmpeg audio codec"); return 0; }
    session->decoder = avcodec_alloc_context3(codec);
    if (!session->decoder || avcodec_parameters_to_context(session->decoder, params) < 0 ||
        avcodec_open2(session->decoder, codec, nullptr) < 0) {
        fail(env, "Cannot initialize audio decoder"); return 0;
    }
    session->packet = av_packet_alloc();
    session->frame = av_frame_alloc();
    if (!session->packet || !session->frame) { fail(env, "Audio buffer allocation failed"); return 0; }
    AVChannelLayout outputLayout = AV_CHANNEL_LAYOUT_STEREO;
    result = swr_alloc_set_opts2(&session->resampler, &outputLayout, AV_SAMPLE_FMT_S16, 48000,
        &session->decoder->ch_layout, session->decoder->sample_fmt,
        session->decoder->sample_rate, 0, nullptr);
    if (result < 0 || !session->resampler || swr_init(session->resampler) < 0) {
        fail(env, "Cannot initialize audio resampler"); return 0;
    }
    return reinterpret_cast<jlong>(session.release());
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeNext(
    JNIEnv* env, jobject, jlong handle) {
    auto* s = reinterpret_cast<AudioSession*>(handle);
    if (!s) { fail(env, "Audio decoder closed"); return nullptr; }
    for (int attempts = 0; attempts < 8192; ++attempts) {
        int result = avcodec_receive_frame(s->decoder, s->frame);
        if (result == 0) {
            const int maxSamples = swr_get_out_samples(s->resampler, s->frame->nb_samples);
            if (maxSamples <= 0 || maxSamples > 480000) {
                av_frame_unref(s->frame); fail(env, "Unsafe audio frame size"); return nullptr;
            }
            std::vector<uint8_t> pcm(static_cast<size_t>(maxSamples) * 4);
            uint8_t* out[] = {pcm.data()};
            const int samples = swr_convert(s->resampler, out, maxSamples,
                const_cast<const uint8_t**>(s->frame->extended_data), s->frame->nb_samples);
            av_frame_unref(s->frame);
            if (samples < 0) { fail(env, "Audio resampling failed"); return nullptr; }
            jbyteArray bytes = env->NewByteArray(samples * 4);
            if (bytes) env->SetByteArrayRegion(bytes, 0, samples * 4,
                reinterpret_cast<const jbyte*>(pcm.data()));
            return bytes;
        }
        if (result == AVERROR_EOF) return nullptr;
        if (result != AVERROR(EAGAIN)) { fail(env, "Audio decode failed"); return nullptr; }
        if (s->pending) {
            result = avcodec_send_packet(s->decoder, s->packet);
            if (result == AVERROR(EAGAIN)) continue;
            av_packet_unref(s->packet);
            s->pending = false;
            if (result < 0) { fail(env, "Audio packet rejected"); return nullptr; }
        } else if (s->flushed) {
            return nullptr;
        } else {
            result = av_read_frame(s->format, s->packet);
            if (result == AVERROR_EOF) {
                s->flushed = true;
                avcodec_send_packet(s->decoder, nullptr);
            } else if (result < 0) {
                fail(env, "Audio packet read failed"); return nullptr;
            } else if (s->packet->stream_index == s->stream) {
                s->pending = true;
            } else {
                av_packet_unref(s->packet);
            }
        }
    }
    fail(env, "Audio packet read limit exceeded");
    return nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeSeek(
    JNIEnv* env, jobject, jlong handle, jlong positionMs) {
    auto* s = reinterpret_cast<AudioSession*>(handle);
    if (!s || positionMs < 0) { fail(env, "Invalid audio seek"); return; }
    int64_t timestamp = av_rescale_q(positionMs, AVRational{1, 1000},
        s->format->streams[s->stream]->time_base);
    if (av_seek_frame(s->format, s->stream, timestamp, AVSEEK_FLAG_BACKWARD) < 0) {
        fail(env, "Audio seek failed"); return;
    }
    avcodec_flush_buffers(s->decoder);
    swr_close(s->resampler);
    if (swr_init(s->resampler) < 0) { fail(env, "Audio resampler reset failed"); return; }
    av_packet_unref(s->packet);
    av_frame_unref(s->frame);
    s->pending = false;
    s->flushed = false;
}
extern "C" JNIEXPORT void JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeClose(
    JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<AudioSession*>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeDurationMs(
    JNIEnv*, jobject, jlong handle) {
    auto* s = reinterpret_cast<AudioSession*>(handle);
    if (!s || s->format->duration == AV_NOPTS_VALUE || s->format->duration <= 0) return 0;
    return av_rescale_q(s->format->duration, AVRational{1, AV_TIME_BASE}, AVRational{1, 1000});
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeSelectedStream(
    JNIEnv*, jobject, jlong handle) {
    auto* s = reinterpret_cast<AudioSession*>(handle);
    return s ? s->stream : -1;
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_sole_cinevault_playback_rescue_video_FfmpegNativeAudioDecoder_nativeTracks(
    JNIEnv* env, jobject, jlong handle) {
    auto* s = reinterpret_cast<AudioSession*>(handle);
    if (!s) { fail(env, "Audio decoder closed"); return nullptr; }
    std::vector<std::string> fields;
    for (unsigned int i = 0; i < s->format->nb_streams; ++i) {
        const AVStream* stream = s->format->streams[i];
        const AVCodecParameters* params = stream->codecpar;
        if (params->codec_type != AVMEDIA_TYPE_AUDIO || !avcodec_find_decoder(params->codec_id)) continue;
        const AVDictionaryEntry* language = av_dict_get(stream->metadata, "language", nullptr, 0);
        fields.push_back(std::to_string(i));
        fields.push_back(language ? language->value : "und");
        fields.push_back(avcodec_get_name(params->codec_id));
        fields.push_back(std::to_string(params->ch_layout.nb_channels));
    }
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(fields.size()), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (!result) return nullptr;
    for (size_t i = 0; i < fields.size(); ++i) {
        jstring value = env->NewStringUTF(fields[i].c_str());
        if (!value) return nullptr;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
        if (env->ExceptionCheck()) return nullptr;
    }
    return result;
}
