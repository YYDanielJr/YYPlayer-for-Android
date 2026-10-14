// Thin Android transport. Only Engine::run executes ordinary libmpv APIs.
#include <jni.h>
#include <algorithm>
#include <dlfcn.h>
#include <android/log.h>
#include <mpv/client.h>
#include <condition_variable>
#include <deque>
#include <future>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include <stdexcept>
#include <cmath>

namespace {
struct Api {
    void* library = nullptr;
#define API(name) decltype(&mpv_##name) name = nullptr
    API(create); API(initialize); API(terminate_destroy); API(set_option_string);
    API(set_option); API(command); API(get_property); API(free_node_contents);
    API(wait_event); API(error_string); API(request_log_messages);
#undef API
    Api() {
        library = dlopen("libmpv.so", RTLD_NOW | RTLD_LOCAL);
        if (!library) throw std::runtime_error(dlerror());
#define LOAD(name) name = reinterpret_cast<decltype(name)>(dlsym(library, "mpv_" #name)); if (!name) throw std::runtime_error("Missing mpv_" #name)
        LOAD(create); LOAD(initialize); LOAD(terminate_destroy); LOAD(set_option_string);
        LOAD(set_option); LOAD(command); LOAD(get_property); LOAD(free_node_contents);
        LOAD(wait_event); LOAD(error_string); LOAD(request_log_messages);
#undef LOAD
    }
};
std::string quote(const std::string& s) {
    std::string result = "\"";
    const char hex[] = "0123456789abcdef";
    for (unsigned char c : s) {
        if (c == '"' || c == '\\') { result += '\\'; result += c; }
        else if (c < 32) { result += "\\u00"; result += hex[c >> 4]; result += hex[c & 15]; }
        else result += c;
    }
    return result + '"';
}
std::string json(const mpv_node& n, int depth = 0) {
    if (depth > 12) return "null";
    switch (n.format) {
        case MPV_FORMAT_STRING: return quote(n.u.string ? n.u.string : "");
        case MPV_FORMAT_FLAG: return n.u.flag ? "true" : "false";
        case MPV_FORMAT_INT64: return std::to_string(n.u.int64);
        case MPV_FORMAT_DOUBLE: return std::isfinite(n.u.double_) ? std::to_string(n.u.double_) : "null";
        case MPV_FORMAT_NODE_ARRAY: case MPV_FORMAT_NODE_MAP: {
            bool map = n.format == MPV_FORMAT_NODE_MAP;
            std::string out = map ? "{" : "[";
            if (n.u.list) for (int i = 0; i < n.u.list->num && i < 20000; ++i) {
                if (i) out += ',';
                if (map) out += quote(n.u.list->keys[i]) + ':';
                out += json(n.u.list->values[i], depth + 1);
            }
            return out + (map ? '}' : ']');
        }
        default: return "null";
    }
}
struct Request { std::vector<std::string> args; std::promise<std::string> ack; };
struct Engine {
    JavaVM* vm; jobject context; jobject surface;
    std::mutex mutex; std::condition_variable wake;
    std::deque<std::shared_ptr<Request>> requests;
    bool stopping = false;
    std::thread worker;
    std::promise<void> started;
    Engine(JNIEnv* env, jobject ctx, jobject output) {
        env->GetJavaVM(&vm); context = env->NewGlobalRef(ctx);
        surface = output ? env->NewGlobalRef(output) : nullptr;
        worker = std::thread([this] { run(); });
    }
    ~Engine() { stop(); }
    void stop() {
        { std::lock_guard<std::mutex> lock(mutex); stopping = true; }
        wake.notify_one();
        if (worker.joinable()) worker.join(); // JNI close is called only on the Kotlin IO control thread.
    }
    std::string call(std::vector<std::string> args) {
        auto request = std::make_shared<Request>(); request->args = std::move(args);
        auto reply = request->ack.get_future();
        { std::lock_guard<std::mutex> lock(mutex);
          if (stopping) throw std::runtime_error("mpv engine closed");
          if (requests.size() >= 64) throw std::runtime_error("mpv command queue full");
          requests.push_back(request); }
        wake.notify_one();
        return reply.get();
    }
    void run() {
        JNIEnv* env = nullptr;
        vm->AttachCurrentThread(&env, nullptr);
        mpv_handle* mpv = nullptr;
        Api* api = nullptr;
        bool ready = false, loaded = false;
        std::string playbackError;
        try {
            static Api processApi; // Loaded once for the process; all engines share one FFmpeg/libc++.
            api = &processApi;
            auto codec = dlopen("libavcodec.so", RTLD_NOW | RTLD_LOCAL);
            if (!codec) throw std::runtime_error(dlerror());
            auto setVM = reinterpret_cast<int(*)(void*, void*)>(dlsym(codec, "av_jni_set_java_vm"));
            auto setContext = reinterpret_cast<int(*)(void*, void*)>(dlsym(codec, "av_jni_set_android_app_ctx"));
            if (!setVM || !setContext) throw std::runtime_error("FFmpeg JNI API unavailable");
            setVM(vm, nullptr); setContext(context, nullptr);
            dlclose(codec);
            mpv = api->create();
            if (!mpv) throw std::runtime_error("mpv_create failed");
            api->request_log_messages(mpv, "warn");
            auto option = [&](const char* key, const char* value) {
                int code = api->set_option_string(mpv, key, value);
                if (code < 0) throw std::runtime_error(std::string(key) + ": " + api->error_string(code));
            };
            option("config", "no"); option("load-scripts", "no"); option("input-default-bindings", "no");
            option("input-vo-keyboard", "no"); option("input-terminal", "no"); option("terminal", "no");
            option("tls-verify", "yes"); option("idle", "yes"); option("keep-open", "yes");
            option("keepaspect", "no"); option("hwdec", "mediacodec-copy");
            option("ao", "aaudio,audiotrack"); option("pause", "yes"); option("sub-ass", "yes");
            option("aaudio-performance-mode", "low-latency");
            // Keep the extra software queue short for press/release rate changes. The Android AO
            // still owns its driver-sized buffer; this does not replace mpv's clock or decoder.
            option("audio-buffer", "0.03");
            // Keep the pitch-preserving tempo filter alive at 1x as well: inserting/removing a
            // new filter on every press/release otherwise drains the old chain first.
            option("af", "scaletempo2=min-speed=0.1:max-speed=8");
            option("sub-fonts-dir", "/system/fonts");
            option("sub-ass-override", "no");
            option("vo", surface ? "gpu" : "null");
            if (surface) {
                option("gpu-context", "android"); option("opengl-es", "yes");
                int64_t wid = reinterpret_cast<intptr_t>(surface);
                int result = api->set_option(mpv, "wid", MPV_FORMAT_INT64, &wid);
                if (result < 0) throw std::runtime_error(api->error_string(result));
            }
            int result = api->initialize(mpv);
            if (result < 0) throw std::runtime_error(api->error_string(result));
            started.set_value(); ready = true;
            int logBudget = 16;
            for (;;) {
                std::shared_ptr<Request> request;
                { std::unique_lock<std::mutex> lock(mutex);
                  wake.wait_for(lock, std::chrono::milliseconds(20), [&]{ return stopping || !requests.empty(); });
                  if (stopping) break;
                  if (!requests.empty()) { request = requests.front(); requests.pop_front(); } }
                for (;;) {
                    auto event = api->wait_event(mpv, 0);
                    if (event->event_id == MPV_EVENT_NONE) break;
                    if (event->event_id == MPV_EVENT_LOG_MESSAGE && logBudget-- > 0) {
                        auto log = static_cast<mpv_event_log_message*>(event->data);
                        __android_log_print(ANDROID_LOG_DEBUG, "YYMpv", "%s: %s", log->prefix, log->text);
                    }
                    if (event->event_id == MPV_EVENT_START_FILE) { loaded = false; playbackError.clear(); }
                    if (event->event_id == MPV_EVENT_FILE_LOADED) loaded = true;
                    if (event->event_id == MPV_EVENT_END_FILE) {
                        loaded = false;
                        auto end = static_cast<mpv_event_end_file*>(event->data);
                        if (end && end->reason == MPV_END_FILE_REASON_ERROR) playbackError = api->error_string(end->error);
                    }
                }
                if (!request) continue;
                try {
                    if (request->args.size() == 1 && request->args[0] == "snapshot") {
                        std::string out = "{\"loaded\":" + std::string(loaded ? "true" : "false") + ",\"error\":" + quote(playbackError);
                        for (auto key : {"time-pos", "duration", "pause", "eof-reached", "speed", "video-params",
                             "track-list", "chapter-list", "chapter", "seeking", "hwdec-current", "video-codec", "audio-codec-name",
                              "audio-params", "current-ao", "sub-delay", "audio-delay", "sub-text", "sub-font", "sub-ass-override", "deband", "mpv-version", "ffmpeg-version"}) {
                            mpv_node node{};
                            int code = api->get_property(mpv, key, MPV_FORMAT_NODE, &node);
                            out += ',' + quote(key) + ':' + (code >= 0 ? json(node) : "null");
                            if (code >= 0) api->free_node_contents(&node);
                        }
                        request->ack.set_value(out + '}');
                    } else {
                        std::vector<const char*> argv;
                        for (const auto& arg : request->args) argv.push_back(arg.c_str());
                        argv.push_back(nullptr);
                        int code = api->command(mpv, argv.data());
                        if (code < 0) throw std::runtime_error(api->error_string(code));
                        request->ack.set_value("{}");
                    }
                } catch (...) { request->ack.set_exception(std::current_exception()); }
            }
        } catch (...) { if (!ready) started.set_exception(std::current_exception()); }
        { std::lock_guard<std::mutex> lock(mutex); stopping = true;
          for (auto& pending : requests) pending->ack.set_exception(std::make_exception_ptr(std::runtime_error("mpv closed")));
          requests.clear(); }
        // Destroy waits for the VO and decoders on this worker before any Surface/FD lease is released.
        if (mpv && api) api->terminate_destroy(mpv);
        if (surface) env->DeleteGlobalRef(surface);
        env->DeleteGlobalRef(context);
        vm->DetachCurrentThread();
    }
};
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Engine>> registry;
jlong sequence = 0;
void fail(JNIEnv* env, const char* message) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message); }
}
#define JNI_METHOD(name) Java_yy_daniel_yyplayerforandroid_playback_MpvNative_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(create)(JNIEnv* env, jobject, jobject context, jobject surface) {
    try {
        auto engine = std::make_shared<Engine>(env, context, surface);
        engine->started.get_future().get();
        std::lock_guard<std::mutex> lock(registryMutex);
        jlong id = ++sequence; registry[id] = engine; return id;
    } catch (const std::exception& e) { fail(env, e.what()); return 0; }
}
extern "C" JNIEXPORT jbyteArray JNICALL JNI_METHOD(request)(JNIEnv* env, jobject, jlong id, jobjectArray arguments) {
    try {
        std::shared_ptr<Engine> engine;
        { std::lock_guard<std::mutex> lock(registryMutex); auto found = registry.find(id);
          if (found == registry.end()) throw std::runtime_error("stale mpv handle"); engine = found->second; }
        std::vector<std::string> args;
        int count = env->GetArrayLength(arguments);
        if (count < 1 || count > 16) throw std::runtime_error("invalid command size");
        for (int i = 0; i < count; ++i) {
            auto bytes = static_cast<jbyteArray>(env->GetObjectArrayElement(arguments, i));
            int length = env->GetArrayLength(bytes);
            if (length > 16384) throw std::runtime_error("command argument too long");
            std::string value(static_cast<size_t>(length), '\0');
            env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(value.data()));
            args.emplace_back(std::move(value)); env->DeleteLocalRef(bytes);
        }
        const std::vector<std::string> commands = {"snapshot", "set", "seek", "loadfile", "sub-add", "stop", "frame-step", "frame-back-step"};
        if (std::find(commands.begin(), commands.end(), args[0]) == commands.end()) throw std::runtime_error("command not allowed");
        if (args[0] == "set") {
            const std::vector<std::string> properties = {"pause", "hwdec", "volume", "speed", "aid", "sid", "deband", "sub-delay", "audio-delay",
                "sub-font", "sub-ass-override", "sub-font-size", "sub-pos", "ab-loop-a", "ab-loop-b"};
            if (args.size() != 3 || std::find(properties.begin(), properties.end(), args[1]) == properties.end()) throw std::runtime_error("property not allowed");
        }
        auto result = engine->call(std::move(args));
        auto bytes = env->NewByteArray(static_cast<jsize>(result.size()));
        if (bytes) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()), reinterpret_cast<const jbyte*>(result.data()));
        return bytes;
    } catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(close)(JNIEnv* env, jobject, jlong id) {
    try {
        std::shared_ptr<Engine> engine;
        { std::lock_guard<std::mutex> lock(registryMutex); auto found = registry.find(id);
          if (found == registry.end()) return; engine = found->second; registry.erase(found); }
        engine->stop();
    } catch (const std::exception& e) { fail(env, e.what()); }
}
