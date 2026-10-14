package games.deadsmile.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class AppI18nTest {
    @Test fun translatesCoreNavigationToEnglish() {
        assertEquals("Home", AppI18n.translate("en", "Início"))
        assertEquals("Settings", AppI18n.translate("en", "Configurações"))
    }

    @Test fun translatesThemeModesToSpanish() {
        assertEquals("Oscuro", AppI18n.translate("es", "Escuro"))
        assertEquals("OLED (negro puro)", AppI18n.translate("es", "OLED (preto puro)"))
    }

    @Test fun preservesUnknownServerContent() {
        assertEquals("Chrono Harbor", AppI18n.translate("en", "Chrono Harbor"))
        assertEquals("Texto específico da API", AppI18n.translate("es", "Texto específico da API"))
    }
}
