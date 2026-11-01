package io.github.dawidkrynski.mediagrab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublisherTest {
    @Test fun finishedFilesPublish() {
        listOf("Film [abc].mp4", "399_400.jpg", "a.b.c.webp", "Odc. 5.mp4").forEach { assertTrue(it, Publisher.isFinished(it)) }
    }

    @Test fun partialFilesAndUnmergedStreamsDoNot() {
        listOf("a.mp4.part", "a.ytdl", "a.f137.mp4", "a.f140.m4a", "a.f251-drc.webm").forEach { assertFalse(it, Publisher.isFinished(it)) }
    }
}
