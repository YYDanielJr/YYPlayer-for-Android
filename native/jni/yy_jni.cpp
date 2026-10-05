// JNI bridge for the portable core.
//
// Only control data crosses this boundary: JSON requests and responses, PCM through direct byte
// buffers and preallocated rings. No mpv, FFmpeg or Android type appears on either side, and no
// Java object is allocated from a realtime callback.
//
// JNI function names follow the Kotlin declarations exactly. The bridge methods live in the nested
// `NativeAudioCore.Bridge` object, whose binary name is `...NativeAudioCore$Bridge`, so `$` is
// escaped as `_00024` and the two control functions are prefixed `native` to match that object.
//   yy.daniel.yyplayerforandroid.audio.NativeAudioCore       -> control-free ring entry points
//   yy.daniel.yyplayerforandroid.audio.NativeAudioCore$Bridge -> version and policy evaluation
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>

#include "yy/core/format.h"
#include "yy/core/json.h"
#include "yy/core/pcm.h"
#include "yy/core/policy.h"
#include "yy/core/ring.h"

namespace {

using yy::core::ActualSharing;
using yy::core::BitPerfectStatus;
using yy::core::ChannelMask;
using yy::core::Decision;
using yy::core::MixerProfile;
using yy::core::PcmFormat;
using yy::core::ProcessingState;
using yy::core::Reason;
using yy::core::RequestedSharing;
using yy::core::RouteCapability;
using yy::core::SampleEncoding;
using yy::core::SharingRequest;
using yy::core::SinkKind;
using yy::core::TransparencyRequest;
namespace json = yy::core::json;

// A ring handle is a jlong the JVM may hold across calls. The registry keeps the mapping explicit
// so a stale or wrong-type handle is rejected instead of dereferenced.
struct RingEntry {
    yy::core::PcmRing ring;
    RingEntry(const PcmFormat& format, std::uint64_t capacity_frames)
        : ring(format, capacity_frames) {}
};

std::mutex& RegistryMutex() {
    static std::mutex mutex;
    return mutex;
}

std::unordered_map<std::int64_t, RingEntry*>& Registry() {
    static std::unordered_map<std::int64_t, RingEntry*> registry;
    return registry;
}

std::int64_t NextHandle = 1;

RingEntry* LookupRing(jlong handle) {
    if (handle <= 0) return nullptr;
    std::lock_guard<std::mutex> lock(RegistryMutex());
    const auto found = Registry().find(static_cast<std::int64_t>(handle));
    return found == Registry().end() ? nullptr : found->second;
}

// JSON value for a jstring, tolerating a null pointer and an invalid encoding.
std::string ToString(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string text(chars);
    env->ReleaseStringUTFChars(value, chars);
    return text;
}

jstring ToJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

bool ParseEncoding(const std::string& name, SampleEncoding* out) {
    if (name == "s16") {
        *out = SampleEncoding::kS16;
    } else if (name == "s24_3") {
        *out = SampleEncoding::kS24_3;
    } else if (name == "s24_in32") {
        *out = SampleEncoding::kS24In32;
    } else if (name == "s32") {
        *out = SampleEncoding::kS32;
    } else if (name == "f32") {
        *out = SampleEncoding::kF32;
    } else if (name == "u8") {
        *out = SampleEncoding::kU8;
    } else {
        return false;  // unknown strings stay unknown instead of becoming a default
    }
    return true;
}

const char* EncodingToJson(SampleEncoding encoding) {
    return yy::core::EncodingName(encoding);
}

bool ParseSink(const std::string& name, SinkKind* out) {
    if (name == "audiotrack_system") {
        *out = SinkKind::kAudioTrackSystem;
    } else if (name == "oboe") {
        *out = SinkKind::kOboe;
    } else if (name == "usb_direct") {
        *out = SinkKind::kUsbDirect;
    } else {
        *out = SinkKind::kUnknown;
    }
    return true;
}

bool ParseSharing(const std::string& name, SharingRequest* out) {
    if (name == "shared") {
        *out = SharingRequest::kShared;
    } else if (name == "prefer_exclusive") {
        *out = SharingRequest::kPreferExclusive;
    } else if (name == "strict_exclusive") {
        *out = SharingRequest::kStrictExclusive;
    } else {
        return false;
    }
    return true;
}

bool ParseTransparency(const std::string& name, TransparencyRequest* out) {
    if (name == "compatible") {
        *out = TransparencyRequest::kCompatible;
    } else if (name == "prefer_unprocessed") {
        *out = TransparencyRequest::kPreferUnprocessed;
    } else if (name == "strict_unprocessed") {
        *out = TransparencyRequest::kStrictUnprocessed;
    } else {
        return false;
    }
    return true;
}

const char* RequestedSharingName(RequestedSharing sharing) {
    return sharing == RequestedSharing::kExclusive ? "exclusive" : "shared";
}

const char* ActualSharingName(ActualSharing sharing) {
    switch (sharing) {
        case ActualSharing::kShared: return "shared";
        case ActualSharing::kExclusive: return "exclusive";
        case ActualSharing::kUnknown: break;
    }
    return "unknown";
}

const char* BitPerfectName(BitPerfectStatus status) {
    switch (status) {
        case BitPerfectStatus::kUnavailable: return "unavailable";
        case BitPerfectStatus::kRequested: return "requested";
        case BitPerfectStatus::kSystemReported: return "system_reported";
        case BitPerfectStatus::kCaptureVerified: return "capture_verified";
        case BitPerfectStatus::kUnknown: break;
    }
    return "unknown";
}

const char* SinkName(SinkKind sink) {
    switch (sink) {
        case SinkKind::kAudioTrackSystem: return "audiotrack_system";
        case SinkKind::kOboe: return "oboe";
        case SinkKind::kUsbDirect: return "usb_direct";
        case SinkKind::kUnknown: break;
    }
    return "unknown";
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_00024Bridge_nativeBridgeVersion(JNIEnv*,
                                                                                       jclass) {
    return 1;
}

// Evaluates one output request. The request and response are flat JSON documents; see
// docs/TECHNICAL_DESIGN.md for the field semantics and docs/adr/0006 for the decision rules.
JNIEXPORT jstring JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_00024Bridge_nativeEvaluateOutputPolicy(
    JNIEnv* env, jclass, jstring request_json) {
    const std::string request = ToString(env, request_json);
    std::string response;
    response.reserve(512);

    PcmFormat source;
    Reason reject = Reason::kOk;
    std::int64_t value = 0;
    if (json::TryGetInt(request, "format.sampleRate", yy::core::kSampleRateMin,
                        yy::core::kSampleRateMax, &value)) {
        source.sample_rate = static_cast<std::int32_t>(value);
    } else {
        reject = Reason::kRouteNotCapable;
    }
    if (reject == Reason::kOk) {
        if (json::TryGetInt(request, "format.channels", 1, yy::core::kChannelCountMax, &value)) {
            source.channel_count = static_cast<std::int32_t>(value);
        } else {
            reject = Reason::kRouteNotCapable;
        }
    }
    std::string encoding_name;
    if (reject == Reason::kOk) {
        if (json::TryGetString(request, "format.encoding", &encoding_name) &&
            ParseEncoding(encoding_name, &source.encoding)) {
            // parsed
        } else {
            reject = Reason::kRouteNotCapable;
        }
    }
    if (json::TryGetInt(request, "format.channelMask", 1, 0x7FFFFFFF, &value)) {
        source.channel_mask = static_cast<ChannelMask>(value);
    } else if (source.channel_count > 0) {
        source.channel_mask = yy::core::MaskForChannels(source.channel_count);
    }

    SharingRequest sharing = SharingRequest::kShared;
    TransparencyRequest transparency = TransparencyRequest::kCompatible;
    if (reject == Reason::kOk) {
        std::string name;
        if (!json::TryGetString(request, "sharing", &name) || !ParseSharing(name, &sharing)) {
            reject = Reason::kRouteNotCapable;
        } else if (!json::TryGetString(request, "transparency", &name) ||
                   !ParseTransparency(name, &transparency)) {
            reject = Reason::kRouteNotCapable;
        }
    }

    ProcessingState processing;
    bool flag = false;
    if (json::TryGetBool(request, "processing.softwareVolume", &flag)) processing.software_volume = flag;
    if (json::TryGetBool(request, "processing.equalizer", &flag)) processing.equalizer = flag;
    if (json::TryGetBool(request, "processing.replayGain", &flag)) processing.replay_gain = flag;
    if (json::TryGetBool(request, "processing.speedChange", &flag)) processing.speed_change = flag;

    bool route_selected = true;
    if (json::TryGetBool(request, "routeSelected", &flag)) route_selected = flag;

    RouteCapability route;
    std::string sink_name;
    if (json::TryGetString(request, "route.sink", &sink_name)) ParseSink(sink_name, &route.sink);
    if (json::TryGetInt(request, "route.deviceId", -2147483647, 2147483647, &value)) {
        route.device_id = static_cast<std::int32_t>(value);
    }
    if (json::TryGetBool(request, "route.supportsBitPerfect", &flag)) route.supports_bit_perfect = flag;
    if (json::TryGetBool(request, "route.supportsExclusive", &flag)) route.supports_exclusive = flag;
    if (json::TryGetBool(request, "route.canReportActualSharing", &flag)) {
        route.can_report_actual_sharing = flag;
    }

    // Profiles are borrowed for the duration of this call only.
    std::string profiles_body;
    std::int32_t profile_count = 0;
    MixerProfile profiles[32];
    if (json::TryGetObjectArray(request, "route.profiles", &profiles_body, &profile_count)) {
        if (profile_count > 32) profile_count = 32;
        // Each element is an object; locate them by re-scanning the array body.
        std::size_t cursor = 0;
        std::int32_t filled = 0;
        while (filled < profile_count && cursor < profiles_body.size()) {
            const std::size_t open = profiles_body.find('{', cursor);
            if (open == std::string::npos) break;
            std::size_t depth = 0;
            std::size_t close = std::string::npos;
            bool in_string = false;
            bool escaped = false;
            for (std::size_t i = open; i < profiles_body.size(); ++i) {
                const char c = profiles_body[i];
                if (in_string) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        in_string = false;
                    }
                    continue;
                }
                if (c == '"') {
                    in_string = true;
                } else if (c == '{') {
                    ++depth;
                } else if (c == '}') {
                    if (depth > 0) --depth;
                    if (depth == 0) {
                        close = i;
                        break;
                    }
                }
            }
            if (close == std::string::npos) break;
            const std::string element = profiles_body.substr(open + 1, close - open - 1);
            MixerProfile profile;
            bool usable = true;
            std::string profile_encoding;
            if (json::TryGetString(element, "encoding", &profile_encoding) &&
                ParseEncoding(profile_encoding, &profile.encoding)) {
                // parsed
            } else {
                usable = false;
            }
            if (usable && json::TryGetInt(element, "sampleRate", yy::core::kSampleRateMin,
                                          yy::core::kSampleRateMax, &value)) {
                profile.sample_rate = static_cast<std::int32_t>(value);
            } else {
                usable = false;
            }
            if (usable && json::TryGetInt(element, "channelMask", 1, 0x7FFFFFFF, &value)) {
                profile.channel_mask = static_cast<ChannelMask>(value);
            } else {
                usable = false;
            }
            if (json::TryGetBool(element, "bitPerfect", &flag)) profile.bit_perfect = flag;
            if (json::TryGetBool(element, "mixesWithOthers", &flag)) profile.mixes_with_others = flag;
            if (usable) profiles[filled++] = profile;
            cursor = close + 1;
        }
        route.profiles = profiles;
        route.profile_count = filled;
    }

