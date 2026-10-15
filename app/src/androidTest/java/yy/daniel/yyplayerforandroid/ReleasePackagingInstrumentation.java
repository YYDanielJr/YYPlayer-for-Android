package yy.daniel.yyplayerforandroid;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Surface;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Exercises the actual minified APK without depending on its renamed Kotlin/AndroidX classes. */
public final class ReleasePackagingInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            check((context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0,
                    "Install the actual release APK before running this instrumentation");
            check("0.1.7".equals(context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName), "versionName");
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            try (android.os.ParcelFileDescriptor command = getUiAutomation().executeShellCommand(
                    "am start -W -n " + context.getPackageName() + "/yy.daniel.yyplayerforandroid.MainActivity");
                 InputStream output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)) {
                byte[] drain = new byte[1024];
                while (output.read(drain) != -1) { /* Foreground launcher on HyperOS. */ }
            }
            Activity activity = startActivitySync(launch);
            check(activity != null && !activity.isFinishing(), "launcher");
            runOnMainSync(activity::finish);
            waitForIdleSync();

            Class<?> audio = Class.forName("yy.daniel.yyplayerforandroid.audio.NativeAudioCore$Bridge",
                    true, context.getClassLoader());
            java.lang.reflect.Field singleton = audio.getDeclaredField("INSTANCE");
            singleton.setAccessible(true);
            Method version = audio.getDeclaredMethod("nativeBridgeVersion");
            version.setAccessible(true);
            check(Integer.valueOf(2).equals(version.invoke(singleton.get(null))), "audio JNI version");

            verifyMpv(context);
            result.putString("stream", "PASS: 3 release checks (version/launcher, audio JNI, mpv decode)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyMpv(Context context) throws Exception {
        Class<?> mpv = Class.forName("yy.daniel.yyplayerforandroid.playback.MpvNative",
                true, context.getClassLoader());
        Object singleton = mpv.getDeclaredField("INSTANCE").get(null);
        long handle = (Long) mpv.getDeclaredMethod("create", Context.class, Surface.class)
                .invoke(singleton, context, null);
        check(handle > 0, "mpv create");
        File fixture = new File(context.getCacheDir(), "release-packaging-fixture.mp4");
        android.os.ParcelFileDescriptor lease = null;
        try {
            Method request = mpv.getDeclaredMethod("request", long.class, byte[][].class);
            request.setAccessible(true);
            try (InputStream input = getContext().getAssets().open("v15-video.mp4");
                 FileOutputStream output = new FileOutputStream(fixture)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            call(request, singleton, handle, "set", "volume", "0");
            call(request, singleton, handle, "set", "pause", "yes");
            lease = android.os.ParcelFileDescriptor.open(fixture, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            call(request, singleton, handle, "loadfile", "fd://" + lease.getFd(), "replace");
            JSONObject snapshot = new JSONObject();
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (System.nanoTime() < deadline) {
                snapshot = new JSONObject(call(request, singleton, handle, "snapshot"));
                if (snapshot.optBoolean("loaded") && snapshot.optJSONObject("video-params") != null) break;
                Thread.sleep(50);
            }
            check(snapshot.optBoolean("loaded"), "mpv loaded: " + snapshot);
            check(snapshot.getJSONObject("video-params").getInt("dw") == 320, "decoded width");
            check("aaudio".equals(snapshot.optString("current-ao")), "actual AAudio output: " + snapshot);
        } finally {
            mpv.getDeclaredMethod("close", long.class).invoke(singleton, handle);
            if (lease != null) lease.close();
            if (fixture.exists() && !fixture.delete()) throw new IllegalStateException("Fixture cleanup failed");
        }
    }

    private static String call(Method request, Object singleton, long handle, String... args) throws Exception {
        byte[][] bytes = new byte[args.length][];
        for (int i = 0; i < args.length; i++) bytes[i] = args[i].getBytes(StandardCharsets.UTF_8);
        return new String((byte[]) request.invoke(singleton, handle, bytes), StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
