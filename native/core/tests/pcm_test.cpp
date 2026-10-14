#include <cstdint>
#include <cstring>
#include <random>
#include <vector>

#include "test_support.h"
#include "yy/core/pcm.h"

using namespace yy::core;

namespace {

std::vector<std::uint8_t> BytesOf16(const std::vector<std::int16_t>& samples) {
    std::vector<std::uint8_t> out(samples.size() * 2);
    for (std::size_t i = 0; i < samples.size(); ++i) {
        const std::uint16_t value = static_cast<std::uint16_t>(samples[i]);
        out[i * 2] = static_cast<std::uint8_t>(value & 0xFFu);
        out[i * 2 + 1] = static_cast<std::uint8_t>((value >> 8) & 0xFFu);
    }
    return out;
}

std::int32_t Read24In32(const std::uint8_t* p) {
    const std::uint32_t raw = static_cast<std::uint32_t>(p[0]) |
                              (static_cast<std::uint32_t>(p[1]) * 256) |
                              (static_cast<std::uint32_t>(p[2]) * 65536) |
                              (static_cast<std::uint32_t>(p[3]) << 24);
    return static_cast<std::int32_t>(raw);
}

std::int32_t Read24Packed(const std::uint8_t* p) {
    std::uint32_t raw = static_cast<std::uint32_t>(p[0]) |
                        (static_cast<std::uint32_t>(p[1]) * 256) |
                        (static_cast<std::uint32_t>(p[2]) * 65536);
    if ((raw & 0x800000u) != 0) raw |= 0xFF000000u;
    return static_cast<std::int32_t>(raw);
}

// Position of a sample inside its container's range, in millionths of full scale. Two samples with
// the same fraction represent the same analog level regardless of how many bits carry them, which is
// the property that makes a widening conversion level-preserving.
std::int64_t FullScaleFraction(std::int32_t sample, int bits) {
    const std::int64_t full_scale = static_cast<std::int64_t>(1) << (bits - 1);
    return static_cast<std::int64_t>(sample) * 1000000 / full_scale;
}

}  // namespace

