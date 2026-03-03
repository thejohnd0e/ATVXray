#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>
#include <tun2socks/tun2socks.h>

namespace {
int pipe_stdout[2];
int pipe_stderr[2];
pthread_t thread_stdout;
pthread_t thread_stderr;
const char *TAG = "ATVxrayTun2Socks";

void *stderr_thread(void *) {
    ssize_t n;
    char buf[2048];
    while ((n = read(pipe_stderr[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = 0;
        __android_log_write(ANDROID_LOG_ERROR, TAG, buf);
    }
    return nullptr;
}

void *stdout_thread(void *) {
    ssize_t n;
    char buf[2048];
    while ((n = read(pipe_stdout[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = 0;
        __android_log_write(ANDROID_LOG_INFO, TAG, buf);
    }
    return nullptr;
}

void redirect_stdio() {
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    pipe(pipe_stdout);
    pipe(pipe_stderr);
    dup2(pipe_stdout[1], STDOUT_FILENO);
    dup2(pipe_stderr[1], STDERR_FILENO);
    pthread_create(&thread_stdout, nullptr, stdout_thread, nullptr);
    pthread_detach(thread_stdout);
    pthread_create(&thread_stderr, nullptr, stderr_thread, nullptr);
    pthread_detach(thread_stderr);
}
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_atvxray_client_NativeTun2Socks_startTun2Socks(JNIEnv *env, jobject /*thiz*/, jobjectArray args) {
    const jsize argc = env->GetArrayLength(args);
    int bytes = 0;
    for (int i = 0; i < argc; ++i) {
        auto js = (jstring) env->GetObjectArrayElement(args, i);
        const char *s = env->GetStringUTFChars(js, nullptr);
        bytes += static_cast<int>(strlen(s)) + 1;
        env->ReleaseStringUTFChars(js, s);
        env->DeleteLocalRef(js);
    }

    auto *buffer = static_cast<char *>(calloc(bytes, sizeof(char)));
    auto **argv = static_cast<char **>(calloc(argc, sizeof(char *)));
    char *cursor = buffer;

    for (int i = 0; i < argc; ++i) {
        auto js = (jstring) env->GetObjectArrayElement(args, i);
        const char *s = env->GetStringUTFChars(js, nullptr);
        const size_t len = strlen(s);
        memcpy(cursor, s, len);
        argv[i] = cursor;
        cursor += len + 1;
        env->ReleaseStringUTFChars(js, s);
        env->DeleteLocalRef(js);
    }

    redirect_stdio();
    const int ret = tun2socks_start(argc, argv);
    free(argv);
    free(buffer);
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_atvxray_client_NativeTun2Socks_stopTun2Socks(JNIEnv *, jobject) {
    tun2socks_terminate();
}
