#include "yy/core/policy.h"

#include <cstdint>

namespace yy::core {
namespace {

// Number of set bits in a channel mask; 0 when the mask is empty or not contiguous.
std::int32_t CountChannels(ChannelMask mask) noexcept {
    std::int32_t count = 0;
    ChannelMask remaining = mask;
    while (remaining != 0) {
        if ((remaining & 1u) != 0) ++count;
        remaining >>= 1u;
    }
    return count;
}

// True when the mask is a contiguous run of bits from bit 0, which is the only layout this core
// accepts: a sparse mask means the caller is guessing the layout.
bool IsContiguous(ChannelMask mask) noexcept {
    if (mask == 0) return false;
    return (mask & (mask + 1u)) == 0;
}

struct Match {
    const MixerProfile* profile = nullptr;
    bool exact = false;
};

// Preference order, strongest first:
//   1. exactly the source rate, channels and encoding, and it is a BIT_PERFECT contract
//   2. exactly the source rate, channels and encoding
//   3. source rate and channels, any encoding
//   4. source rate only
//   5. anything the route reports (last resort, relaxed policies only)
Match BestProfile(const RouteCapability& route, const PcmFormat& source) noexcept {
    Match best;
    int best_rank = 0;  // 0 = nothing yet
    for (std::int32_t i = 0; i < route.profile_count; ++i) {
        const MixerProfile& profile = route.profiles[i];
        if (profile.sample_rate <= 0 || BytesPerSample(profile.encoding) == kBytesPerSampleUnknown) continue;
        if (!IsContiguous(profile.channel_mask)) continue;
        const std::int32_t channels = CountChannels(profile.channel_mask);
        const bool same_rate = profile.sample_rate == source.sample_rate;
        const bool same_channels = channels == source.channel_count;
        const bool same_encoding = profile.encoding == source.encoding;
        const bool exact = same_rate && same_channels && same_encoding;
        int rank = 1;
        if (same_rate) rank = 4;
        if (same_rate && same_channels) rank = 3;
        if (exact) rank = 2;
        if (exact && profile.bit_perfect) rank = 5;
        if (rank > best_rank) {
            best_rank = rank;
            best.profile = &profile;
            best.exact = exact && profile.bit_perfect;
        }
    }
    return best;
}

}  // namespace

const char* ReasonCode(Reason reason) noexcept {
    switch (reason) {
        case Reason::kOk: return "ok";
        case Reason::kStrictExclusiveUnavailable: return "strict_exclusive_unavailable";
        case Reason::kStrictUnprocessedNeedsBitPerfect: return "strict_unprocessed_needs_bit_perfect";
        case Reason::kStrictUnprocessedDspActive: return "strict_unprocessed_dsp_active";
        case Reason::kStrictUnprocessedWouldResample: return "strict_unprocessed_would_resample";
        case Reason::kStrictUnprocessedWouldConvert: return "strict_unprocessed_would_convert";
        case Reason::kExclusiveNotProvenByAudioTrack: return "exclusive_not_proven_by_audiotrack";
        case Reason::kNoRouteSelected: return "no_route_selected";
        case Reason::kRouteNotCapable: return "route_not_capable";
        case Reason::kSampleRateNotOffered: return "sample_rate_not_offered";
        case Reason::kChannelCountNotOffered: return "channel_count_not_offered";
        case Reason::kStrictUnprocessedProfileUnknown: return "strict_unprocessed_profile_unknown";
    }
    return "unknown";
}

const char* ReasonTextZh(Reason reason) noexcept {
    switch (reason) {
        case Reason::kOk: return "可以按请求输出";
        case Reason::kStrictExclusiveUnavailable: return "该输出设备无法提供独占流，已停止而不是静默回退";
        case Reason::kStrictUnprocessedNeedsBitPerfect: return "该设备组合没有系统位完美（BIT_PERFECT）契约，无法声明原样输出";
        case Reason::kStrictUnprocessedDspActive: return "当前启用了软件音量、均衡器、ReplayGain 或变速，会改变样本";
        case Reason::kStrictUnprocessedWouldResample: return "设备只提供其它采样率，严格原样输出需要重采样";
        case Reason::kStrictUnprocessedWouldConvert: return "设备只提供其它位深或编码，严格原样输出需要转换";
        case Reason::kExclusiveNotProvenByAudioTrack: return "系统 AudioTrack 路径无法证明流独占，不能据此显示独占";
        case Reason::kNoRouteSelected: return "尚未选择输出设备";
        case Reason::kRouteNotCapable: return "所选输出设备不可用于当前格式";
        case Reason::kSampleRateNotOffered: return "设备未提供该采样率";
        case Reason::kChannelCountNotOffered: return "设备未提供该声道数";
        case Reason::kStrictUnprocessedProfileUnknown: return "该输出设备没有报告可用的混音格式，无法声明原样输出";
    }
    return "未知情况";
}

PcmFormat MixerProfile::ToFormat(std::int32_t channels) const noexcept {
    PcmFormat format;
    format.sample_rate = sample_rate;
    format.channel_count = channels;
    format.encoding = encoding;
    format.channel_mask = channel_mask;
    return format;
}

Decision Evaluate(const RouteCapability& route,
                  const PcmFormat& source,
                  SharingRequest sharing,
                  TransparencyRequest transparency,
                  const ProcessingState& processing,
                  bool route_selected) noexcept {    Decision decision;

    if (!route_selected) {
        decision.reason = Reason::kNoRouteSelected;
        return decision;
    }
    if (!IsPlausibleFormat(source)) {
        decision.reason = Reason::kRouteNotCapable;
        return decision;
    }

    // --- condition 1: stream sharing -------------------------------------------------------
    ActualSharing expected_sharing = ActualSharing::kUnknown;
    if (sharing == SharingRequest::kStrictExclusive) {
        if (route.sink == SinkKind::kAudioTrackSystem) {
            // The platform never exposes stream exclusivity for AudioTrack. A BIT_PERFECT mixer
            // contract is a different promise and must not be used to satisfy this request.
            decision.reason = Reason::kExclusiveNotProvenByAudioTrack;
            return decision;
        }
        if (!route.supports_exclusive || !route.can_report_actual_sharing) {
            decision.reason = Reason::kStrictExclusiveUnavailable;
            return decision;
        }
        expected_sharing = ActualSharing::kExclusive;
    } else if (sharing == SharingRequest::kPreferExclusive) {
        if (route.supports_exclusive && route.can_report_actual_sharing) {
            expected_sharing = ActualSharing::kExclusive;
        } else {
            expected_sharing = ActualSharing::kShared;
        }
    } else {
        expected_sharing = ActualSharing::kShared;
    }

    // --- condition 2: sample transparency --------------------------------------------------
    if (transparency == TransparencyRequest::kStrictUnprocessed && processing.Any()) {
        decision.reason = Reason::kStrictUnprocessedDspActive;
        return decision;
    }

    const Match match = BestProfile(route, source);
    if (match.profile == nullptr) {
        // A route without an explicit profile list can still be opened at the source format; that
        // is a guess, so strict transparency refuses it and relaxed policies record the fallback.
        if (transparency == TransparencyRequest::kStrictUnprocessed) {
            decision.reason = Reason::kStrictUnprocessedProfileUnknown;
            return decision;
        }
        decision.plan.sink = route.sink;
        decision.plan.format = source;
        decision.plan.requested_sharing = sharing == SharingRequest::kStrictExclusive
                                              ? RequestedSharing::kExclusive
                                              : (sharing == SharingRequest::kPreferExclusive
                                                     ? RequestedSharing::kExclusive
                                                     : RequestedSharing::kShared);
        decision.plan.expected_sharing = expected_sharing;
        decision.plan.pristine_expected = false;
        decision.plan.same_route_fallback_used = true;
        decision.bit_perfect = BitPerfectStatus::kUnavailable;
        decision.accepted = true;
        decision.reason = Reason::kOk;
        return decision;
    }

    const MixerProfile& profile = *match.profile;
    const std::int32_t channels = CountChannels(profile.channel_mask);
    const bool same_rate = profile.sample_rate == source.sample_rate;
    const bool same_channels = channels == source.channel_count;
    const bool same_encoding = profile.encoding == source.encoding;
    const bool exact_format = same_rate && same_channels && same_encoding;

    if (transparency == TransparencyRequest::kStrictUnprocessed) {
        // The system path can only promise pristine samples when the route reports a BIT_PERFECT
        // contract for exactly the format we are about to open.
        if (route.sink == SinkKind::kAudioTrackSystem &&
            (route.supports_bit_perfect || profile.bit_perfect) && !profile.bit_perfect) {
            decision.reason = Reason::kStrictUnprocessedNeedsBitPerfect;
            return decision;
        }
        if (!same_rate) {
            decision.reason = Reason::kStrictUnprocessedWouldResample;
            return decision;
        }
        if (!same_channels) {
            decision.reason = Reason::kChannelCountNotOffered;
            return decision;
        }
        if (!same_encoding) {
            decision.reason = Reason::kStrictUnprocessedWouldConvert;
            return decision;
        }
        if (route.sink == SinkKind::kAudioTrackSystem && !profile.bit_perfect) {
            // Nothing on this route promises pristine samples, so the claim is refused even though
            // the format happens to match.
            decision.reason = Reason::kStrictUnprocessedNeedsBitPerfect;
            return decision;
        }
    }

    decision.accepted = true;
    decision.plan.sink = route.sink;
    decision.plan.format = profile.ToFormat(channels);
    decision.plan.requested_sharing = (sharing == SharingRequest::kShared) ? RequestedSharing::kShared
                                                                          : RequestedSharing::kExclusive;
    decision.plan.expected_sharing = expected_sharing;
    // Asking the mixer for BIT_PERFECT is only meaningful when the samples reaching it are still
    // the decoded ones. An exact format match plus an unprocessed software path is the whole
    // precondition; software volume/EQ/ReplayGain/speed already changed the samples.
    decision.plan.request_bit_perfect = profile.bit_perfect && exact_format &&
                                        transparency != TransparencyRequest::kCompatible &&
                                        !processing.Any();
    decision.plan.pristine_expected = exact_format && transparency != TransparencyRequest::kCompatible;
    decision.plan.same_route_fallback_used = !exact_format;

    if (decision.plan.request_bit_perfect) {
        // Requested only. The adapter promotes this to kSystemReported after the opened output
        // reports the matching route and format; this core never claims more than it can see.
        decision.bit_perfect = BitPerfectStatus::kRequested;
    } else if (route.supports_bit_perfect) {
        decision.bit_perfect = BitPerfectStatus::kUnavailable;
    } else {
        decision.bit_perfect = BitPerfectStatus::kUnavailable;
    }
    decision.reason = Reason::kOk;
    return decision;
}

}  // namespace yy::core
