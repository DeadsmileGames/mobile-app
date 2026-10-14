package games.deadsmile.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseRepositoryTest {
    @Test
    fun comparesVersionComponentsNumerically() {
        assertTrue(GitHubReleaseRepository.compareVersions("v1.10.0", "1.9.9") > 0)
        assertTrue(GitHubReleaseRepository.compareVersions("1.2.1", "1.2.0") > 0)
        assertTrue(GitHubReleaseRepository.compareVersions("1.1.0", "1.1.0") == 0)
        assertTrue(GitHubReleaseRepository.compareVersions("1.1.0", "1.1.0-beta") > 0)
    }

    @Test
    fun normalizesTagsAndRejectsMalformedVersions() {
        assertEquals("1.2.3", GitHubReleaseRepository.normalizeVersion("v1.2.3"))
        assertEquals("1.2.3", GitHubReleaseRepository.normalizeVersion("1.2.3-rc.1"))
        assertNull(GitHubReleaseRepository.normalizeVersion("latest"))
        assertNull(GitHubReleaseRepository.normalizeVersion("v1.2"))
        assertNull(GitHubReleaseRepository.normalizeVersion("1.2.3.4.5"))
    }

    @Test
    fun acceptsOnlyHttpsApkAssetsFromExpectedRepositoryReleasePath() {
        assertTrue(
            GitHubReleaseRepository.isTrustedApkUrl(
                "https://github.com/DeadsmileGames/mobile-app/releases/download/v1.2.3/Deadsmile-Games-Companion-1.2.3.apk"
            )
        )
        assertFalse(
            GitHubReleaseRepository.isTrustedApkUrl(
                "https://github.com.evil.example/DeadsmileGames/mobile-app/releases/download/v1.2.3/update.apk"
            )
        )
        assertFalse(
            GitHubReleaseRepository.isTrustedApkUrl(
                "http://github.com/DeadsmileGames/mobile-app/releases/download/v1.2.3/update.apk"
            )
        )
        assertFalse(
            GitHubReleaseRepository.isTrustedApkUrl(
                "https://github.com/DeadsmileGames/other-app/releases/download/v1.2.3/update.apk"
            )
        )
        assertFalse(
            GitHubReleaseRepository.isTrustedApkUrl(
                "https://github.com/DeadsmileGames/mobile-app/releases/download/v1.2.3/update.apk?download=1"
            )
        )
    }
}