void yy::test::RunPcmTests(Context& context) {
    context.Begin("pcm");

    YY_CASE(context, "pristine conversion matrix");
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS16, SampleEncoding::kS16));
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS16, SampleEncoding::kS24_3));
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS16, SampleEncoding::kS24In32));
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS16, SampleEncoding::kS32));
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS24_3, SampleEncoding::kS24In32));
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS24_3, SampleEncoding::kS32));
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS24In32, SampleEncoding::kS32));
    // Dropping bits or going through float is never pristine.
    YY_EXPECT(context, !IsPristineConversion(SampleEncoding::kS32, SampleEncoding::kS16));
    YY_EXPECT(context, !IsPristineConversion(SampleEncoding::kS24In32, SampleEncoding::kS16));
    // s24-in-32 keeps its 24 significant bits in the low bits of a 4-byte container and only pads
    // the high byte, so repacking it into the 3-byte container changes no sample value.
    YY_EXPECT(context, IsPristineConversion(SampleEncoding::kS24In32, SampleEncoding::kS24_3));
    YY_EXPECT(context, !IsPristineConversion(SampleEncoding::kS16, SampleEncoding::kF32));
    YY_EXPECT(context, !IsPristineConversion(SampleEncoding::kF32, SampleEncoding::kS32));
    YY_EXPECT(context, !IsPristineConversion(SampleEncoding::kS16, SampleEncoding::kU8));
    YY_EXPECT(context, !IsPristineConversion(SampleEncoding::kUnknown, SampleEncoding::kS16));
    // The reported width is the width of the samples that come out, which widening never increases.
    YY_EXPECT_EQ(context, ConvertedEffectiveBits(SampleEncoding::kS16, SampleEncoding::kS24In32), 16u);
    YY_EXPECT_EQ(context, ConvertedEffectiveBits(SampleEncoding::kS24In32, SampleEncoding::kS32), 24u);
    YY_EXPECT_EQ(context, ConvertedEffectiveBits(SampleEncoding::kS24In32, SampleEncoding::kS16), 0u);

    YY_CASE(context, "extreme and alternating samples");
    // Zero, +/-1, min/max and sample-by-sample channel alternation all survive 16 -> 24 containers.
    // The values are compared against the *scaled* expectation: full scale in integer PCM is
    // 2^(bits-1), so a 16-bit sample occupies the top 16 bits of the 24-bit container.
    const std::vector<std::int16_t> samples = {
        0, 0,
        1, -1,
        -1, 1,
        32767, -32768,
        -32768, 32767,
        12345, -12345,
    };
    const std::vector<std::uint8_t> src16 = BytesOf16(samples);
    std::vector<std::uint8_t> dst24in32(samples.size() * 4);
    std::uint64_t frames = 0;
    ConvertResult result = Convert(SampleEncoding::kS16, SampleEncoding::kS24In32,
                                   src16.data(), src16.size(), dst24in32.data(), dst24in32.size(),
                                   2, &frames);
    YY_EXPECT_EQ(context, static_cast<int>(result), static_cast<int>(ConvertResult::kOk));
    YY_EXPECT_EQ(context, frames, 6ull);
    for (std::size_t i = 0; i < samples.size(); ++i) {
        const std::int32_t widened = Read24In32(dst24in32.data() + i * 4);
        YY_EXPECT_EQ(context, widened, static_cast<std::int32_t>(samples[i]) * 256);
    }
    // Widening changes the container, never the level: the sample must land in the destination's
    // significant bits, so its fraction of full scale is identical before and after.
    YY_EXPECT_EQ(context, FullScaleFraction(Read24In32(dst24in32.data() + 6 * 4), 24),
                 FullScaleFraction(static_cast<std::int32_t>(samples[6]), 16));
    YY_EXPECT_EQ(context, FullScaleFraction(Read24In32(dst24in32.data() + 7 * 4), 24),
                 FullScaleFraction(static_cast<std::int32_t>(samples[7]), 16));

    YY_CASE(context, "16 -> 24 packed keeps sign");
    std::vector<std::uint8_t> packed(samples.size() * 3);
    frames = 0;
    result = Convert(SampleEncoding::kS16, SampleEncoding::kS24_3,
                     src16.data(), src16.size(), packed.data(), packed.size(), 2, &frames);
    YY_EXPECT_EQ(context, static_cast<int>(result), static_cast<int>(ConvertResult::kOk));
    YY_EXPECT_EQ(context, frames, 6ull);
    for (std::size_t i = 0; i < samples.size(); ++i) {
        YY_EXPECT_EQ(context, Read24Packed(packed.data() + i * 3),
                     static_cast<std::int32_t>(samples[i]) * 256);
    }

    YY_CASE(context, "24 packed -> 24 in 32 keeps value");
    // Same significant width: this is a pure repack, so no scaling happens at all.
    frames = 0;
    std::vector<std::uint8_t> widened(samples.size() * 4);
    result = Convert(SampleEncoding::kS24_3, SampleEncoding::kS24In32,
                     packed.data(), packed.size(), widened.data(), widened.size(), 2, &frames);
    YY_EXPECT_EQ(context, static_cast<int>(result), static_cast<int>(ConvertResult::kOk));
    for (std::size_t i = 0; i < samples.size(); ++i) {
        YY_EXPECT_EQ(context, Read24In32(widened.data() + i * 4),
                     static_cast<std::int32_t>(samples[i]) * 256);
    }

    YY_CASE(context, "same encoding copies");
    std::vector<std::uint8_t> copy(src16.size(), 0);
    frames = 0;
    result = Convert(SampleEncoding::kS16, SampleEncoding::kS16,
                     src16.data(), src16.size(), copy.data(), copy.size(), 2, &frames);
    YY_EXPECT_EQ(context, static_cast<int>(result), static_cast<int>(ConvertResult::kOk));
    YY_EXPECT_EQ(context, frames, 6ull);
    YY_EXPECT(context, std::memcmp(src16.data(), copy.data(), src16.size()) == 0);

    YY_CASE(context, "lossy conversions are refused, not approximated");
    frames = 123;
    result = Convert(SampleEncoding::kS24In32, SampleEncoding::kS16,
                     widened.data(), widened.size(), copy.data(), copy.size(), 2, &frames);
    YY_EXPECT_EQ(context, static_cast<int>(result), static_cast<int>(ConvertResult::kLossy));
    YY_EXPECT_EQ(context, frames, 0ull);

    YY_CASE(context, "partial frames are never read or written");
    // 5 bytes of stereo 16-bit data: one whole frame plus one stray byte.
    std::vector<std::uint8_t> odd(5);
    std::memcpy(odd.data(), src16.data(), 4);
    odd[4] = 0xAB;
    std::vector<std::uint8_t> odd_out(8, 0xCD);
    frames = 0;
    result = Convert(SampleEncoding::kS16, SampleEncoding::kS24In32,
                     odd.data(), odd.size(), odd_out.data(), odd_out.size(), 2, &frames);
    YY_EXPECT_EQ(context, static_cast<int>(result), static_cast<int>(ConvertResult::kOk));
    YY_EXPECT_EQ(context, frames, 1ull);
    // One stereo frame at 24-in-32 is exactly the 8-byte destination: both samples are zero, and
    // the stray fifth input byte is not a frame, so nothing beyond it is read.
    for (int i = 0; i < 8; ++i) {
        YY_EXPECT_EQ(context, odd_out[static_cast<std::size_t>(i)], static_cast<std::uint8_t>(0x00));
    }
    YY_EXPECT_EQ(context, odd[4], static_cast<std::uint8_t>(0xAB));  // source untouched

    YY_CASE(context, "arguments and small buffers");
    frames = 0;
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS16,
                                                   nullptr, 4, copy.data(), copy.size(), 2, &frames)),
                 static_cast<int>(ConvertResult::kBadArgument));
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS16,
                                                   src16.data(), src16.size(), copy.data(), copy.size(),
                                                   0, &frames)),
                 static_cast<int>(ConvertResult::kBadArgument));
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS16,
                                                   src16.data(), src16.size(), copy.data(), copy.size(),
                                                   2, nullptr)),
                 static_cast<int>(ConvertResult::kBadArgument));
    // Output too small for even one frame.
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS16,
                                                   src16.data(), src16.size(), copy.data(), 3, 2, &frames)),
                 static_cast<int>(ConvertResult::kInsufficientOutput));
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS16,
                                                   src16.data(), 3, copy.data(), copy.size(), 2, &frames)),
                 static_cast<int>(ConvertResult::kInsufficientInput));

    YY_CASE(context, "random round trip 16 -> 32 is level preserving and refuses narrowing");
    std::mt19937 rng(20261005u);
    std::uniform_int_distribution<int> distribution(-32768, 32767);
    std::vector<std::int16_t> random_samples(2000);
    for (auto& sample : random_samples) sample = static_cast<std::int16_t>(distribution(rng));
    const std::vector<std::uint8_t> random16 = BytesOf16(random_samples);
    std::vector<std::uint8_t> random32(random_samples.size() * 4);
    frames = 0;
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS32,
                                                   random16.data(), random16.size(), random32.data(),
                                                   random32.size(), 2, &frames)),
                 static_cast<int>(ConvertResult::kOk));
    std::vector<std::uint8_t> back(random16.size());
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS32, SampleEncoding::kS16,
                                                   random32.data(), random32.size(), back.data(),
                                                   back.size(), 2, &frames)),
                 static_cast<int>(ConvertResult::kLossy));
    // The 32-bit container holds the 16-bit sample shifted into its top 16 bits, so every sample sits
    // at exactly the same fraction of full scale it had before. Unshifted storage would pass a
    // "value preserved" check and still be 96 dB quiet, which is the bug this asserts against.
    for (std::size_t i = 0; i < random_samples.size(); ++i) {
        const std::uint32_t raw = static_cast<std::uint32_t>(random32[i * 4]) |
                                  (static_cast<std::uint32_t>(random32[i * 4 + 1]) * 256) |
                                  (static_cast<std::uint32_t>(random32[i * 4 + 2]) * 65536) |
                                  (static_cast<std::uint32_t>(random32[i * 4 + 3]) << 24);
        const std::int32_t wide = static_cast<std::int32_t>(raw);
        YY_EXPECT_EQ(context, wide, static_cast<std::int32_t>(random_samples[i]) * 65536);
        YY_EXPECT_EQ(context, FullScaleFraction(wide, 32),
                     FullScaleFraction(static_cast<std::int32_t>(random_samples[i]), 16));
    }
    // A 24-in-32 container widened to s32 must also keep its level, and must not be shifted twice.
    std::vector<std::uint8_t> from24in32(random_samples.size() * 4);
    std::vector<std::uint8_t> toS32(from24in32.size());
    frames = 0;
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS24In32,
                                                   random16.data(), random16.size(),
                                                   from24in32.data(), from24in32.size(), 2, &frames)),
                 static_cast<int>(ConvertResult::kOk));
    YY_EXPECT_EQ(context, frames, random_samples.size() / 2);  // 2000 samples = 1000 stereo frames
    frames = 0;
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS24In32, SampleEncoding::kS32,
                                                   from24in32.data(), from24in32.size(), toS32.data(),
                                                   toS32.size(), 2, &frames)),
                 static_cast<int>(ConvertResult::kOk));
    YY_EXPECT_EQ(context, frames, random_samples.size() / 2);  // 2000 samples = 1000 stereo frames
    for (std::size_t i = 0; i < random_samples.size(); ++i) {
        const std::uint32_t raw = static_cast<std::uint32_t>(toS32[i * 4]) |
                                  (static_cast<std::uint32_t>(toS32[i * 4 + 1]) * 256) |
                                  (static_cast<std::uint32_t>(toS32[i * 4 + 2]) * 65536) |
                                  (static_cast<std::uint32_t>(toS32[i * 4 + 3]) << 24);
        YY_EXPECT_EQ(context, static_cast<std::int32_t>(raw),
                     static_cast<std::int32_t>(random_samples[i]) * 65536);
    }

    YY_CASE(context, "widening helpers keep the level");
    YY_EXPECT_EQ(context, WidenS16ToS24(static_cast<std::int16_t>(-32768)), -8388608);
    YY_EXPECT_EQ(context, WidenS16ToS24(static_cast<std::int16_t>(32767)), 8388352);
    YY_EXPECT_EQ(context, WidenS16ToS32(static_cast<std::int16_t>(-1)), -65536);
    YY_EXPECT_EQ(context, WidenS24ToS32(-8388608), -2147483647 - 1);

    YY_CASE(context, "result names are stable for logs");
    YY_CASE(context, "overlapping ranges are refused before modifying PCM");
    std::vector<std::uint8_t> overlap(32, 0x55);
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS16, SampleEncoding::kS32,
        overlap.data(), 8, overlap.data() + 2, 16, 2, &frames)),
        static_cast<int>(ConvertResult::kBadArgument));
    YY_EXPECT_EQ(context, frames, 0ull);
    for (auto byte : overlap) YY_EXPECT_EQ(context, byte, static_cast<std::uint8_t>(0x55));

    YY_CASE(context, "24-in-32 ignores padding and widens signed extremes without overflow");
    const std::uint8_t padded24[] = {0, 0, 0x80, 0x55, 0xff, 0xff, 0x7f, 0xaa};
    std::uint8_t paddedOut[8] = {};
    YY_EXPECT_EQ(context, static_cast<int>(Convert(SampleEncoding::kS24In32, SampleEncoding::kS32,
        padded24, sizeof(padded24), paddedOut, sizeof(paddedOut), 2, &frames)),
        static_cast<int>(ConvertResult::kOk));
    const std::uint8_t expectedPadded[] = {0, 0, 0, 0x80, 0, 0xff, 0xff, 0x7f};
    for (std::size_t i = 0; i < sizeof(paddedOut); ++i) YY_EXPECT_EQ(context, paddedOut[i], expectedPadded[i]);
    YY_EXPECT_EQ(context, WidenS16ToS32(static_cast<std::int16_t>(-32768)), -2147483647 - 1);
    YY_EXPECT_EQ(context, WidenS24ToS32(8388607), 2147483392);
    YY_EXPECT_EQ(context, std::string(ConvertResultName(ConvertResult::kLossy)), std::string("lossy"));
    YY_EXPECT_EQ(context, std::string(ConvertResultName(ConvertResult::kOk)), std::string("ok"));
}
