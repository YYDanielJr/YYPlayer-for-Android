# Native build notes

The portable core lives in `native/core`. It is plain C++17 with no Android, JNI, mpv or FFmpeg
types, so the same sources build for a host (tests) and for Android (the app).

## Host build and tests

```powershell
E:\DeveloperTools\cmake\bin\cmake.exe -S native -B native\build-host -G Ninja `
  -DCMAKE_MAKE_PROGRAM=E:/DeveloperTools/ninja/ninja.exe `
  -DCMAKE_BUILD_TYPE=Release `
  -DCMAKE_CXX_COMPILER=E:/DeveloperTools/LLVM/bin/clang++.exe
E:\DeveloperTools\cmake\bin\cmake.exe --build native\build-host
E:\DeveloperTools\cmake\bin\ctest.exe --test-dir native\build-host --output-on-failure
```

`native\build*` trees are generated and ignored by git.

## Android cross-compile (both ABIs)

The app builds this tree itself through `externalNativeBuild`; these commands reproduce it outside
Gradle. Quote the `-DCMAKE_TOOLCHAIN_FILE=...` argument, otherwise CMake may lose it.

```powershell
$cmake = 'E:\DeveloperTools\AndroidSDK\cmake\3.22.1\bin\cmake.exe'
$tc = 'E:\DeveloperTools\AndroidSDK\ndk\28.2.13676358\build\cmake\android.toolchain.cmake'
foreach ($abi in @('arm64-v8a','x86_64')) {
  & $cmake -S native -B "native\build-android-$abi" -G Ninja `
    -DCMAKE_MAKE_PROGRAM=E:/DeveloperTools/ninja/ninja.exe `
    "-DCMAKE_TOOLCHAIN_FILE=$tc" -DANDROID_ABI=$abi -DANDROID_PLATFORM=android-26 `
    -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DYY_BUILD_TESTS=OFF -DYY_BUILD_JNI=ON
  & $cmake --build "native\build-android-$abi"
}
```

The core test binary also builds for Android (`-DYY_BUILD_TESTS=ON`), which is how the same
assertions get run on a real device:

```powershell
adb push native\build-android-arm64-v8a\yy_core_tests /data/local/tmp/yy_core_tests
adb shell chmod 755 /data/local/tmp/yy_core_tests
adb shell /data/local/tmp/yy_core_tests
```

## Layout

| Path | Contents |
| --- | --- |
| `core/include/yy/core/format.h` | PCM format description, byte/frame math with overflow checks |
| `core/include/yy/core/pcm.h` | Pristine (bit-preserving) sample conversion |
| `core/include/yy/core/ring.h` | Lock-free single-producer/single-consumer PCM ring |
| `core/include/yy/core/policy.h` | Output decision: sharing AND transparency, refusals carry reasons |
| `core/include/yy/core/json.h` | Small JSON reader/writer for the control bridge |
| `core/tests/` | Host and device tests; no GoogleTest dependency |
| `jni/yy_jni.cpp` | JNI bridge for `yy.daniel.yyplayerforandroid.audio.NativeAudioCore` |