    Decision decision;
    if (reject != Reason::kOk) {
        decision.reason = reject;
    } else {
        decision = yy::core::Evaluate(route, source, sharing, transparency, processing, route_selected);
    }

    json::AppendKey(&response, "accepted");
    json::AppendBool(&response, decision.accepted);
    response.push_back(',');
    json::AppendKey(&response, "reasonCode");
    json::AppendString(&response, yy::core::ReasonCode(decision.reason));
    response.push_back(',');
    json::AppendKey(&response, "reasonText");
    json::AppendString(&response, yy::core::ReasonTextZh(decision.reason));
    response.push_back(',');
    json::AppendKey(&response, "sink");
    json::AppendString(&response, SinkName(decision.plan.sink));
    response.push_back(',');
    json::AppendKey(&response, "sampleRate");
    json::AppendInt(&response, decision.plan.format.sample_rate);
    response.push_back(',');
    json::AppendKey(&response, "channels");
    json::AppendInt(&response, decision.plan.format.channel_count);
    response.push_back(',');
    json::AppendKey(&response, "encoding");
    json::AppendString(&response, EncodingToJson(decision.plan.format.encoding));
    response.push_back(',');
    json::AppendKey(&response, "channelMask");
    json::AppendInt(&response, static_cast<std::int64_t>(decision.plan.format.channel_mask));
    response.push_back(',');
    json::AppendKey(&response, "effectiveBits");
    json::AppendInt(&response, yy::core::EffectiveBits(decision.plan.format.encoding));
    response.push_back(',');
    json::AppendKey(&response, "requestedSharing");
    json::AppendString(&response, RequestedSharingName(decision.plan.requested_sharing));
    response.push_back(',');
    json::AppendKey(&response, "expectedSharing");
    json::AppendString(&response, ActualSharingName(decision.plan.expected_sharing));
    response.push_back(',');
    json::AppendKey(&response, "requestBitPerfect");
    json::AppendBool(&response, decision.plan.request_bit_perfect);
    response.push_back(',');
    json::AppendKey(&response, "pristineExpected");
    json::AppendBool(&response, decision.plan.pristine_expected);
    response.push_back(',');
    json::AppendKey(&response, "sameRouteFallback");
    json::AppendBool(&response, decision.plan.same_route_fallback_used);
    response.push_back(',');
    json::AppendKey(&response, "bitPerfect");
    json::AppendString(&response, BitPerfectName(decision.bit_perfect));
    return ToJString(env, response);
}

