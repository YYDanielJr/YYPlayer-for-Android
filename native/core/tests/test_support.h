// Minimal dependency-free test harness: the portable core is also built and run on a real device
// through adb, where no GoogleTest binary is available.
#ifndef YY_TEST_SUPPORT_H
#define YY_TEST_SUPPORT_H

#include <cstdint>
#include <cstdio>
#include <string>
#include <type_traits>

namespace yy::test {

class Context {
 public:
    void Begin(const char* suite) {
        suite_ = suite;
        std::printf("[suite] %s\n", suite);
    }
    void Check(bool condition, const char* expression, const char* file, int line) {
        ++checks_;
        if (condition) return;
        ++failures_;
        std::printf("  FAIL %s:%d  %s\n", file, line, expression);
    }
    template <typename A, typename B>
    void CheckEq(const A& actual, const B& expected, const char* expression,
                 const char* file, int line) {
        ++checks_;
        if (actual == expected) return;
        ++failures_;
        std::printf("  FAIL %s:%d  %s  (actual != expected)\n", file, line, expression);
        PrintValue("actual", actual);
        PrintValue("expected", expected);
    }
    void Case(const char* name) { std::printf("  case %s\n", name); }

    int failures() const { return failures_; }
    int checks() const { return checks_; }

 private:
    static void PrintText(const char* label, const char* text) {
        std::printf("       %s=%s\n", label, text);
    }
    static void PrintText(const char* label, const std::string& text) {
        std::printf("       %s=%s\n", label, text.c_str());
    }
    template <typename T>
    static void PrintValue(const char* label, const T& value) {
        if constexpr (std::is_same<T, const char*>::value || std::is_same<T, char*>::value) {
            PrintText(label, value == nullptr ? "(null)" : value);
        } else if constexpr (std::is_same<T, std::string>::value) {
            PrintText(label, value);
        } else if constexpr (std::is_same<T, bool>::value) {
            std::printf("       %s=%s\n", label, value ? "true" : "false");
        } else if constexpr (std::is_enum<T>::value) {
            std::printf("       %s=enum(%lld)\n", label,
                        static_cast<long long>(static_cast<std::int64_t>(value)));
        } else if constexpr (std::is_integral<T>::value && std::is_signed<T>::value) {
            std::printf("       %s=%lld\n", label, static_cast<long long>(value));
        } else if constexpr (std::is_integral<T>::value) {
            std::printf("       %s=%llu\n", label, static_cast<unsigned long long>(value));
        } else {
            std::printf("       %s=<unprintable>\n", label);
        }
    }

    const char* suite_ = "";
    int checks_ = 0;
    int failures_ = 0;
};

void RunFormatTests(Context& context);
void RunJsonTests(Context& context);
void RunPcmTests(Context& context);
void RunPolicyTests(Context& context);
void RunRingTests(Context& context);

}  // namespace yy::test

#define YY_EXPECT(context, condition) (context).Check((condition), #condition, __FILE__, __LINE__)
#define YY_EXPECT_EQ(context, actual, expected) \
    (context).CheckEq((actual), (expected), #actual " == " #expected, __FILE__, __LINE__)
#define YY_CASE(context, name) (context).Case(name)

#endif  // YY_TEST_SUPPORT_H
