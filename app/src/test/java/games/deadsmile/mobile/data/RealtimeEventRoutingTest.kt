package games.deadsmile.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeEventRoutingTest {
    @Test fun wishlistEventsRefreshWishlist() {
        assertEquals(RealtimeEventTarget.Wishlist, realtimeEventTarget("wishlist.updated"))
        assertEquals(RealtimeEventTarget.Wishlist, realtimeEventTarget("WISHLIST.UPDATED"))
    }

    @Test fun accountAndAchievementEventsRefreshAccount() {
        assertEquals(RealtimeEventTarget.Account, realtimeEventTarget("account.profile.updated"))
        assertEquals(RealtimeEventTarget.Account, realtimeEventTarget("achievement.unlocked"))
    }

    @Test fun publicEventsRefreshContent() {
        assertEquals(RealtimeEventTarget.PublicContent, realtimeEventTarget("game.published"))
        assertEquals(RealtimeEventTarget.PublicContent, realtimeEventTarget("news.updated"))
        assertEquals(RealtimeEventTarget.PublicContent, realtimeEventTarget("video.published"))
    }

    @Test fun handshakeAndUnknownEventsAreIgnored() {
        assertEquals(RealtimeEventTarget.Ignore, realtimeEventTarget("connected"))
        assertEquals(RealtimeEventTarget.Ignore, realtimeEventTarget(""))
        assertEquals(RealtimeEventTarget.Ignore, realtimeEventTarget("unknown.event"))
    }
}
