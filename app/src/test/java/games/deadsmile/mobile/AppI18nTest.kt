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

    @Test fun accountAndRequestFeedbackIsTranslatedInEnglishAndSpanish() {
        val messages = listOf(
            Triple("Não foi possível carregar o conteúdo completo agora. Tente novamente.", "Could not load the full content right now. Please try again.", "No se pudo cargar el contenido completo ahora. Inténtalo de nuevo."),
            Triple("Enviamos um link de confirmação. Seu e-mail atual continua ativo até a confirmação.", "We sent a confirmation link. Your current email remains active until confirmation.", "Enviamos un enlace de confirmación. Tu correo actual seguirá activo hasta que lo confirmes."),
            Triple("Preferências de privacidade salvas.", "Privacy preferences saved.", "Preferencias de privacidad guardadas."),
            Triple("Não foi possível iniciar a verificação em duas etapas.", "Could not start two-step verification.", "No se pudo iniciar la verificación en dos pasos."),
            Triple("Verificação em duas etapas desativada.", "Two-step verification disabled.", "Verificación en dos pasos desactivada."),
            Triple("Verificação em duas etapas ativada.", "Two-step verification enabled.", "Verificación en dos pasos activada."),
            Triple("Não foi possível identificar este jogo para sua lista de desejos.", "Could not identify this game for your wishlist.", "No se pudo identificar este juego para tu lista de deseos."),
            Triple("Jogo adicionado à lista de desejos.", "Game added to wishlist.", "Juego añadido a la lista de deseos."),
            Triple("Digite o código de autenticação em duas etapas.", "Enter your two-step verification code.", "Introduce el código de verificación en dos pasos."),
            Triple("Autenticação concluída.", "Authentication complete.", "Autenticación completada."),
            Triple("Conta criada. Confira seu e-mail para concluir a verificação.", "Account created. Check your email to complete verification.", "Cuenta creada. Revisa tu correo para completar la verificación."),
            Triple("Você saiu da conta.", "You have signed out.", "Has cerrado sesión."),
            Triple("Não foi possível conectar. Confira sua internet e tente novamente.", "Could not connect. Check your internet connection and try again.", "No se pudo conectar. Comprueba tu conexión a internet e inténtalo de nuevo."),
            Triple("E-mail ou senha não conferem.", "Email or password is incorrect.", "El correo o la contraseña no son correctos."),
            Triple("Não foi possível autorizar esta ação. Confira sua conta e tente novamente.", "Could not authorize this action. Check your account and try again.", "No se pudo autorizar esta acción. Comprueba tu cuenta e inténtalo de nuevo."),
            Triple("Este recurso não está disponível no momento.", "This feature is not available right now.", "Esta función no está disponible en este momento."),
            Triple("Esses dados já estão em uso. Confira as informações.", "These details are already in use. Check the information.", "Estos datos ya están en uso. Comprueba la información."),
            Triple("Algumas informações precisam ser corrigidas.", "Some information needs to be corrected.", "Hay que corregir algunos datos."),
            Triple("Muitas tentativas em sequência. Aguarde um pouco e tente novamente.", "Too many attempts in a row. Please wait a moment and try again.", "Demasiados intentos seguidos. Espera un momento e inténtalo de nuevo."),
            Triple("O serviço está indisponível agora. Tente novamente mais tarde.", "The service is unavailable right now. Please try again later.", "El servicio no está disponible ahora. Inténtalo de nuevo más tarde."),
            Triple("Não foi possível concluir agora. Tente novamente.", "Could not complete this right now. Please try again.", "No se pudo completar ahora. Inténtalo de nuevo."),
            Triple("Perfil atualizado.", "Profile updated.", "Perfil actualizado."),
            Triple("Dispositivo desconectado.", "Device disconnected.", "Dispositivo desconectado."),
            Triple("Agora adicione a chave ao seu aplicativo autenticador.", "Now add the key to your authenticator app.", "Ahora añade la clave a tu aplicación de autenticación."),
            Triple("Entre na conta para usar sua lista de desejos.", "Sign in to use your wishlist.", "Inicia sesión para usar tu lista de deseos."),
            Triple("Jogo removido da lista de desejos.", "Game removed from wishlist.", "Juego eliminado de la lista de deseos."),
            Triple("Login realizado.", "Login successful.", "Inicio de sesión correcto."),
            Triple("Confira os dados informados e tente novamente.", "Check the information you entered and try again.", "Revisa los datos que has introducido e inténtalo de nuevo."),
        )
        messages.forEach { (source, english, spanish) ->
            assertEquals("English translation for: " + source, english, AppI18n.translate("en", source))
            assertEquals("Spanish translation for: " + source, spanish, AppI18n.translate("es", source))
            assertEquals("Portuguese source stays unchanged", source, AppI18n.translate("pt-BR", source))
        }
    }

    @Test fun preservesUnknownServerContent() {
        assertEquals("Chrono Harbor", AppI18n.translate("en", "Chrono Harbor"))
        assertEquals("Texto específico da API", AppI18n.translate("es", "Texto específico da API"))
    }
}
