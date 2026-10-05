#include "yy/core/ring.h"

#include <algorithm>
#include <cstring>

namespace yy::core {
namespace {

// Never allocate a ring larger than this for a single format, whatever the caller asks for.
constexpr std::uint64_t kMaxRingBytes = 64ull * 1024ull * 1024ull;

std::uint64_t ClampCapacity(const PcmFormat& format, std::uint64_t capacity_frames) {
    const std::uint64_t frame_bytes = format.IsValid() ? [&format] {
        const std::uint32_t sample_bytes = BytesPerSample(format.encoding);
        if (sample_bytes == kBytesPerSampleUnknown) return std::uint64_t{0};
        return static_cast<std::uint64_t>(sample_bytes) * static_cast<std::uint64_t>(format.channel_count);
    }() : 0;
    if (frame_bytes == 0) return 0;
    std::uint64_t frames = capacity_frames == 0 ? 1 : capacity_frames;
    const std::uint64_t max_frames = kMaxRingBytes / frame_bytes;
    if (frames > max_frames) frames = max_frames == 0 ? 1 : max_frames;
    return frames;
}

}  // namespace

PcmRing::PcmRing(const PcmFormat& format, std::uint64_t capacity_frames)
    : format_(format), capacity_frames_(ClampCapacity(format, capacity_frames)) {
    if (capacity_frames_ == 0) {
        storage_.reset();
        frame_bytes_ = 0;
        return;
    }
    const std::uint32_t sample_bytes = BytesPerSample(format_.encoding);
    frame_bytes_ = static_cast<std::uint64_t>(sample_bytes) * static_cast<std::uint64_t>(format_.channel_count);
    storage_.reset(new std::uint8_t[static_cast<std::size_t>(capacity_frames_ * frame_bytes_)]);
}

PcmRing::~PcmRing() = default;

std::uint8_t* PcmRing::At(std::uint64_t frame_index) const noexcept {
    return storage_.get() + (frame_index % capacity_frames_) * frame_bytes_;
}

std::uint64_t PcmRing::WritableFrames() const noexcept {
    if (frame_bytes_ == 0) return 0;
    const std::uint64_t head = head_.load(std::memory_order_relaxed);
    const std::uint64_t tail = tail_.load(std::memory_order_acquire);
    const std::uint64_t used = head - tail;
    return used >= capacity_frames_ ? 0 : capacity_frames_ - used;
}

std::uint64_t PcmRing::ReadableFrames() const noexcept {
    if (frame_bytes_ == 0) return 0;
    const std::uint64_t head = head_.load(std::memory_order_acquire);
    const std::uint64_t tail = tail_.load(std::memory_order_relaxed);
    const std::uint64_t used = head - tail;
    return used > capacity_frames_ ? capacity_frames_ : used;
}

std::uint64_t PcmRing::Write(const std::uint8_t* src, std::uint64_t frames) noexcept {
    if (src == nullptr || frame_bytes_ == 0 || frames == 0) return 0;
    const std::uint64_t head = head_.load(std::memory_order_relaxed);
    const std::uint64_t tail = tail_.load(std::memory_order_acquire);
    const std::uint64_t used = head - tail;
    const std::uint64_t free_frames = used >= capacity_frames_ ? 0 : capacity_frames_ - used;
    const std::uint64_t to_write = std::min(frames, free_frames);
    if (to_write == 0) {
        dropped_frames_.fetch_add(frames, std::memory_order_relaxed);
        overflow_write_calls_.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    if (to_write < frames) {
        dropped_frames_.fetch_add(frames - to_write, std::memory_order_relaxed);
        overflow_write_calls_.fetch_add(1, std::memory_order_relaxed);
    }
    const std::uint64_t offset = head % capacity_frames_;
    const std::uint64_t first = std::min(to_write, capacity_frames_ - offset);
    std::memcpy(At(head), src, static_cast<std::size_t>(first * frame_bytes_));
    if (to_write > first) {
        std::memcpy(At(0), src + first * frame_bytes_, static_cast<std::size_t>((to_write - first) * frame_bytes_));
    }
    head_.store(head + to_write, std::memory_order_release);
    written_frames_.fetch_add(to_write, std::memory_order_relaxed);
    return to_write;
}

std::uint64_t PcmRing::Read(std::uint8_t* dst, std::uint64_t max_frames) noexcept {
    if (dst == nullptr || frame_bytes_ == 0 || max_frames == 0) return 0;
    const std::uint64_t head = head_.load(std::memory_order_acquire);
    const std::uint64_t tail = tail_.load(std::memory_order_relaxed);
    const std::uint64_t used = head - tail;
    const std::uint64_t available = used > capacity_frames_ ? capacity_frames_ : used;
    const std::uint64_t to_read = std::min(max_frames, available);
    if (to_read == 0) {
        underrun_frames_.fetch_add(max_frames, std::memory_order_relaxed);
        underrun_read_calls_.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    if (to_read < max_frames) {
        underrun_frames_.fetch_add(max_frames - to_read, std::memory_order_relaxed);
        underrun_read_calls_.fetch_add(1, std::memory_order_relaxed);
    }
    const std::uint64_t offset = tail % capacity_frames_;
    const std::uint64_t first = std::min(to_read, capacity_frames_ - offset);
    std::memcpy(dst, At(tail), static_cast<std::size_t>(first * frame_bytes_));
    if (to_read > first) {
        std::memcpy(dst + first * frame_bytes_, At(0), static_cast<std::size_t>((to_read - first) * frame_bytes_));
    }
    tail_.store(tail + to_read, std::memory_order_release);
    read_frames_.fetch_add(to_read, std::memory_order_relaxed);
    return to_read;
}

void PcmRing::Clear() noexcept {
    if (frame_bytes_ == 0) return;
    const std::uint64_t head = head_.load(std::memory_order_acquire);
    // Discarding unread frames is not an underrun: the caller asked for the drop.
    tail_.store(head, std::memory_order_release);
}

RingStats PcmRing::Stats() const noexcept {
    RingStats stats;
    stats.written_frames = written_frames_.load(std::memory_order_relaxed);
    stats.read_frames = read_frames_.load(std::memory_order_relaxed);
    stats.dropped_frames = dropped_frames_.load(std::memory_order_relaxed);
    stats.underrun_frames = underrun_frames_.load(std::memory_order_relaxed);
    stats.overflow_write_calls = overflow_write_calls_.load(std::memory_order_relaxed);
    stats.underrun_read_calls = underrun_read_calls_.load(std::memory_order_relaxed);
    return stats;
}

}  // namespace yy::core
