// Output policy: turns "what the user asked for" plus "what the route reports" into one explicit
// decision, and never upgrades a status it cannot prove.
//
// The two conditions are independent and combined with AND:
//   sharing      Shared / PreferExclusive / StrictExclusive
//   transparency Compatible / PreferUnprocessed / StrictUnprocessed
// A system USB AudioTrack session with BIT_PERFECT mixer attributes is reported as
// system-reported bit-perfect; it must never be reported as an exclusive stream, because the
// platform gives no evidence of that.
#ifndef YY_CORE_POLICY_H
#define YY_CORE_POLICY_H

#include <cstdint>

#include "yy/core/format.h"

namespace yy::core {

enum class SharingRequest : std::uint8_t {
    kShared = 0,
    kPreferExclusive = 1,
    kStrictExclusive = 2,
};

enum class TransparencyRequest : std::uint8_t {
    kCompatible = 0,
    kPreferUnprocessed = 1,
    kStrictUnprocessed = 2,
};

enum class SinkKind : std::uint8_t {
    kUnknown = 0,
    kAudioTrackSystem = 1,  // platform AudioTrack, shared mixer unless mixer attributes apply
    kOboe = 2,              // AAudio/Oboe stream; may be exclusive when the stream reports it
    kUsbDirect = 3,         // reserved for the later UAC/isochronous path
};

enum class RequestedSharing : std::uint8_t { kShared = 0, kExclusive = 1 };
enum class ActualSharing : std::uint8_t { kUnknown = 0, kShared = 1, kExclusive = 2 };

enum class BitPerfectStatus : std::uint8_t {
    kUnknown = 0,
    kUnavailable = 1,      // no contract on this path, or the route cannot report one
    kRequested = 2,        // asked for, not yet confirmed by the opened output
    kSystemReported = 3,   // system/mixer contract accepted and the actual route/format matched
    kCaptureVerified = 4,  // only after digital capture evidence (never set by this core)
};

enum class Reason : std::uint8_t {
    kOk = 0,
    kStrictExclusiveUnavailable = 1,      // route cannot provide an exclusive stream
    kStrictUnprocessedNeedsBitPerfect = 2,// no matching BIT_PERFECT contract on this route
    kStrictUnprocessedDspActive = 3,      // software volume/EQ/ReplayGain/speed would alter samples
    kStrictUnprocessedWouldResample = 4,  // only a different sample rate is offered
    kStrictUnprocessedWouldConvert = 5,   // only a different encoding/bit depth is offered
    kExclusiveNotProvenByAudioTrack = 6,  // system AudioTrack cannot prove stream exclusivity
    kNoRouteSelected = 7,
    kRouteNotCapable = 8,
    kSampleRateNotOffered = 9,
    kChannelCountNotOffered = 10,
    kStrictUnprocessedProfileUnknown = 11,// route reports no usable format, so nothing can be promised
};

const char* ReasonCode(Reason reason) noexcept;   // stable ASCII code for logs and snapshots
const char* ReasonTextZh(Reason reason) noexcept; // short user-facing explanation

// Software processing that is currently applied on the PCM path. Any of these makes a strict
// "unprocessed" claim false; the core refuses instead of silently disabling the user's settings.
struct ProcessingState {
    bool software_volume = false;
    bool equalizer = false;
    bool replay_gain = false;
    bool speed_change = false;

    constexpr bool Any() const noexcept {
        return software_volume || equalizer || replay_gain || speed_change;
    }
};

// One output format a route reports as usable. `bit_perfect` is the system/mixer contract for that
// exact combination; `mixes_with_others` reflects AudioMixerAttributes.isMixerAttributesSupported
// semantics on the system path.
struct MixerProfile {
    SampleEncoding encoding = SampleEncoding::kUnknown;
    std::int32_t sample_rate = 0;
    ChannelMask channel_mask = 0;
    bool bit_perfect = false;
    bool mixes_with_others = true;

    PcmFormat ToFormat(std::int32_t channels) const noexcept;
};

struct RouteCapability {
    std::int32_t device_id = 0;        // session-scoped identifier
    SinkKind sink = SinkKind::kUnknown;
    bool supports_bit_perfect = false; // route has at least one BIT_PERFECT profile
    bool supports_exclusive = false;   // Oboe: an exclusive stream is plausible on this route
    bool can_report_actual_sharing = false;
    std::int32_t preferred_sample_rate = 0;

    const MixerProfile* Profiles() const noexcept { return profiles; }
    std::int32_t profile_count = 0;
    const MixerProfile* profiles = nullptr;  // borrowed, owned by the caller
};

struct OutputPlan {
    SinkKind sink = SinkKind::kUnknown;
    PcmFormat format;                       // format the sink must be opened with
    RequestedSharing requested_sharing = RequestedSharing::kShared;
    ActualSharing expected_sharing = ActualSharing::kUnknown;
    // True only when the mixer/BIT_PERFECT contract is worth requesting: the route reports an exact
    // match for the source format and nothing on the software path alters the samples. Software
    // volume, EQ, ReplayGain or a speed change makes the request pointless even though the route
    // still has a BIT_PERFECT profile, so the core refuses to ask for it rather than displaying a
    // contract the samples no longer satisfy.
    bool request_bit_perfect = false;
    bool pristine_expected = false;         // no format conversion on the accepted path
    bool same_route_fallback_used = false;  // relaxed policy stayed on the requested route
};

struct Decision {
    bool accepted = false;
    Reason reason = Reason::kOk;
    OutputPlan plan;
    BitPerfectStatus bit_perfect = BitPerfectStatus::kUnknown;
};

// Pure decision function: no platform calls, no allocation, fully host-testable.
//
// Strict policies never fall back: if the route cannot satisfy them the decision is rejected and
// carries the reason. Relaxed policies may choose a different profile on the SAME route and mark
// `same_route_fallback_used`.
Decision Evaluate(const RouteCapability& route,
                  const PcmFormat& source,
                  SharingRequest sharing,
                  TransparencyRequest transparency,
                  const ProcessingState& processing,
                  bool route_selected = true) noexcept;

}  // namespace yy::core

#endif  // YY_CORE_POLICY_H
