package io.github.dawidkrynski.mediagrab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {
    @Test fun findsLinkInsideSharedSentence() {
        assertEquals(
            "https://vm.tiktok.com/abc/",
            UrlRules.extractUrl("Zobacz ten film: https://vm.tiktok.com/abc/."),
        )
    }

    @Test fun rejectsTextWithoutLinkAndLinkWithCredentials() {
        assertNull(UrlRules.extractUrl("nic tu nie ma"))
        assertNull(UrlRules.extractUrl(null))
        assertNull(UrlRules.extractUrl("https://user:pass@example.com/a"))
    }

    @Test fun watchUrlDropsPlaylistParameters() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc&t=5",
            UrlRules.extractUrl("https://www.youtube.com/watch?v=abc&list=PL1&index=2&t=5"),
        )
    }

    @Test fun collectionsAreDetected() {
        listOf(
            "https://www.youtube.com/playlist?list=PL1",
            "https://www.youtube.com/@kanal",
            "https://www.instagram.com/nazwa/",
            "https://www.tiktok.com/@nazwa",
            "https://x.com/nazwa",
            "https://www.reddit.com/r/pics/",
        ).forEach { assertTrue(it, UrlRules.isCollection(it)) }
    }

    @Test fun singlePostsAreNotCollections() {
        listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://www.youtube.com/shorts/abc",
            "https://www.instagram.com/p/abc/?igsh=x",
            "https://www.instagram.com/nazwa/p/abc/",
            "https://www.instagram.com/reel/abc/",
            "https://www.instagram.com/reels/abc/",
            "https://www.instagram.com/stories/nazwa/123/",
            "https://www.tiktok.com/@nazwa/video/123",
            "https://www.tiktok.com/@nazwa/photo/123",
            "https://vm.tiktok.com/abc/",
            "https://x.com/nazwa/status/123",
            "https://www.reddit.com/r/pics/comments/abc/t/",
            "https://www.reddit.com/r/pics/s/abc",
            "https://redd.it/abc",
            "https://vimeo.com/123",
        ).forEach { assertFalse(it, UrlRules.isCollection(it)) }
    }

    @Test fun instagramPostsUseGalleryDlAndReelsYtDlp() {
        assertEquals(Engine.GALLERY_DL, UrlRules.engine("https://www.instagram.com/p/abc/"))
        assertEquals(Engine.GALLERY_DL, UrlRules.engine("https://www.instagram.com/nazwa/p/abc/"))
        assertEquals(Engine.YT_DLP, UrlRules.engine("https://www.instagram.com/reel/abc/"))
        assertEquals(Engine.YT_DLP, UrlRules.engine("https://youtu.be/abc"))
    }

    @Test fun sourceNamesFolders() {
        assertEquals("youtube", UrlRules.source("https://m.youtube.com/watch?v=a"))
        assertEquals("x", UrlRules.source("https://twitter.com/a/status/1"))
        assertEquals("vimeo-com", UrlRules.source("https://www.vimeo.com/1"))
    }
}
