#include <cstdint>
#include <string>

#include "test_support.h"
#include "yy/core/policy.h"

using namespace yy::core;

namespace {

PcmFormat SourceFormat(std::int32_t rate, std::int32_t channels, SampleEncoding encoding) {
    PcmFormat format;
    format.sample_rate = rate;
    format.channel_count = channels;
    format.encoding = encoding;
    format.channel_mask = MaskForChannels(channels);
    return format;
}

MixerProfile Profile(std::int32_t rate, std::int32_t channels, SampleEncoding encoding, bool bit_perfect) {
    MixerProfile profile;
    profile.sample_rate = rate;
    profile.channel_mask = MaskForChannels(channels);
    profile.encoding = encoding;
    profile.bit_perfect = bit_perfect;
    profile.mixes_with_others = !bit_perfect;
    return profile;
}

// A USB route that reports one BIT_PERFECT combination, as an API 34+ system path would.
RouteCapability UsbBitPerfectRoute(const MixerProfile* profiles, std::int32_t count) {
    RouteCapability route;
    route.device_id = 42;
    route.sink = SinkKind::kAudioTrackSystem;
    route.supports_bit_perfect = true;
    route.supports_exclusive = false;
    route.can_report_actual_sharing = false;
    route.profiles = profiles;
    route.profile_count = count;
    return route;
}

RouteCapability OboeRoute(bool exclusive_possible) {
    RouteCapability route;
    route.device_id = 7;
    route.sink = SinkKind::kOboe;
    route.supports_bit_perfect = false;
    route.supports_exclusive = exclusive_possible;
    route.can_report_actual_sharing = true;
    return route;
}

ProcessingState NoProcessing() { return ProcessingState{}; }

}  // namespace

