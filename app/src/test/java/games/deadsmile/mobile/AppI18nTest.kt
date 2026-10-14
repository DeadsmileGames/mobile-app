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

    @Test fun globalSearchCopyIsTranslatedIntoAllSupportedLanguages() {
        assertEquals("Search across the app", AppI18n.translate("en", "Buscar em todo o app"))
        assertEquals("Buscar en toda la aplicación", AppI18n.translate("es", "Buscar em todo o app"))
        assertEquals("Buscar em todo o app", AppI18n.translate("pt-BR", "Buscar em todo o app"))
    }

    @Test fun mandatoryLanguageSelectionCopyIsLocalized() {
        assertEquals("Choose a language to continue.", AppI18n.translate("en", "O idioma precisa ser escolhido para continuar."))
        assertEquals("Elige un idioma para continuar.", AppI18n.translate("es", "O idioma precisa ser escolhido para continuar."))
    }

    @Test fun dynamicAppOwnedMessagesKeepTheirDataWhenTranslated() {
        assertEquals("Installed version: 1.2.3", AppI18n.translate("en", "Versão instalada: 1.2.3"))
        assertEquals("Versión instalada: 1.2.3", AppI18n.translate("es", "Versão instalada: 1.2.3"))
    }

    @Test fun preservesUnknownServerContent() {
        assertEquals("Chrono Harbor", AppI18n.translate("en", "Chrono Harbor"))
        assertEquals("Texto específico da API", AppI18n.translate("es", "Texto específico da API"))
    }
}
