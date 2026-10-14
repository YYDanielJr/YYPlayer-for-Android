#include "test_support.h"
#include "yy/core/format.h"

using namespace yy::core;

void yy::test::RunFormatTests(Context& context) {
    context.Begin("format");

    YY_CASE(context, "sample widths");
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kU8), 1u);
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kS16), 2u);
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kS24_3), 3u);
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kS24In32), 4u);
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kS32), 4u);
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kF32), 4u);
    YY_EXPECT_EQ(context, BytesPerSample(SampleEncoding::kUnknown), kBytesPerSampleUnknown);

    YY_CASE(context, "effective bits are not container bits");
    YY_EXPECT_EQ(context, EffectiveBits(SampleEncoding::kS24_3), 24u);
    YY_EXPECT_EQ(context, EffectiveBits(SampleEncoding::kS24In32), 24u);
    YY_EXPECT_EQ(context, EffectiveBits(SampleEncoding::kS32), 32u);
    YY_EXPECT_EQ(context, EffectiveBits(SampleEncoding::kUnknown), 0u);

    YY_CASE(context, "rate range");
    YY_EXPECT(context, IsSupportedSampleRate(44100));
    YY_EXPECT(context, IsSupportedSampleRate(48000));
    YY_EXPECT(context, IsSupportedSampleRate(96000));
    YY_EXPECT(context, IsSupportedSampleRate(192000));
    YY_EXPECT(context, IsSupportedSampleRate(768000));
    YY_EXPECT(context, !IsSupportedSampleRate(0));
    YY_EXPECT(context, !IsSupportedSampleRate(-44100));
    YY_EXPECT(context, !IsSupportedSampleRate(1000000));

    YY_CASE(context, "plausible format");
    PcmFormat stereo48;
    stereo48.sample_rate = 48000;
    stereo48.channel_count = 2;
    stereo48.encoding = SampleEncoding::kS16;
    YY_EXPECT(context, IsPlausibleFormat(stereo48));

    PcmFormat unknown_encoding = stereo48;
    unknown_encoding.encoding = SampleEncoding::kUnknown;
    YY_EXPECT(context, !IsPlausibleFormat(unknown_encoding));

    PcmFormat zero_channels = stereo48;
    zero_channels.channel_count = 0;
    YY_EXPECT(context, !IsPlausibleFormat(zero_channels));

    PcmFormat too_many_channels = stereo48;
    too_many_channels.channel_count = kChannelCountMax + 1;
    YY_EXPECT(context, !IsPlausibleFormat(too_many_channels));

    PcmFormat correct_mask = stereo48;
    correct_mask.channel_mask = 0x3;
    YY_EXPECT(context, IsPlausibleFormat(correct_mask));

    PcmFormat partial_mask = stereo48;
    partial_mask.channel_mask = 0x1;  // claims one channel while declaring two
    YY_EXPECT(context, !IsPlausibleFormat(partial_mask));

    YY_CASE(context, "byte and frame math");
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo48, 0), 0ull);
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo48, 1), 4ull);
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo48, 1000), 4000ull);
    // 24-in-32 stereo: the container is 4 bytes even though only 24 bits are significant.
    PcmFormat stereo24in32 = stereo48;
    stereo24in32.encoding = SampleEncoding::kS24In32;
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo24in32, 1000), 8000ull);
    PcmFormat stereo24_3 = stereo48;
    stereo24_3.encoding = SampleEncoding::kS24_3;
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo24_3, 1000), 6000ull);

    YY_EXPECT_EQ(context, FramesInBytes(stereo24_3, 6000), 1000ull);
    YY_EXPECT_EQ(context, FramesInBytes(stereo24_3, 6001), 1000ull);  // partial frame truncated
    YY_EXPECT_EQ(context, FramesInBytes(stereo48, 3), 0ull);

    YY_CASE(context, "byte math overflow");
    const std::uint64_t huge = static_cast<std::uint64_t>(-1);
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo48, huge), huge);
    YY_EXPECT_EQ(context, ByteSizeOfFrames(stereo48, huge / 4 + 1), huge);
    YY_EXPECT_EQ(context, ByteSizeOfFrames(unknown_encoding, 4), huge);

    YY_CASE(context, "channel masks");
    YY_EXPECT_EQ(context, MaskForChannels(1), 0x1u);
    YY_EXPECT_EQ(context, MaskForChannels(2), 0x3u);
    YY_EXPECT_EQ(context, MaskForChannels(6), 0x3Fu);
    YY_EXPECT_EQ(context, MaskForChannels(0), 0u);
    YY_EXPECT_EQ(context, MaskForChannels(-1), 0u);
    YY_EXPECT_EQ(context, MaskForChannels(kChannelCountMax + 1), 0u);

    YY_CASE(context, "encoding names are stable");
    YY_EXPECT_EQ(context, std::string(EncodingName(SampleEncoding::kS24In32)), std::string("s24_in32"));
    YY_EXPECT_EQ(context, std::string(EncodingName(SampleEncoding::kUnknown)), std::string("unknown"));
}