// Creates a ring for one PCM format. Returns 0 when the format is not usable exactly as described.
JNIEXPORT jlong JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_createRing(JNIEnv* env, jclass,
                                                                  jint sample_rate, jint channels,
                                                                  jstring encoding_name,
                                                                  jint channel_mask,
                                                                  jlong capacity_frames) {
    const std::string name = ToString(env, encoding_name);
    PcmFormat format;
    format.sample_rate = sample_rate;
    format.channel_count = channels;
    if (!ParseEncoding(name, &format.encoding)) return 0;
    format.channel_mask = channel_mask > 0 ? static_cast<ChannelMask>(channel_mask) : 0;
    if (!yy::core::IsPlausibleFormat(format)) return 0;
    if (capacity_frames <= 0) return 0;
    auto* entry = new (std::nothrow) RingEntry(format, static_cast<std::uint64_t>(capacity_frames));
    if (entry == nullptr) return 0;
    if (entry->ring.capacity_frames() == 0) {
        // The format was rejected while allocating; do not publish a handle that stores nothing.
        delete entry;
        return 0;
    }
    std::lock_guard<std::mutex> lock(RegistryMutex());
    const std::int64_t handle = NextHandle++;
    Registry()[handle] = entry;
    return static_cast<jlong>(handle);
}

