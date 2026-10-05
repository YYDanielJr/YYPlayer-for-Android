// Single-producer / single-consumer PCM ring.
//
// One writer thread (decoder feed) and one reader thread (sink writer or realtime callback) share
// this buffer with no lock, no allocation and no I/O. The reader must never block: when there are
// fewer frames than requested it copies what exists, counts the shortfall, and returns.
#ifndef YY_CORE_RING_H
#define YY_CORE_RING_H

#include <atomic>
#include <cstdint>
#include <memory>

#include "yy/core/format.h"

namespace yy::core {

struct RingStats {
    std::uint64_t written_frames = 0;      // frames accepted by Write
    std::uint64_t read_frames = 0;         // frames handed to the sink
    std::uint64_t dropped_frames = 0;      // frames refused because the ring was full
    std::uint64_t underrun_frames = 0;     // frames the sink asked for but could not get
    std::uint64_t overflow_write_calls = 0;
    std::uint64_t underrun_read_calls = 0;
};

class PcmRing {
 public:
    // `format` must already describe the byte layout; capacity is clamped to at least one frame.
    PcmRing(const PcmFormat& format, std::uint64_t capacity_frames);
    ~PcmRing();

    PcmRing(const PcmRing&) = delete;
    PcmRing& operator=(const PcmRing&) = delete;

    const PcmFormat& format() const noexcept { return format_; }

    // Frames the ring can currently accept / provide. Each is a snapshot and may be stale by the
    // time the caller acts on it; the copy calls are authoritative.
    std::uint64_t WritableFrames() const noexcept;
    std::uint64_t ReadableFrames() const noexcept;
    std::uint64_t capacity_frames() const noexcept { return capacity_frames_; }

    // Writer side. Returns frames actually written; a full ring drops the remainder and counts it.
    std::uint64_t Write(const std::uint8_t* src, std::uint64_t frames) noexcept;

    // Reader side. Returns frames actually copied; a short ring counts the missing frames as
    // underrun. `frames` is capped by the destination capacity the caller passes as `max_frames`.
    std::uint64_t Read(std::uint8_t* dst, std::uint64_t max_frames) noexcept;

    // Discards every buffered frame. Used on seek, format change and route loss. Safe to call from
    // the writer thread only; a reader that is already inside Read() sees a consistent tail.
    void Clear() noexcept;

    RingStats Stats() const noexcept;

 private:
    std::uint8_t* At(std::uint64_t frame_index) const noexcept;

    PcmFormat format_;
    std::uint64_t capacity_frames_ = 0;
    std::uint64_t frame_bytes_ = 0;  // 0 when the format is invalid: the ring then stores nothing
    std::unique_ptr<std::uint8_t[]> storage_;

    // Owned by the writer / reader respectively; published to the other side with release/acquire.
    alignas(64) std::atomic<std::uint64_t> head_{0};
    alignas(64) std::atomic<std::uint64_t> tail_{0};

    std::atomic<std::uint64_t> written_frames_{0};
    std::atomic<std::uint64_t> read_frames_{0};
    std::atomic<std::uint64_t> dropped_frames_{0};
    std::atomic<std::uint64_t> underrun_frames_{0};
    std::atomic<std::uint64_t> overflow_write_calls_{0};
    std::atomic<std::uint64_t> underrun_read_calls_{0};
};

}  // namespace yy::core

#endif  // YY_CORE_RING_H
