// Sample-level PCM conversion used by the pristine (no DSP) path.
//
// Rule for this core: a conversion is allowed only when it preserves every significant bit of the
// decoded samples. Widening an integer container (s16 -> s24_3 / s24_in32 / s32) does not create
// precision and is therefore allowed and reported; anything that would drop effective bits, go
// through floating point, or use floating point arithmetic is refused here and must be reported by
// the caller as "not pristine" instead of being done silently.
//
// Widening is level-preserving, not value-preserving. Full scale in integer PCM is 2^(bits-1), so a
// sample is a position in its container's range rather than an absolute amplitude: the same numeric
// value in a wider container is a quieter signal. Convert() therefore shifts the sample into the
// destination's significant bits and leaves exact zero low bits, which keeps the level and drops
// nothing. Repacking between containers of the same significant width (s24_in32 <-> s24_3) stays a
// pure move that changes no value.
#ifndef YY_CORE_PCM_H
#define YY_CORE_PCM_H

#include <cstdint>

#include "yy/core/format.h"

namespace yy::core {

enum class ConvertResult : std::uint8_t {
    kOk = 0,
    kUnsupported = 1,   // encoding pair is not implemented by this core
    kLossy = 2,         // conversion would drop significant bits or require float math
    kBadArgument = 3,   // null buffer, invalid format, or byte counts that are not whole frames
    kInsufficientInput = 4,
    kInsufficientOutput = 5,
};

const char* ConvertResultName(ConvertResult result) noexcept;

// True when Convert() can run and keeps the same effective bit depth on the way out.
bool IsPristineConversion(SampleEncoding from, SampleEncoding to) noexcept;

// Effective bits of the converted result, or 0 when the conversion is not pristine/implemented.
std::uint32_t ConvertedEffectiveBits(SampleEncoding from, SampleEncoding to) noexcept;

// Converts whole interleaved frames between `from` and `to`. Only whole frames are converted; a
// trailing partial frame is reported through `converted_frames` and never read or written.
// Returns kOk even when fewer frames were converted than requested, as long as at least one was
// produced; callers must use `converted_frames` and must not assume full progress.
ConvertResult Convert(SampleEncoding from, SampleEncoding to,
                      const std::uint8_t* src, std::uint64_t src_bytes,
                      std::uint8_t* dst, std::uint64_t dst_bytes,
                      std::int32_t channels,
                      std::uint64_t* converted_frames) noexcept;

// Integer arithmetic helpers shared with the engine. Explicitly unsigned so shifts and sign
// extension do not depend on implementation-defined signed overflow behaviour. Each one places the
// sample in the wider container's significant bits, so the level survives the move; `S24` inputs and
// outputs hold 24 significant bits.
std::int32_t WidenS16ToS24(std::int16_t sample) noexcept;
std::int32_t WidenS16ToS32(std::int16_t sample) noexcept;
std::int32_t WidenS24ToS32(std::int32_t sample24) noexcept;

}  // namespace yy::core

#endif  // YY_CORE_PCM_H