JNIEXPORT jint JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_ringWrite(JNIEnv* env, jclass, jlong handle,
                                                                 jobject buffer, jint frames) {
    RingEntry* entry = LookupRing(handle);
    if (entry == nullptr || buffer == nullptr || frames <= 0) return 0;
    auto* bytes = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (bytes == nullptr || capacity <= 0) return 0;
    const std::uint64_t frame_bytes =
        static_cast<std::uint64_t>(yy::core::BytesPerSample(entry->ring.format().encoding)) *
        static_cast<std::uint64_t>(entry->ring.format().channel_count);
    if (frame_bytes == 0) return 0;
    std::uint64_t want = static_cast<std::uint64_t>(frames);
    const std::uint64_t fits = static_cast<std::uint64_t>(capacity) / frame_bytes;
    if (want > fits) want = fits;
    if (want == 0) return 0;
    return static_cast<jint>(entry->ring.Write(bytes, want));
}

JNIEXPORT jint JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_ringRead(JNIEnv* env, jclass, jlong handle,
                                                                jobject buffer, jint max_frames) {
    RingEntry* entry = LookupRing(handle);
    if (entry == nullptr || buffer == nullptr || max_frames <= 0) return 0;
    auto* bytes = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (bytes == nullptr || capacity <= 0) return 0;
    const std::uint64_t frame_bytes =
        static_cast<std::uint64_t>(yy::core::BytesPerSample(entry->ring.format().encoding)) *
        static_cast<std::uint64_t>(entry->ring.format().channel_count);
    if (frame_bytes == 0) return 0;
    std::uint64_t want = static_cast<std::uint64_t>(max_frames);
    const std::uint64_t fits = static_cast<std::uint64_t>(capacity) / frame_bytes;
    if (want > fits) want = fits;
    if (want == 0) return 0;
    return static_cast<jint>(entry->ring.Read(bytes, want));
}

