package io.github.dawidkrynski.mediagrab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramSessionTest {
    @Test fun withoutSessionCookieThereIsNoLogin() {
        assertNull(InstagramSession.netscape("csrftoken=a; mid=b", 100))
        assertNull(InstagramSession.netscape("sessionid=; mid=b", 100))
    }

    @Test fun writesNetscapeFormatKeepingValuesWithEquals() {
        assertEquals(
            "# Netscape HTTP Cookie File\n" +
                ".instagram.com\tTRUE\t/\tTRUE\t100\tsessionid\ta=b\n" +
                ".instagram.com\tTRUE\t/\tTRUE\t100\tmid\tc\n",
            InstagramSession.netscape("sessionid=a=b; mid=c", 100),
        )
    }
}
