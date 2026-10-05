#include "yy/core/pcm.h"

namespace yy::core {
namespace {

std::uint32_t SampleBytes(SampleEncoding encoding) noexcept {
    return BytesPerSample(encoding);
}

// Little-endian load/store. Sample data from sinks and decoders is little-endian on every platform
// this project targets, and the byte order is part of the format contract, not of the host.
std::uint32_t LoadLE(const std::uint8_t* p, std::uint32_t bytes) noexcept {
    std::uint32_t value = 0;
    for (std::uint32_t i = 0; i < bytes; ++i) {
        value |= static_cast<std::uint32_t>(p[i]) << (8u * i);
    }
    return value;
}

void StoreLE(std::uint8_t* p, std::uint32_t bytes, std::uint32_t value) noexcept {
    for (std::uint32_t i = 0; i < bytes; ++i) {
        p[i] = static_cast<std::uint8_t>((value >> (8u * i)) & 0xFFu);
    }
}

std::int32_t SignExtend(std::uint32_t value, std::uint32_t bits) noexcept {
    if (bits == 0 || bits >= 32) return static_cast<std::int32_t>(value);
    const std::uint32_t sign_bit = 1u << (bits - 1u);
    if ((value & sign_bit) == 0) return static_cast<std::int32_t>(value);
    const std::uint32_t widened = value | ~((1u << bits) - 1u);
    return static_cast<std::int32_t>(widened);
}

// Reads one sample as a signed 32-bit integer in the source's own scale.
std::int32_t ReadSample(SampleEncoding encoding, const std::uint8_t* p) noexcept {
    switch (encoding) {
        case SampleEncoding::kS16:
            return SignExtend(LoadLE(p, 2), 16);
        case SampleEncoding::kS24_3:
            return SignExtend(LoadLE(p, 3), 24);
        case SampleEncoding::kS24In32:
        case SampleEncoding::kS32:
            return SignExtend(LoadLE(p, 4), 32);
        case SampleEncoding::kUnknown:
        case SampleEncoding::kU8:
        case SampleEncoding::kF32:
            break;
    }
    return 0;
}

// Writes one sample without changing its scale. `bits` is the significant width of the value.
void WriteSample(SampleEncoding encoding, std::uint8_t* p, std::int32_t sample, std::uint32_t bits) noexcept {
    switch (encoding) {
        case SampleEncoding::kS16:
            StoreLE(p, 2, static_cast<std::uint32_t>(sample) & 0xFFFFu);
            return;
        case SampleEncoding::kS24_3:
            StoreLE(p, 3, static_cast<std::uint32_t>(sample) & 0xFFFFFFu);
            return;
        case SampleEncoding::kS24In32:
        case SampleEncoding::kS32:
            StoreLE(p, 4, static_cast<std::uint32_t>(sample));
            return;
        case SampleEncoding::kUnknown:
        case SampleEncoding::kU8:
        case SampleEncoding::kF32:
            break;
    }
    (void)bits;
}

}  // namespace

const char* ConvertResultName(ConvertResult result) noexcept {
    switch (result) {
        case ConvertResult::kOk: return "ok";
        case ConvertResult::kUnsupported: return "unsupported";
        case ConvertResult::kLossy: return "lossy";
        case ConvertResult::kBadArgument: return "bad_argument";
        case ConvertResult::kInsufficientInput: return "insufficient_input";
        case ConvertResult::kInsufficientOutput: return "insufficient_output";
    }
    return "unknown";
}

std::uint32_t ConvertedEffectiveBits(SampleEncoding from, SampleEncoding to) noexcept {
    if (!IsPristineConversion(from, to)) return 0;
    // A pristine conversion changes containers, never sample values, so the significant width of
    // the output equals the significant width of the input.
    return EffectiveBits(from);
}

bool IsPristineConversion(SampleEncoding from, SampleEncoding to) noexcept {
    if (from == to) {
        return BytesPerSample(from) != kBytesPerSampleUnknown;
    }
    const std::uint32_t from_bits = EffectiveBits(from);
    const std::uint32_t to_bits = EffectiveBits(to);
    if (from_bits == 0 || to_bits == 0) return false;
    // Integer containers only. Float containers are not part of the pristine path, and unsigned
    // 8-bit samples are not a supported sink format for this player.
    const bool integer_pair = from != SampleEncoding::kF32 && to != SampleEncoding::kF32 &&
                              from != SampleEncoding::kU8 && to != SampleEncoding::kU8;
    if (!integer_pair) return false;
    if (to_bits > from_bits) return true;  // widening a container never changes a sample value
    if (to_bits == from_bits) return true; // same significant width: repacking keeps every value
    return false;
}

ConvertResult Convert(SampleEncoding from, SampleEncoding to,
                      const std::uint8_t* src, std::uint64_t src_bytes,
                      std::uint8_t* dst, std::uint64_t dst_bytes,
                      std::int32_t channels,
                      std::uint64_t* converted_frames) noexcept {
    if (converted_frames != nullptr) *converted_frames = 0;
    if (src == nullptr || dst == nullptr || converted_frames == nullptr) {
        return ConvertResult::kBadArgument;
    }
    const std::uint32_t src_sample_bytes = SampleBytes(from);
    const std::uint32_t dst_sample_bytes = SampleBytes(to);
    if (src_sample_bytes == kBytesPerSampleUnknown || dst_sample_bytes == kBytesPerSampleUnknown) {
        return ConvertResult::kUnsupported;
    }
    if (channels <= 0 || channels > kChannelCountMax) return ConvertResult::kBadArgument;
    // Same buffer with different sample widths would overwrite unread input.
    if (src == dst && src_sample_bytes != dst_sample_bytes) return ConvertResult::kBadArgument;
    if (!IsPristineConversion(from, to)) {
        return (EffectiveBits(from) != 0 && EffectiveBits(to) != 0) ? ConvertResult::kLossy
                                                                   : ConvertResult::kUnsupported;
    }

    const std::uint64_t src_frame_bytes = static_cast<std::uint64_t>(src_sample_bytes) *
                                          static_cast<std::uint64_t>(channels);
    const std::uint64_t dst_frame_bytes = static_cast<std::uint64_t>(dst_sample_bytes) *
                                          static_cast<std::uint64_t>(channels);
    const std::uint64_t src_frames = src_bytes / src_frame_bytes;
    const std::uint64_t dst_frames = dst_bytes / dst_frame_bytes;
    const std::uint64_t frames = src_frames < dst_frames ? src_frames : dst_frames;
    if (frames == 0) {
        return src_frames == 0 ? ConvertResult::kInsufficientInput : ConvertResult::kInsufficientOutput;
    }

    if (from == to) {
        // Same width: still an explicit copy so the caller never aliases the ring buffer.
        for (std::uint64_t i = 0; i < frames * dst_frame_bytes; ++i) dst[i] = src[i];
        *converted_frames = frames;
        return ConvertResult::kOk;
    }

    const std::uint32_t src_bits = EffectiveBits(from);
    const std::uint64_t samples = frames * static_cast<std::uint64_t>(channels);
    for (std::uint64_t i = 0; i < samples; ++i) {
        const std::int32_t sample = ReadSample(from, src + i * src_sample_bytes);
        // The destination advances by its own sample width, not by the source width.
        WriteSample(to, dst + i * dst_sample_bytes, sample, src_bits);
    }
    *converted_frames = frames;
    return ConvertResult::kOk;
}
std::int32_t WidenS16ToS24(std::int16_t sample) noexcept {
    return static_cast<std::int32_t>(sample);
}

std::int32_t WidenS16ToS32(std::int16_t sample) noexcept {
    return static_cast<std::int32_t>(sample);
}

std::int32_t WidenS24ToS32(std::int32_t sample24) noexcept {
    return sample24;
}

}  // namespace yy::core
