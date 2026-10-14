#include "yy/core/format.h"

namespace yy::core {
namespace {

constexpr std::uint64_t kOverflow = static_cast<std::uint64_t>(-1);

}  // namespace

std::uint32_t BytesPerSample(SampleEncoding encoding) noexcept {
    switch (encoding) {
        case SampleEncoding::kU8: return 1;
        case SampleEncoding::kS16: return 2;
        case SampleEncoding::kS24_3: return 3;
        case SampleEncoding::kS24In32: return 4;
        case SampleEncoding::kS32: return 4;
        case SampleEncoding::kF32: return 4;
        case SampleEncoding::kUnknown: break;
    }
    return kBytesPerSampleUnknown;
}

std::uint32_t EffectiveBits(SampleEncoding encoding) noexcept {
    switch (encoding) {
        case SampleEncoding::kU8: return 8;
        case SampleEncoding::kS16: return 16;
        case SampleEncoding::kS24_3: return 24;
        case SampleEncoding::kS24In32: return 24;
        case SampleEncoding::kS32: return 32;
        case SampleEncoding::kF32: return 32;
        case SampleEncoding::kUnknown: break;
    }
    return 0;
}

const char* EncodingName(SampleEncoding encoding) noexcept {
    switch (encoding) {
        case SampleEncoding::kUnknown: return "unknown";
        case SampleEncoding::kU8: return "u8";
        case SampleEncoding::kS16: return "s16";
        case SampleEncoding::kS24_3: return "s24_3";
        case SampleEncoding::kS24In32: return "s24_in32";
        case SampleEncoding::kS32: return "s32";
        case SampleEncoding::kF32: return "f32";
    }
    return "unknown";
}

bool IsSupportedSampleRate(std::int32_t sample_rate) noexcept {
    return sample_rate >= kSampleRateMin && sample_rate <= kSampleRateMax;
}

bool IsPlausibleFormat(const PcmFormat& format) noexcept {
    if (!IsSupportedSampleRate(format.sample_rate)) return false;
    if (format.channel_count <= 0 || format.channel_count > kChannelCountMax) return false;
    if (BytesPerSample(format.encoding) == kBytesPerSampleUnknown) return false;
    if (format.channel_mask != 0) {
        // A mask must describe exactly the declared channels; a partial mask means the caller is
        // guessing the layout, which this core refuses to silently accept.
        ChannelMask expected = MaskForChannels(format.channel_count);
        if (format.channel_mask != expected) return false;
    }
    return true;
}

std::uint64_t ByteSizeOfFrames(const PcmFormat& format, std::uint64_t frames) noexcept {
    const std::uint32_t sample_bytes = BytesPerSample(format.encoding);
    if (sample_bytes == kBytesPerSampleUnknown) return kOverflow;
    if (format.channel_count <= 0) return kOverflow;
    if (frames > kOverflow / sample_bytes) return kOverflow;
    const std::uint64_t frame_bytes = static_cast<std::uint64_t>(sample_bytes) *
                                      static_cast<std::uint64_t>(format.channel_count);
    if (frames > kOverflow / frame_bytes) return kOverflow;
    return frames * frame_bytes;
}

std::uint64_t FramesInBytes(const PcmFormat& format, std::uint64_t bytes) noexcept {
    const std::uint32_t sample_bytes = BytesPerSample(format.encoding);
    if (sample_bytes == kBytesPerSampleUnknown) return 0;
    if (format.channel_count <= 0) return 0;
    const std::uint64_t frame_bytes = static_cast<std::uint64_t>(sample_bytes) *
                                      static_cast<std::uint64_t>(format.channel_count);
    return bytes / frame_bytes;
}

ChannelMask MaskForChannels(std::int32_t channels) noexcept {
    if (channels <= 0 || channels > kChannelCountMax) return 0;
    if (channels >= 32) return 0xFFFFFFFFu;
    return (static_cast<ChannelMask>(1) << channels) - 1u;
}

}  // namespace yy::core