JNIEXPORT jboolean JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_ringClear(JNIEnv*, jclass, jlong handle) {
    RingEntry* entry = LookupRing(handle);
    if (entry == nullptr) return JNI_FALSE;
    entry->ring.Clear();
    return JNI_TRUE;
}

JNIEXPORT jlongArray JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_ringStats(JNIEnv* env, jclass, jlong handle) {
    RingEntry* entry = LookupRing(handle);
    jlong values[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    if (entry != nullptr) {
        const yy::core::RingStats stats = entry->ring.Stats();
        values[0] = static_cast<jlong>(entry->ring.capacity_frames());
        values[1] = static_cast<jlong>(entry->ring.ReadableFrames());
        values[2] = static_cast<jlong>(stats.written_frames);
        values[3] = static_cast<jlong>(stats.read_frames);
        values[4] = static_cast<jlong>(stats.dropped_frames);
        values[5] = static_cast<jlong>(stats.underrun_frames);
        values[6] = static_cast<jlong>(stats.overflow_write_calls);
        values[7] = static_cast<jlong>(stats.underrun_read_calls);
    }
    jlongArray result = env->NewLongArray(8);
    if (result == nullptr) return nullptr;
    env->SetLongArrayRegion(result, 0, 8, values);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_releaseRing(JNIEnv*, jclass, jlong handle) {
    RingEntry* entry = nullptr;
    {
        std::lock_guard<std::mutex> lock(RegistryMutex());
        const auto found = Registry().find(static_cast<std::int64_t>(handle));
        if (found == Registry().end()) return JNI_FALSE;
        entry = found->second;
        Registry().erase(found);
    }
    delete entry;
    return JNI_TRUE;
}

// Converts whole interleaved frames between two encodings, keeping every significant bit.
JNIEXPORT jint JNICALL
Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_convertPcm(JNIEnv* env, jclass,
                                                                  jstring from_name,
                                                                  jstring to_name, jobject src,
                                                                  jint src_bytes, jobject dst,
                                                                  jint dst_bytes, jint channels) {
    if (src == nullptr || dst == nullptr || src_bytes <= 0 || dst_bytes <= 0 || channels <= 0) {
        return -1;
    }
    const std::string from_text = ToString(env, from_name);
    const std::string to_text = ToString(env, to_name);
    SampleEncoding from = SampleEncoding::kUnknown;
    SampleEncoding to = SampleEncoding::kUnknown;
    if (!ParseEncoding(from_text, &from) || !ParseEncoding(to_text, &to)) return -1;
    auto* src_ptr = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(src));
    auto* dst_ptr = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(dst));
    if (src_ptr == nullptr || dst_ptr == nullptr) return -1;
    const jlong src_capacity = env->GetDirectBufferCapacity(src);
    const jlong dst_capacity = env->GetDirectBufferCapacity(dst);
    if (src_capacity < src_bytes || dst_capacity < dst_bytes) return -1;
    std::uint64_t converted = 0;
    const yy::core::ConvertResult result =
        yy::core::Convert(from, to, src_ptr, static_cast<std::uint64_t>(src_bytes), dst_ptr,
                          static_cast<std::uint64_t>(dst_bytes), channels, &converted);
    if (result != yy::core::ConvertResult::kOk) return -1;
    return static_cast<jint>(converted);
}

}  // extern "C"
