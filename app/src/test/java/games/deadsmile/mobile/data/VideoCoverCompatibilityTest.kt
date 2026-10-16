package games.deadsmile.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCoverCompatibilityTest {
    private val videoId = "123e4567-e89b-12d3-a456-426614174000"
    private val thumbnail = "https://api-ust8.onrender.com/media/trailer-frame.jpg"
    private val cover = "https://api-ust8.onrender.com/media/game-cover.jpg"

    @Test fun videoThumbnailAndAssociatedGameCoverStaySeparate() {
        val video = JSONObject(
            """
            {"items":[{"id":"$videoId","title":"Starfall Trailer","thumbnail_pt_br":"/media/trailer-frame.jpg","thumbnail":"/media/trailer-frame.jpg",
              "game_cover_image":"/media/game-cover.jpg","video_url":"https://www.youtube.com/watch?v=abc123"}]}
            """.trimIndent()
        ).feed("items").single()

        assertEquals(thumbnail, video.image)
        assertEquals(cover, video.gameCoverImage)
    }

    @Test fun detailPayloadWithoutCoverKeepsCoverFromVideoFeed() {
        val fallback = FeedItem(
            id = videoId, title = "Starfall Trailer", summary = "", image = thumbnail,
            body = "", slug = "", url = "https://www.youtube.com/watch?v=abc123",
            gameCoverImage = cover
        )
        val detail = JSONObject(
            """{"id":"$videoId","title":"Starfall Trailer","video_url":"https://www.youtube.com/watch?v=abc123"}"""
        ).detailItem(fallback, preferGame = false)

        assertEquals(cover, detail.gameCoverImage)
        assertEquals(thumbnail, detail.image)
    }

    @Test fun videoDetailsUseTheUuidEndpointInsteadOfNewswireSlug() {
        val video = FeedItem(
            id = videoId, title = "Starfall Trailer", summary = "", image = thumbnail,
            body = "", slug = "", url = "https://www.youtube.com/watch?v=abc123"
        )

        assertEquals("/videos/$videoId", contentDetailRoute(video, false, setOf(videoId)))
    }

    @Test fun gameAndNewsRoutesRemainCompatibleAndVideoIdsAreValidated() {
        val item = FeedItem(
            id = "news-42", title = "Patch notes", summary = "", image = null,
            body = "", slug = "patch-notes", url = null
        )
        assertEquals("/games/starfall", contentDetailRoute(item.copy(slug = "Starfall"), true, emptySet()))
        assertEquals("/newswire/patch-notes", contentDetailRoute(item, false, emptySet()))

        val invalidVideo = item.copy(id = "video-42", slug = "")
        assertNull(contentDetailRoute(invalidVideo, false, setOf("video-42")))
    }
}
