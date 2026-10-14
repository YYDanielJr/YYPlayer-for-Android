package yy.daniel.yyplayerforandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Locks the Kotlin side of the JNI contract.
 *
 * Kotlin compiles the nested `Bridge` object into its own top-level class, and every native
 * declaration lives in it - the ring and PCM entry points included, even though the outer
 * `NativeAudioCore` is what callers use. The native side registers all of them on that class, so a
 * declaration that moves, or a function added without a matching registration, stops resolving
 * silently: `System.loadLibrary` still succeeds and the Kotlin wrapper reports "native unavailable".
 * This test fails on the host instead of on a device.
 *
 * `loadLibrary` cannot run in a host JVM, so the check is reflection over the declarations. The
 * classes are loaded without running initializers, because `Bridge`'s initializer loads the library.
 */
class NativeAudioCoreContractTest {

    private fun load(name: String): Class<*> = Class.forName(name, false, javaClass.classLoader)

    private val coreClass = load("yy.daniel.yyplayerforandroid.audio.NativeAudioCore")
    private val bridgeClass = load("yy.daniel.yyplayerforandroid.audio.NativeAudioCore\$Bridge")

    private fun externalNames(type: Class<*>): List<String> =
        type.declaredMethods
            .filter { Modifier.isNative(it.modifiers) }
            .map { it.name }
            .sorted()

    @Test
    fun everyNativeEntryPointLivesOnTheNestedBridgeObject() {
        // The exact set the C++ registration table lists. Adding a Kotlin declaration without
        // registering it in native/jni/yy_jni.cpp makes this test fail, which is intended.
        assertEquals(
            "Bridge must declare every native entry point, because that is where they are registered",
            listOf(
                "convertPcm",
                "createRing",
                "nativeBridgeVersion",
                "nativeEvaluateOutputPolicy",
                "nativeIsPristineConversion",
                "releaseRing",
                "ringClear",
                "ringRead",
                "ringStats",
                "ringWrite",
            ),
            externalNames(bridgeClass),
        )
    }

    @Test
    fun outerObjectDeclaresNoNativeMethodsOfItsOwn() {
        // The public API on NativeAudioCore is plain Kotlin wrapping the bridge. A native method
        // appearing here would have to be registered on the outer class as well, and would otherwise
        // resolve to nothing while looking like a supported feature.
        assertEquals(
            "NativeAudioCore itself must not declare native methods",
            emptyList<String>(),
            externalNames(coreClass),
        )
    }

    @Test
    fun expectedVersionMatchesTheRegisteredBridge() {
        // NativeAudioCore refuses to claim availability when the native version differs, so the two
        // constants have to move together. The value itself is asserted on the device.
        val field = coreClass.getDeclaredField("EXPECTED_VERSION")
        field.isAccessible = true
        assertEquals(2, field.getInt(null))
    }

    @Test
    fun ringEntryPointsUseDirectBuffersAndPlainScalarsOnly() {
        // Direct buffers and scalars are the only things the bridge knows how to read; a Java array or
        // a wrapper type would need a different JNI signature than the registered one.
        val write = bridgeClass.getDeclaredMethod(
            "ringWrite",
            Long::class.javaPrimitiveType,
            java.nio.ByteBuffer::class.java,
            Int::class.javaPrimitiveType,
        )
        assertEquals(Int::class.javaPrimitiveType, write.returnType)
        val stats = bridgeClass.getDeclaredMethod("ringStats", Long::class.javaPrimitiveType)
        assertTrue("ringStats returns a long array", stats.returnType == LongArray::class.java)
    }
}
