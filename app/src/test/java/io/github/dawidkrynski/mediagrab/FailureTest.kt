package io.github.dawidkrynski.mediagrab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureTest {
    @Test fun instagramEmptyResponseIsAccessNotLogin() {
        val stderr = "ERROR: [Instagram] abc: Instagram sent an empty media response. Check if this post is accessible " +
            "in your browser without being logged-in. If it is not, then use --cookies-from-browser"
        assertEquals(Failure.ACCESS, Failure.classify(stderr))
    }

    @Test fun botCheckIsAccessNotLogin() {
        assertEquals(Failure.ACCESS, Failure.classify("ERROR: Sign in to confirm you're not a bot"))
    }

    @Test fun rateLimitWinsOverOtherWords() {
        assertEquals(Failure.RATE_LIMIT, Failure.classify("HTTP Error 429: Too Many Requests. Please log in"))
    }

    @Test fun warningsDoNotOverrideTheError() {
        val stderr = "WARNING: [Reddit] HTTP Error 403: Forbidden, install the required dependencies\n" +
            "ERROR: [Reddit] 1: Account authentication is required. Use --cookies"
        assertEquals(Failure.LOGIN, Failure.classify(stderr))
    }

    @Test fun loginAndOtherKinds() {
        assertEquals(Failure.LOGIN, Failure.classify("[instagram][error] HTTP redirect to login page"))
        assertEquals(Failure.UNSUPPORTED, Failure.classify("ERROR: Unsupported URL: https://a.b/"))
        assertEquals(Failure.NO_MEDIA, Failure.classify("ERROR: [twitter] 1: No video could be found in this tweet"))
        assertEquals(Failure.UNAVAILABLE, Failure.classify("ERROR: Video unavailable"))
        assertEquals(Failure.NETWORK, Failure.classify("Unable to download webpage: timed out"))
        assertEquals(Failure.UNKNOWN, Failure.classify("coś innego"))
    }

    @Test fun onlyMissingVideoTriesOtherEngine() {
        assertTrue(Failure.UNSUPPORTED.triesOtherEngine)
        assertTrue(Failure.NO_MEDIA.triesOtherEngine)
        assertFalse(Failure.LOGIN.triesOtherEngine)
        assertFalse(Failure.RATE_LIMIT.triesOtherEngine)
        assertFalse(Failure.NETWORK.triesOtherEngine)
    }
}
