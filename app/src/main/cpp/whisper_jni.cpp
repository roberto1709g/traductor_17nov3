#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static struct whisper_context *g_ctx = nullptr;
static std::string g_loaded_model_path = "";

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_orvex_traductor_MainActivity_transcribeAudioNative(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path,
        jfloatArray audio_data) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    std::string current_path(path);
    env->ReleaseStringUTFChars(model_path, path);

    if (g_ctx == nullptr || g_loaded_model_path != current_path) {
        if (g_ctx != nullptr) {
            whisper_free(g_ctx);
            g_ctx = nullptr;
        }
        LOGD("Cargando modelo Whisper desde: %s", current_path.c_str());
        struct whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;
        g_ctx = whisper_init_from_file_with_params(current_path.c_str(), cparams);
        if (g_ctx == nullptr) {
            LOGE("Error al cargar modelo Whisper.");
            return env->NewStringUTF("Error: No se pudo cargar el modelo Whisper.");
        }
        g_loaded_model_path = current_path;
    }

    jsize len = env->GetArrayLength(audio_data);
    if (len == 0) {
        return env->NewStringUTF("");
    }

    jfloat *body = env->GetFloatArrayElements(audio_data, nullptr);
    std::vector<float> pcmf32(body, body + len);
    env->ReleaseFloatArrayElements(audio_data, body, JNI_ABORT);

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.print_special    = false;
    wparams.translate        = false;
    wparams.language         = "en";
    wparams.n_threads        = 4;
    wparams.no_context       = true;
    wparams.single_segment   = false;
    wparams.audio_ctx        = 512;

    if (whisper_full(g_ctx, wparams, pcmf32.data(), pcmf32.size()) != 0) {
        LOGE("Fallo al procesar audio con whisper_full");
        return env->NewStringUTF("");
    }

    std::string result_text = "";
    const int n_segments = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(g_ctx, i);
        if (text != nullptr) {
            result_text += text;
        }
    }

    return env->NewStringUTF(result_text.c_str());
}

JNIEXPORT void JNICALL
Java_com_orvex_traductor_MainActivity_freeWhisperNative(
        JNIEnv *env,
        jobject /* this */) {
    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        g_loaded_model_path = "";
        LOGD("Memoria de Whisper liberada.");
    }
}

}
