// YYPlayer portable media core: PCM format description shared by every platform sink.
// Plain C++17, no OS, JNI, Android or third-party types may appear in this header.
#ifndef YY_CORE_FORMAT_H
#define YY_CORE_FORMAT_H

#include <cstdint>

namespace yy::core {

// Sink-side sample encodings. Names describe the container, not the effective bit depth:
// kS24_3 is 24 significant bits in 3 packed bytes, kS24In32 keeps 24 significant bits in a
// 4-byte container. A sink may not treat those two as interchangeable at the data level.
enum class SampleEncoding : std::uint8_t {
    kUnknown = 0,
    kU8 = 1,
    kS16 = 2,
    kS24_3 = 3,
    kS24In32 = 4,
    kS32 = 5,
    kF32 = 6,
};

// Interleaved sample count == channel count. Bit i is set when channel i carries a sample.
using ChannelMask = std::uint32_t;

constexpr std::uint32_t kBytesPerSampleUnknown = 0;

std::uint32_t BytesPerSample(SampleEncoding encoding) noexcept;

// Effective significant bits: kS24_3 and kS24In32 both report 24, kS32 reports 32.
std::uint32_t EffectiveBits(SampleEncoding encoding) noexcept;

const char* EncodingName(SampleEncoding encoding) noexcept;

struct PcmFormat {
    std::int32_t sample_rate = 0;              // Hz, > 0 when known
    std::int32_t channel_count = 0;            // interleaved channel count
    SampleEncoding encoding = SampleEncoding::kUnknown;
    ChannelMask channel_mask = 0;              // optional, 0 when unknown

    constexpr bool IsValid() const noexcept {
        return sample_rate > 0 && channel_count > 0 && encoding != SampleEncoding::kUnknown;
    }
};

constexpr std::int32_t kSampleRateMin = 8000;
constexpr std::int32_t kSampleRateMax = 768000;
constexpr std::int32_t kChannelCountMax = 32;

// Range checks that never depend on the platform. Returns false for unknown/out-of-range input so
// callers must treat "unknown" as unknown instead of substituting a default.
bool IsSupportedSampleRate(std::int32_t sample_rate) noexcept;
bool IsPlausibleFormat(const PcmFormat& format) noexcept;

// Byte size of interleaved frames. Returns kBytesPerSampleUnknown when the input is not a format
// this core understands, or when the product would overflow 64 bits.
std::uint64_t ByteSizeOfFrames(const PcmFormat& format, std::uint64_t frames) noexcept;

// Frames that fit in a byte count, truncating a partial frame. Overflow-safe.
std::uint64_t FramesInBytes(const PcmFormat& format, std::uint64_t bytes) noexcept;

// Bit mask with the lowest `channels` bits set, 0 for an out-of-range count.
ChannelMask MaskForChannels(std::int32_t channels) noexcept;

}  // namespace yy::core

#endif  // YY_CORE_FORMAT_H