void yy::test::RunPolicyTests(Context& context) {
    context.Begin("policy");

    const PcmFormat source48 = SourceFormat(48000, 2, SampleEncoding::kS24In32);

    YY_CASE(context, "no route selected is a refusal, not a fallback");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing(),
                                           /*route_selected=*/false);
        YY_EXPECT(context, !decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason),
                     static_cast<int>(Reason::kNoRouteSelected));
    }

    YY_CASE(context, "usb bit-perfect + shared + strict unprocessed stays unproven exclusive");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason), static_cast<int>(Reason::kOk));
        YY_EXPECT_EQ(context, static_cast<int>(decision.plan.requested_sharing),
                     static_cast<int>(RequestedSharing::kShared));
        YY_EXPECT_EQ(context, static_cast<int>(decision.plan.expected_sharing),
                     static_cast<int>(ActualSharing::kShared));
        YY_EXPECT(context, decision.plan.request_bit_perfect);
        YY_EXPECT(context, decision.plan.pristine_expected);
        // Requested, never SystemReported: only the opened output can promote the status.
        YY_EXPECT_EQ(context, static_cast<int>(decision.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kRequested));
        YY_EXPECT(context, !decision.plan.same_route_fallback_used);
    }

    YY_CASE(context, "audiotrack cannot satisfy strict exclusive even with bit-perfect");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        const Decision decision = Evaluate(route, source48, SharingRequest::kStrictExclusive,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason),
                     static_cast<int>(Reason::kExclusiveNotProvenByAudioTrack));
    }

    YY_CASE(context, "oboe exclusive with unknown HAL processing plays but claims nothing extra");
    {
        const RouteCapability route = OboeRoute(/*exclusive_possible=*/true);
        const Decision decision = Evaluate(route, source48, SharingRequest::kStrictExclusive,
                                           TransparencyRequest::kCompatible, NoProcessing());
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.plan.sink), static_cast<int>(SinkKind::kOboe));
        YY_EXPECT_EQ(context, static_cast<int>(decision.plan.expected_sharing),
                     static_cast<int>(ActualSharing::kExclusive));
        YY_EXPECT(context, !decision.plan.pristine_expected);
        YY_EXPECT(context, decision.plan.same_route_fallback_used);  // no profile list on this route
        YY_EXPECT_EQ(context, static_cast<int>(decision.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kUnavailable));
    }

    YY_CASE(context, "oboe without exclusive support fails a strict exclusive request");
    {
        const RouteCapability route = OboeRoute(/*exclusive_possible=*/false);
        const Decision decision = Evaluate(route, source48, SharingRequest::kStrictExclusive,
                                           TransparencyRequest::kCompatible, NoProcessing());
        YY_EXPECT(context, !decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason),
                     static_cast<int>(Reason::kStrictExclusiveUnavailable));
    }

    YY_CASE(context, "prefer exclusive degrades to shared only on the same route");
    {
        const RouteCapability route = OboeRoute(/*exclusive_possible=*/false);
        const Decision decision = Evaluate(route, source48, SharingRequest::kPreferExclusive,
                                           TransparencyRequest::kCompatible, NoProcessing());
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.plan.requested_sharing),
                     static_cast<int>(RequestedSharing::kExclusive));
        YY_EXPECT_EQ(context, static_cast<int>(decision.plan.expected_sharing),
                     static_cast<int>(ActualSharing::kShared));
    }

    YY_CASE(context, "software processing refuses strict unprocessed instead of disabling it");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        ProcessingState volume;
        volume.software_volume = true;
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, volume);
        YY_EXPECT(context, !decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason),
                     static_cast<int>(Reason::kStrictUnprocessedDspActive));

        ProcessingState equalizer;
        equalizer.equalizer = true;
        YY_EXPECT(context, !Evaluate(route, source48, SharingRequest::kShared,
                                     TransparencyRequest::kStrictUnprocessed, equalizer).accepted);
        ProcessingState gain;
        gain.replay_gain = true;
        YY_EXPECT(context, !Evaluate(route, source48, SharingRequest::kShared,
                                     TransparencyRequest::kStrictUnprocessed, gain).accepted);
        ProcessingState speed;
        speed.speed_change = true;
        YY_EXPECT(context, !Evaluate(route, source48, SharingRequest::kShared,
                                     TransparencyRequest::kStrictUnprocessed, speed).accepted);
    }

    YY_CASE(context, "processing is allowed when the user only asked for compatible output");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        ProcessingState volume;
        volume.software_volume = true;
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kCompatible, volume);
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT(context, !decision.plan.pristine_expected);
        // Software volume already changed the samples, so the BIT_PERFECT contract must not be
        // requested even though the route still reports one.
        YY_EXPECT(context, !decision.plan.request_bit_perfect);
        YY_EXPECT_EQ(context, static_cast<int>(decision.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kUnavailable));
    }

    YY_CASE(context, "software volume refuses the bit-perfect claim but not the playback");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        ProcessingState volume;
        volume.software_volume = true;
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kPreferUnprocessed, volume);
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT(context, !decision.plan.request_bit_perfect);
        YY_EXPECT_EQ(context, static_cast<int>(decision.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kUnavailable));
        // The same request without processing does ask for the system contract.
        const Decision clean = Evaluate(route, source48, SharingRequest::kShared,
                                        TransparencyRequest::kPreferUnprocessed, NoProcessing());
        YY_EXPECT(context, clean.accepted);
        YY_EXPECT(context, clean.plan.request_bit_perfect);
        YY_EXPECT_EQ(context, static_cast<int>(clean.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kRequested));
    }

    YY_CASE(context, "api 33 or non-bit-perfect route: prefer unprocessed falls back on route");
    {
        const MixerProfile plain = Profile(48000, 2, SampleEncoding::kS16, false);
        RouteCapability route;
        route.device_id = 3;
        route.sink = SinkKind::kAudioTrackSystem;
        route.supports_bit_perfect = false;
        route.profiles = &plain;
        route.profile_count = 1;
        const PcmFormat source16 = SourceFormat(48000, 2, SampleEncoding::kS16);

        const Decision strict = Evaluate(route, source16, SharingRequest::kShared,
                                         TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !strict.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(strict.reason),
                     static_cast<int>(Reason::kStrictUnprocessedNeedsBitPerfect));

        const Decision relaxed = Evaluate(route, source16, SharingRequest::kShared,
                                          TransparencyRequest::kPreferUnprocessed, NoProcessing());
        YY_EXPECT(context, relaxed.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(relaxed.reason), static_cast<int>(Reason::kOk));
        YY_EXPECT_EQ(context, static_cast<int>(relaxed.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kUnavailable));
    }

    YY_CASE(context, "strict unprocessed refuses a resample instead of resampling");
    {
        const MixerProfile only_48 = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&only_48, 1);
        const PcmFormat source44 = SourceFormat(44100, 2, SampleEncoding::kS24In32);
        const Decision decision = Evaluate(route, source44, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason),
                     static_cast<int>(Reason::kStrictUnprocessedWouldResample));
    }

    YY_CASE(context, "a route that claims bit-perfect must provide it for this format");
    {
        // The route advertises BIT_PERFECT support, but the only profile it lists is 16-bit at the
        // source rate: there is no bit-perfect combination for a 24-bit source, so the strict
        // request is refused instead of being quietly opened as a converted stream.
        const MixerProfile only_16 = Profile(44100, 2, SampleEncoding::kS16, false);
        const RouteCapability route = UsbBitPerfectRoute(&only_16, 1);
        const PcmFormat source24 = SourceFormat(44100, 2, SampleEncoding::kS24In32);
        const Decision decision = Evaluate(route, source24, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !decision.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(decision.reason),
                     static_cast<int>(Reason::kStrictUnprocessedNeedsBitPerfect));
    }

    YY_CASE(context, "strict unprocessed is refused when the route offers another format");
    {
        // No bit-perfect claim anywhere on this route, so the concrete mismatch is what surfaces.
        const MixerProfile only_16 = Profile(44100, 2, SampleEncoding::kS16, false);
        RouteCapability route;
        route.device_id = 21;
        route.sink = SinkKind::kAudioTrackSystem;
        route.supports_bit_perfect = false;
        route.profiles = &only_16;
        route.profile_count = 1;
        const PcmFormat source24 = SourceFormat(48000, 2, SampleEncoding::kS24In32);
        const Decision resample = Evaluate(route, source24, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !resample.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(resample.reason),
                     static_cast<int>(Reason::kStrictUnprocessedWouldResample));

        const PcmFormat source44 = SourceFormat(44100, 2, SampleEncoding::kS24In32);
        const Decision convert = Evaluate(route, source44, SharingRequest::kShared,
                                          TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !convert.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(convert.reason),
                     static_cast<int>(Reason::kStrictUnprocessedWouldConvert));

        const MixerProfile mono = Profile(48000, 1, SampleEncoding::kS24In32, false);
        RouteCapability mono_route = route;
        mono_route.profiles = &mono;
        const Decision channels = Evaluate(mono_route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !channels.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(channels.reason),
                     static_cast<int>(Reason::kChannelCountNotOffered));
    }

    YY_CASE(context, "bit-perfect profile is preferred over an equally exact plain profile");
    {
        const MixerProfile profiles[2] = {
            Profile(48000, 2, SampleEncoding::kS24In32, false),
            Profile(48000, 2, SampleEncoding::kS24In32, true),
        };
        const RouteCapability route = UsbBitPerfectRoute(profiles, 2);
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kPreferUnprocessed, NoProcessing());
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT(context, decision.plan.request_bit_perfect);
    }

    YY_CASE(context, "relaxed policy may pick a different profile on the same route");
    {
        const MixerProfile only_96 = Profile(96000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&only_96, 1);
        const Decision decision = Evaluate(route, source48, SharingRequest::kShared,
                                           TransparencyRequest::kPreferUnprocessed, NoProcessing());
        YY_EXPECT(context, decision.accepted);
        YY_EXPECT(context, decision.plan.same_route_fallback_used);
        YY_EXPECT_EQ(context, decision.plan.format.sample_rate, 96000);
        // A 48 kHz source opened at 96 kHz is not pristine and must not claim bit-perfect.
        YY_EXPECT(context, !decision.plan.request_bit_perfect);
        YY_EXPECT(context, !decision.plan.pristine_expected);
        YY_EXPECT_EQ(context, static_cast<int>(decision.bit_perfect),
                     static_cast<int>(BitPerfectStatus::kUnavailable));
    }

    YY_CASE(context, "a route that reports nothing cannot be used for strict output");
    {
        RouteCapability route;
        route.device_id = 11;
        route.sink = SinkKind::kAudioTrackSystem;
        const Decision strict = Evaluate(route, source48, SharingRequest::kShared,
                                         TransparencyRequest::kStrictUnprocessed, NoProcessing());
        YY_EXPECT(context, !strict.accepted);
        YY_EXPECT_EQ(context, static_cast<int>(strict.reason),
                     static_cast<int>(Reason::kStrictUnprocessedProfileUnknown));

        const Decision relaxed = Evaluate(route, source48, SharingRequest::kShared,
                                          TransparencyRequest::kCompatible, NoProcessing());
        YY_EXPECT(context, relaxed.accepted);
        YY_EXPECT(context, relaxed.plan.same_route_fallback_used);
        YY_EXPECT_EQ(context, relaxed.plan.format.sample_rate, 48000);
    }

    YY_CASE(context, "an implausible source format is refused");
    {
        const MixerProfile usb_profile = Profile(48000, 2, SampleEncoding::kS24In32, true);
        const RouteCapability route = UsbBitPerfectRoute(&usb_profile, 1);
        const PcmFormat bad_rate = SourceFormat(0, 2, SampleEncoding::kS24In32);
        YY_EXPECT(context, !Evaluate(route, bad_rate, SharingRequest::kShared,
                                     TransparencyRequest::kCompatible, NoProcessing()).accepted);
        const PcmFormat bad_channels = SourceFormat(48000, 0, SampleEncoding::kS24In32);
        YY_EXPECT(context, !Evaluate(route, bad_channels, SharingRequest::kShared,
                                     TransparencyRequest::kCompatible, NoProcessing()).accepted);
    }

    YY_CASE(context, "reason codes are stable identifiers for logs");
    YY_EXPECT_EQ(context, std::string(ReasonCode(Reason::kOk)), std::string("ok"));
    YY_EXPECT_EQ(context, std::string(ReasonCode(Reason::kStrictUnprocessedNeedsBitPerfect)),
                 std::string("strict_unprocessed_needs_bit_perfect"));
    YY_EXPECT_EQ(context, std::string(ReasonCode(Reason::kExclusiveNotProvenByAudioTrack)),
                 std::string("exclusive_not_proven_by_audiotrack"));
}
