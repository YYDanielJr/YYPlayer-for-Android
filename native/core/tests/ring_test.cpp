#include <cstdint>
#include <thread>
#include <vector>

#include "test_support.h"
#include "yy/core/ring.h"

using namespace yy::core;

namespace {

PcmFormat Stereo48(SampleEncoding encoding) {
    PcmFormat format;
    format.sample_rate = 48000;
    format.channel_count = 2;
    format.encoding = encoding;
    return format;
}

std::vector<std::uint8_t> Pattern(std::uint64_t frames, std::uint32_t frame_bytes, std::uint8_t seed) {
    std::vector<std::uint8_t> data(static_cast<std::size_t>(frames * frame_bytes));
    for (std::size_t i = 0; i < data.size(); ++i) {
        data[i] = static_cast<std::uint8_t>((seed + i) & 0xFFu);
    }
    return data;
}

}  // namespace

void yy::test::RunRingTests(Context& context) {
    context.Begin("ring");

    YY_CASE(context, "capacity and empty state");
    PcmRing ring(Stereo48(SampleEncoding::kS16), 64);
    YY_EXPECT_EQ(context, ring.capacity_frames(), 64ull);
    YY_EXPECT_EQ(context, ring.ReadableFrames(), 0ull);
    YY_EXPECT_EQ(context, ring.WritableFrames(), 64ull);

    YY_CASE(context, "write and read preserve bytes");
    const auto frames = Pattern(32, 4, 0x11);
    YY_EXPECT_EQ(context, ring.Write(frames.data(), 32), 32ull);
    YY_EXPECT_EQ(context, ring.ReadableFrames(), 32ull);
    std::vector<std::uint8_t> out(32 * 4);
    YY_EXPECT_EQ(context, ring.Read(out.data(), 32), 32ull);
    YY_EXPECT(context, out == frames);
    YY_EXPECT_EQ(context, ring.ReadableFrames(), 0ull);

    YY_CASE(context, "full ring refuses the remainder and counts the drop");
    YY_EXPECT_EQ(context, ring.Write(frames.data(), 32), 32ull);
    YY_EXPECT_EQ(context, ring.Write(frames.data(), 32), 32ull);   // fills exactly
    YY_EXPECT_EQ(context, ring.WritableFrames(), 0ull);
    const auto overflow = Pattern(8, 4, 0x22);
    YY_EXPECT_EQ(context, ring.Write(overflow.data(), 8), 0ull);
    RingStats stats = ring.Stats();
    YY_EXPECT_EQ(context, stats.dropped_frames, 8ull);
    YY_EXPECT_EQ(context, stats.overflow_write_calls, 1ull);
    // 32 frames written in case 1, 32 read back, then 32 + 32 more accepted before the ring is full.
    YY_EXPECT_EQ(context, stats.written_frames, 96ull);

    YY_CASE(context, "underrun is counted, not faked with silence");
    std::vector<std::uint8_t> big(64 * 4, 0);
    YY_EXPECT_EQ(context, ring.Read(big.data(), 64), 64ull);  // full drain
    YY_EXPECT_EQ(context, ring.Read(big.data(), 16), 0ull);
    stats = ring.Stats();
    YY_EXPECT_EQ(context, stats.underrun_frames, 16ull);
    YY_EXPECT_EQ(context, stats.underrun_read_calls, 1ull);

    YY_CASE(context, "partial read reports missing frames");
    YY_EXPECT_EQ(context, ring.Write(frames.data(), 4), 4ull);
    std::uint8_t small[10 * 4] = {};
    YY_EXPECT_EQ(context, ring.Read(small, 10), 4ull);
    stats = ring.Stats();
    YY_EXPECT_EQ(context, stats.underrun_frames, 22ull);  // 16 from the previous case + 6 now

    YY_CASE(context, "wraparound keeps frame order");
    PcmRing wrap(Stereo48(SampleEncoding::kS16), 8);
    std::vector<std::uint8_t> out_small(3 * 4);
    std::vector<std::uint8_t> expected;
    for (std::uint8_t round = 0; round < 6; ++round) {
        const auto chunk = Pattern(3, 4, static_cast<std::uint8_t>(round * 7));
        YY_EXPECT_EQ(context, wrap.Write(chunk.data(), 3), 3ull);
        YY_EXPECT_EQ(context, wrap.Read(out_small.data(), 3), 3ull);
        YY_EXPECT(context, out_small == chunk);
        expected.insert(expected.end(), chunk.begin(), chunk.end());
    }

    YY_CASE(context, "clear discards buffered frames without counting underrun");
    const std::uint64_t underrun_before = wrap.Stats().underrun_frames;
    YY_EXPECT_EQ(context, wrap.Write(frames.data(), 4), 4ull);
    wrap.Clear();
    YY_EXPECT_EQ(context, wrap.ReadableFrames(), 0ull);
    YY_EXPECT_EQ(context, wrap.Stats().underrun_frames, underrun_before);

    YY_CASE(context, "24-in-32 frames are byte exact");
    PcmRing wide(Stereo48(SampleEncoding::kS24In32), 16);
    const auto wide_frames = Pattern(16, 8, 0x5A);
    YY_EXPECT_EQ(context, wide.Write(wide_frames.data(), 16), 16ull);
    std::vector<std::uint8_t> wide_out(16 * 8);
    YY_EXPECT_EQ(context, wide.Read(wide_out.data(), 16), 16ull);
    YY_EXPECT(context, wide_out == wide_frames);

    YY_CASE(context, "invalid format yields a zero-capacity ring instead of a guess");
    PcmFormat invalid = Stereo48(SampleEncoding::kS16);
    invalid.encoding = SampleEncoding::kUnknown;
    PcmRing broken(invalid, 64);
    YY_EXPECT_EQ(context, broken.capacity_frames(), 0ull);
    YY_EXPECT_EQ(context, broken.Write(frames.data(), 4), 0ull);
    YY_EXPECT_EQ(context, broken.Read(out.data(), 4), 0ull);

    YY_CASE(context, "a faster writer loses frames instead of overwriting unread audio");
    {
        // Deterministic version of the producer/consumer race: the writer offers 1000 frames into a
        // 64 frame ring while nothing reads, so exactly 936 must be refused and counted.
        PcmRing pressure(Stereo48(SampleEncoding::kS16), 64);
        const auto offer = Pattern(1000, 4, 0x33);
        YY_EXPECT_EQ(context, pressure.Write(offer.data(), 1000), 64ull);
        const RingStats stats_pressure = pressure.Stats();
        YY_EXPECT_EQ(context, stats_pressure.written_frames, 64ull);
        YY_EXPECT_EQ(context, stats_pressure.dropped_frames, 936ull);
        YY_EXPECT_EQ(context, stats_pressure.overflow_write_calls, 1ull);
        YY_EXPECT_EQ(context, pressure.ReadableFrames(), 64ull);
        // The 64 frames that were accepted are still the first 64 of the offered batch.
        std::vector<std::uint8_t> drained(64 * 4);
        YY_EXPECT_EQ(context, pressure.Read(drained.data(), 64), 64ull);
        YY_EXPECT(context, std::memcmp(drained.data(), offer.data(), 64 * 4) == 0);
    }

    YY_CASE(context, "single writer and single reader stay consistent");
    PcmRing concurrent(Stereo48(SampleEncoding::kS16), 256);
    constexpr std::uint64_t kTotalFrames = 200000;
    std::uint64_t produced = 0;
    std::uint64_t consumed = 0;
    std::uint64_t requested = 0;
    bool order_ok = true;
    std::thread writer([&] {
        std::uint64_t frame_index = 0;
        while (frame_index < kTotalFrames) {
            const std::uint64_t remaining = kTotalFrames - frame_index;
            const std::uint64_t want = (frame_index % 37) + 1;
            const std::uint64_t count = want < remaining ? want : remaining;
            requested += count;
            // Every frame carries its own index so the reader can verify ordering.
            std::vector<std::uint8_t> data(static_cast<std::size_t>(count * 4));
            for (std::uint64_t i = 0; i < count; ++i) {
                const std::uint32_t value = static_cast<std::uint32_t>((frame_index + i) & 0xFFFFu);
                data[i * 4] = static_cast<std::uint8_t>(value & 0xFFu);
                data[i * 4 + 1] = static_cast<std::uint8_t>((value >> 8) & 0xFFu);
                data[i * 4 + 2] = static_cast<std::uint8_t>(value & 0xFFu);
                data[i * 4 + 3] = static_cast<std::uint8_t>((value >> 8) & 0xFFu);
            }
            // A full ring refuses the whole batch; the writer retries the same frame next round so
            // the total request never exceeds the remaining frames.
            const std::uint64_t written = concurrent.Write(data.data(), count);
            produced += written;
            frame_index += written;
            if (written == 0) std::this_thread::yield();
        }
    });
    std::vector<std::uint8_t> buffer(64 * 4);
    while (consumed < kTotalFrames) {
        const std::uint64_t got = concurrent.Read(buffer.data(), 64);
        for (std::uint64_t i = 0; i < got; ++i) {
            const std::uint32_t value = static_cast<std::uint32_t>(buffer[i * 4]) |
                                        (static_cast<std::uint32_t>(buffer[i * 4 + 1]) << 8);
            if (value != static_cast<std::uint32_t>((consumed + i) & 0xFFFFu)) order_ok = false;
        }
        consumed += got;
        if (got == 0) std::this_thread::yield();
    }
    writer.join();
    YY_EXPECT_EQ(context, produced, kTotalFrames);
    YY_EXPECT_EQ(context, consumed, kTotalFrames);
    YY_EXPECT(context, order_ok);
    stats = concurrent.Stats();
    YY_EXPECT_EQ(context, stats.read_frames, kTotalFrames);
    YY_EXPECT_EQ(context, stats.written_frames, kTotalFrames);
    YY_EXPECT(context, produced == kTotalFrames);
    // Whether the ring ever ran full here depends on scheduling, so the refusal path itself is
    // pinned by the deterministic case above; this case only proves the accounting stays exact.
    YY_EXPECT_EQ(context, requested, stats.written_frames + stats.dropped_frames);
}
