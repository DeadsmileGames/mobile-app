package games.deadsmile.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedGameParsingTest {
    @Test fun articleEndpointKeepsArticleAndItsAssociatedGame() {
        val payload = JSONObject(
            """
            {
              "article": {
                "id": "news-7",
                "slug": "patch-notes",
                "title": "Patch Notes",
                "game": {
                  "id": "game-42",
                  "slug": "starfall",
                  "title": "Starfall"
                }
              }
            }
            """.trimIndent()
        )
        val fallback = FeedItem(
            id = "news-7",
            title = "Fallback title",
            summary = "",
            image = null,
            body = "",
            slug = "patch-notes",
            url = null
        )

        val result = payload.detailItem(fallback, preferGame = false)

        assertEquals("Patch Notes", result.title)
        assertTrue(result.associatedGameIds.contains("game-42"))
        assertTrue(result.associatedGameSlugs.contains("starfall"))
        assertTrue(result.relatedGames.any { it.id == "game-42" && it.title == "Starfall" })
    }

    @Test fun articleAndRelatedGameAtEnvelopeLevelAreNotConfused() {
        val payload = JSONObject(
            """
            {
              "article": {"id":"news-8","slug":"release","title":"Release announcement"},
              "game": {"id":"game-8","slug":"release-game","title":"Associated Game"}
            }
            """.trimIndent()
        )
        val fallback = FeedItem(
            id = "news-8",
            title = "Fallback title",
            summary = "",
            image = null,
            body = "",
            slug = "release",
            url = null
        )

        val result = payload.detailItem(fallback, preferGame = false)

        assertEquals("Release announcement", result.title)
        assertTrue(result.relatedGames.any { it.id == "game-8" && it.title == "Associated Game" })
    }
}
