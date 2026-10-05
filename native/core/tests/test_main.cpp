#include <cstdio>

#include "test_support.h"

int main() {
    yy::test::Context context;
    yy::test::RunFormatTests(context);
    yy::test::RunJsonTests(context);
    yy::test::RunPcmTests(context);
    yy::test::RunPolicyTests(context);
    yy::test::RunRingTests(context);

    std::printf("\nchecks=%d failures=%d\n", context.checks(), context.failures());
    if (context.failures() == 0) {
        std::printf("RESULT OK\n");
        return 0;
    }
    std::printf("RESULT FAILED\n");
    return 1;
}
