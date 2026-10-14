package yy.daniel.yyplayerforandroid.playback

import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.audio.*

class OutputFallbackPolicyTest {
    @Test fun fallbackRequiresCompatiblePolicyAndSameLiveRoute() {
        assertTrue(OutputFallbackPolicy.allowed(SharingRequest.PREFER_EXCLUSIVE, TransparencyRequest.PREFER_UNPROCESSED, 7, 7, true))
        assertFalse(OutputFallbackPolicy.allowed(SharingRequest.STRICT_EXCLUSIVE, TransparencyRequest.COMPATIBLE, 7, 7, true))
        assertFalse(OutputFallbackPolicy.allowed(SharingRequest.SHARED, TransparencyRequest.STRICT_UNPROCESSED, 7, 7, true))
        assertFalse(OutputFallbackPolicy.allowed(SharingRequest.SHARED, TransparencyRequest.COMPATIBLE, 7, 8, true))
        assertFalse(OutputFallbackPolicy.allowed(SharingRequest.SHARED, TransparencyRequest.COMPATIBLE, 7, 7, false))
        assertFalse(OutputFallbackPolicy.allowed(SharingRequest.SHARED, TransparencyRequest.COMPATIBLE, null, null, true))
    }
}
