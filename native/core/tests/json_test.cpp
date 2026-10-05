#include <string>

#include "test_support.h"
#include "yy/core/json.h"

using namespace yy::core::json;

void yy::test::RunJsonTests(Context& context) {
    context.Begin("json");

    const std::string document =
        "{\"format\":{\"sampleRate\":48000,\"channels\":2,\"encoding\":\"s24_in32\","
        "\"channelMask\":3},\"route\":{\"deviceId\":42,\"sink\":\"audiotrack_system\","
        "\"supportsBitPerfect\":true,\"profiles\":[{\"encoding\":\"s24_in32\","
        "\"sampleRate\":48000,\"channelMask\":3,\"bitPerfect\":true},{\"encoding\":\"s16\","
        "\"sampleRate\":44100,\"channelMask\":3,\"bitPerfect\":false}]},"
        "\"processing\":{\"softwareVolume\":false},\"sharing\":\"shared\","
        "\"transparency\":\"prefer_unprocessed\",\"routeSelected\":true}";

    YY_CASE(context, "integers are range checked");
    std::int64_t value = 0;
    YY_EXPECT(context, TryGetInt(document, "format.sampleRate", 0, 1000000, &value));
    YY_EXPECT_EQ(context, value, 48000);
    YY_EXPECT(context, TryGetInt(document, "route.deviceId", 0, 100000, &value));
    YY_EXPECT_EQ(context, value, 42);
    // Outside the accepted range the value must be treated as unknown, not clamped.
    YY_EXPECT(context, !TryGetInt(document, "format.sampleRate", 0, 100, &value));
    YY_EXPECT(context, !TryGetInt(document, "missing", 0, 1000000, &value));
    YY_EXPECT(context, !TryGetInt(document, "format", 0, 1000000, &value));

    YY_CASE(context, "booleans and strings");
    bool flag = false;
    YY_EXPECT(context, TryGetBool(document, "route.supportsBitPerfect", &flag));
    YY_EXPECT(context, flag);
    YY_EXPECT(context, TryGetBool(document, "routeSelected", &flag));
    YY_EXPECT(context, flag);
    YY_EXPECT(context, TryGetBool(document, "processing.softwareVolume", &flag));
    YY_EXPECT(context, !flag);
    YY_EXPECT(context, !TryGetBool(document, "missing", &flag));
    std::string text;
    YY_EXPECT(context, TryGetString(document, "sharing", &text));
    YY_EXPECT_EQ(context, text, std::string("shared"));
    YY_EXPECT(context, TryGetString(document, "format.encoding", &text));
    YY_EXPECT_EQ(context, text, std::string("s24_in32"));
    YY_EXPECT(context, !TryGetString(document, "route.deviceId", &text));

    YY_CASE(context, "nested objects are addressed by path");
    std::string format;
    YY_EXPECT(context, TryGetObject(document, "format", &format));
    YY_EXPECT(context, TryGetInt(format, "channels", 0, 32, &value));
    YY_EXPECT_EQ(context, value, 2);
    YY_EXPECT(context, TryGetBool(format, "encoding", &flag) == false);
    std::string route;
    YY_EXPECT(context, TryGetObject(document, "route", &route));
    YY_EXPECT(context, TryGetInt(route, "deviceId", 0, 100000, &value));
    YY_EXPECT_EQ(context, value, 42);
    YY_EXPECT(context, !TryGetObject(document, "sharing", &format));

    YY_CASE(context, "arrays of objects");
    std::string profiles;
    std::int32_t count = 0;
    YY_EXPECT(context, TryGetObjectArray(document, "route.profiles", &profiles, &count));
    YY_EXPECT_EQ(context, count, 2);
    YY_EXPECT(context, profiles.find("bitPerfect") != std::string::npos);
    YY_EXPECT(context, TryGetObjectArray(document, "route.profiles", &profiles, &count));
    YY_EXPECT(context, TryGetObjectArray("{\"profiles\":[]}", "profiles", &profiles, &count));
    YY_EXPECT_EQ(context, count, 0);

    YY_CASE(context, "a member is read from the addressed object only");
    // sampleRate lives inside "format"; addressing it from the root without the path must fail
    // rather than silently reach into a child object.
    YY_EXPECT(context, !TryGetInt(document, "sampleRate", 0, 1000000, &value));
    YY_EXPECT(context, !TryGetBool(document, "bitPerfect", &flag));
    // A nested member never shadows a top-level one, and both remain addressable.
    const std::string shadowed = "{\"a\":{\"sampleRate\":96000},\"sampleRate\":44100}";
    YY_EXPECT(context, TryGetInt(shadowed, "sampleRate", 0, 1000000, &value));
    YY_EXPECT_EQ(context, value, 44100);
    YY_EXPECT(context, TryGetInt(shadowed, "a.sampleRate", 0, 1000000, &value));
    YY_EXPECT_EQ(context, value, 96000);

    YY_CASE(context, "string values are not mistaken for member names");
    const std::string decoys =
        "{\"label\":\"sampleRate\",\"sharing\":\"shared\",\"note\":\"{ \\\"deviceId\\\": 7 }\","
        "\"deviceId\":3}";
    YY_EXPECT(context, TryGetString(decoys, "label", &text));
    YY_EXPECT_EQ(context, text, std::string("sampleRate"));
    YY_EXPECT(context, TryGetInt(decoys, "deviceId", 0, 100000, &value));
    YY_EXPECT_EQ(context, value, 3);
    YY_EXPECT(context, TryGetString(decoys, "sharing", &text));
    YY_EXPECT_EQ(context, text, std::string("shared"));
    YY_EXPECT(context, TryGetString(decoys, "note", &text));
    YY_EXPECT_EQ(context, text, std::string("{ \\\"deviceId\\\": 7 }"));

    YY_CASE(context, "braces inside strings do not confuse the scanner");
    const std::string tricky = "{\"label\":\"a{b}c[d]\",\"sampleRate\":44100}";
    YY_EXPECT(context, TryGetInt(tricky, "sampleRate", 0, 1000000, &value));
    YY_EXPECT_EQ(context, value, 44100);
    YY_EXPECT(context, TryGetString(tricky, "label", &text));
    YY_EXPECT_EQ(context, text, std::string("a{b}c[d]"));

    YY_CASE(context, "an escaped quote inside a string is handled");
    const std::string escaped_value = "{\"label\":\"a\\\"b\",\"sampleRate\":44100}";
    YY_EXPECT(context, TryGetInt(escaped_value, "sampleRate", 0, 1000000, &value));
    YY_EXPECT_EQ(context, value, 44100);

    YY_CASE(context, "malformed input is refused, not guessed");
    YY_EXPECT(context, !TryGetInt("{\"sampleRate\":}", "sampleRate", 0, 1000000, &value));
    YY_EXPECT(context, !TryGetObject("{\"format\":42}", "format", &format));
    YY_EXPECT(context, !TryGetObjectArray("{\"profiles\":7}", "profiles", &profiles, &count));
    YY_EXPECT_EQ(context, count, 0);
    YY_EXPECT(context, !TryGetString("{\"sharing\":42}", "sharing", &text));
    YY_EXPECT(context, !TryGetInt("not json", "sampleRate", 0, 1000000, &value));
    YY_EXPECT(context, !TryGetInt("{}", "sampleRate", 0, 1000000, &value));
    YY_EXPECT(context, !TryGetInt("{\"a..b\":1}", "a..b", 0, 10, &value));

    YY_CASE(context, "writers produce parseable fragments");
    std::string out;
    AppendKey(&out, "code");
    AppendString(&out, "ok");
    out.push_back(',');
    AppendKey(&out, "count");
    AppendInt(&out, 12);
    out.push_back(',');
    AppendKey(&out, "accepted");
    AppendBool(&out, true);
    YY_EXPECT_EQ(context, out, std::string("\"code\":\"ok\",\"count\":12,\"accepted\":true"));
    const std::string wrapped = "{" + out + "}";
    YY_EXPECT(context, TryGetString(wrapped, "code", &text));
    YY_EXPECT_EQ(context, text, std::string("ok"));
    YY_EXPECT(context, TryGetInt(wrapped, "count", 0, 100, &value));
    YY_EXPECT_EQ(context, value, 12);

    YY_CASE(context, "writer escapes quotes and control characters");
    std::string escaped;
    AppendString(&escaped, "a\"b\\c");
    YY_EXPECT_EQ(context, escaped, std::string("\"a\\\"b\\\\c\""));
    std::string control;
    AppendString(&control, std::string("x\x01y"));
    YY_EXPECT_EQ(context, control, std::string("\"x\\u0001y\""));
}
