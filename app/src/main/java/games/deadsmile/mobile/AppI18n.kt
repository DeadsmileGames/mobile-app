package games.deadsmile.mobile

import android.content.Context
import java.util.Locale

/**
 * Central translation layer for application-owned UI copy.
 * Text received from the API remains as authored when no translation is available.
 */
object AppI18n {
    private val english = mapOf(
        "Deadsmile" to "Deadsmile Games Companion",
        "DEADSMILE GAMES" to "DEADSMILE GAMES COMPANION",
        "Início" to "Home", "Jogos" to "Games", "Notícias" to "News", "Vídeos" to "Videos",
        "Biblioteca" to "Library", "Conta" to "Account", "Lista de desejos" to "Wishlist",
        "Configurações" to "Settings", "Detalhes" to "Details", "Voltar" to "Back",
        "Atualizar conteúdo" to "Refresh content", "Detalhes do jogo" to "Game details",
        "Catálogo, notícias e novidades" to "Catalog, news and updates",
        "Catálogo, notícias e biblioteca de PC" to "Catalog, news and PC library",
        "Catálogo, conta e novidades da plataforma" to "Catalog, account and platform updates",
        "Catálogo oficial, notícias, vídeos e biblioteca da sua conta para PC." to "Official catalog, news, videos and your PC account library.",
        "Explorar jogos" to "Browse games", "Últimas notícias" to "Latest news",
        "Ver catálogo" to "View catalog", "Ver todas" to "View all", "Ver todos" to "View all",
        "Assistir" to "Watch", "Ver detalhes" to "View details", "Buscar jogos" to "Search games",
        "Buscar conteúdo" to "Search content", "Carregando informações…" to "Loading information…",
        "Carregando dispositivos…" to "Loading devices…",
        "O catálogo não está disponível agora. Tente atualizar em alguns instantes." to "The catalog is temporarily unavailable. Try refreshing shortly.",
        "Nenhuma notícia nova por enquanto." to "No new news right now.",
        "Novos vídeos aparecerão aqui quando estiverem disponíveis." to "New videos will appear here when available.",
        "Visitar site oficial" to "Visit official website", "Abrir Microsoft Store" to "Open Microsoft Store",
        "Ver página no itch.io" to "View itch.io page", "Ver opções de compra" to "View purchase options",
        "Sobre o jogo" to "About the game", "Artigo completo" to "Full article",
        "Capturas de tela" to "Screenshots", "Imagens do artigo" to "Article images",
        "Jogos relacionados" to "Related games", "Abrir conteúdo" to "Open content",
        "Visitar Deadsmile Games" to "Visit Deadsmile Games", "CATÁLOGO DEADSMILE" to "DEADSMILE CATALOG",
        "DEADSMILE · CONTEÚDO" to "DEADSMILE · CONTENT",
        "Publicado · disponível no launcher para Windows" to "Published · available in the Windows launcher",
        "Publicado · download ainda não configurado" to "Published · download not configured",
        "Em desenvolvimento" to "In development", "Jogo anunciado" to "Announced",
        "Lançado" to "Released", "Anunciado" to "Announced", "Estado do jogo" to "Game status",
        "O catálogo confirma que o jogo está publicado. Para instalar ou jogar, use o launcher no PC." to "The catalog confirms this game is published. Use the PC launcher to install or play it.",
        "O jogo está marcado como publicado, mas a API não confirma um download configurado. Consulte os canais oficiais." to "The game is marked as published, but the API does not confirm a configured download. Check official channels.",
        "Ainda não há uma data de lançamento publicada." to "A release date has not been published yet.",
        "A data prevista passou, mas o catálogo ainda marca o jogo como em desenvolvimento." to "The planned date has passed, but the catalog still marks the game as in development.",
        "O jogo foi anunciado, mas a data de lançamento ainda não foi divulgada." to "The game has been announced, but no release date has been shared.",
        "Não há dados suficientes para confirmar a disponibilidade deste jogo. Consulte o site oficial." to "There is not enough data to confirm availability. Check the official website.",
        "Acesso pago" to "Paid access", "Gratuito" to "Free",
        "Personalize sua experiência" to "Personalize your experience",
        "Deixe o app do seu jeito. As mudanças são aplicadas na hora." to "Make the app yours. Changes apply immediately.",
        "Aparência" to "Appearance", "Seguir o sistema" to "Follow system", "Claro" to "Light", "Escuro" to "Dark",
        "OLED (preto puro)" to "OLED (pure black)", "Cores do sistema" to "System colors",
        "Usa as cores dinâmicas do Android quando disponíveis." to "Uses Android dynamic colors when available.",
        "Cor de destaque" to "Accent color", "Violeta" to "Violet", "Rosa" to "Rose", "Verde-água" to "Teal", "Azul" to "Blue",
        "Comportamento" to "Behavior", "Reduzir animações" to "Reduce animations",
        "Encurta transições e efeitos de movimento." to "Shortens transitions and motion effects.",
        "Notificações" to "Notifications", "Novidades do catálogo" to "Catalog updates",
        "Avisos de novidades do catálogo, notícias e vídeos. A atualização em segundo plano depende das regras de bateria e rede do Android." to "Catalog, news and video notifications. Background checks depend on Android battery and network policies.",
        "Escolha os avisos que quer receber" to "Choose which notifications to receive",
        "Receber novidades" to "Receive updates", "Consulta atualizações periodicamente, mesmo fora do app." to "Checks for updates periodically, even when the app is closed.",
        "Notificações ativadas." to "Notifications enabled.", "Notificações pausadas." to "Notifications paused.",
        "Permissão não concedida. Você pode ativá-la nas configurações do Android." to "Permission not granted. Enable it in Android settings.",
        "Novos jogos" to "New games", "Publicações e atualizações do catálogo." to "Catalog releases and updates.",
        "Notícia nova na Deadsmile" to "New Deadsmile Games news", "Novas publicações e mudanças nas notícias." to "New posts and news changes.",
        "Novo vídeo" to "New video", "Vídeos adicionados ao feed." to "Videos added to the feed.",
        "Eventos da conta" to "Account events", "Eventos da plataforma, como conquistas e lista de desejos." to "Platform events such as achievements and wishlist changes.",
        "Sobre" to "About", "Deadsmile Games para Android" to "Deadsmile Games Companion for Android",
        "Sua lista de desejos" to "Your wishlist", "Sua lista está vazia" to "Your wishlist is empty",
        "Adicione jogos aos favoritos pela tela de detalhes." to "Add games to your favorites from the details screen.",
        "Entre na sua conta para guardar jogos e sincronizar sua lista com o launcher." to "Sign in to save games and sync your wishlist with the launcher.",
        "Entre na sua conta" to "Sign in", "Sincronizada com sua conta Deadsmile" to "Synced with your Deadsmile account",
        "Remover da lista" to "Remove from wishlist", "Sua biblioteca" to "Your library",
        "Entre para consultar os jogos associados à sua conta." to "Sign in to view games associated with your account.",
        "Jogos vinculados à sua conta · somente consulta" to "Games linked to your account · view only",
        "Na biblioteca" to "In library", "Nome de exibição" to "Display name", "Nome de usuário" to "Username",
        "Biografia" to "Bio", "Site pessoal" to "Personal website", "Localização" to "Location",
        "URL da foto (opcional)" to "Photo URL (optional)", "Escolher foto do dispositivo" to "Choose a photo",
        "Foto local selecionada" to "Local photo selected", "Remover" to "Remove", "Cancelar" to "Cancel",
        "Editar perfil" to "Edit profile", "Atualizar" to "Refresh", "Salvar perfil" to "Save profile",
        "Conta e acesso" to "Account and access", "Endereço de e-mail" to "Email address", "Novo e-mail" to "New email",
        "Senha atual" to "Current password", "Enviar link de confirmação" to "Send confirmation link",
        "Sessões ativas" to "Active sessions",
        "Veja onde sua conta está conectada e encerre sessões que você não reconhece." to "Review where your account is signed in and end sessions you do not recognize.",
        "Sessão atual" to "Current session", "Desconectar" to "Sign out device", "Segurança" to "Security",
        "Verificação em duas etapas" to "Two-step verification", "Código do autenticador" to "Authenticator code",
        "Desativar verificação em duas etapas" to "Disable two-step verification",
        "Configurar verificação em duas etapas" to "Set up two-step verification",
        "Adicione esta chave manualmente no aplicativo autenticador:" to "Enter this key manually in your authenticator app:",
        "URI de configuração" to "Setup URI", "Código de seis dígitos" to "Six-digit code",
        "Confirmar e ativar" to "Confirm and enable", "Privacidade" to "Privacy", "Salvar privacidade" to "Save privacy",
        "Sair da conta" to "Sign out", "Desativar verificação em duas etapas?" to "Disable two-step verification?",
        "Confirme com o código atual do seu autenticador." to "Confirm with your current authenticator code.",
        "Desativar" to "Disable", "Seu universo de jogos" to "Your games catalog",
        "Código de autenticação" to "Authentication code", "Confirmar código" to "Confirm code",
        "E-mail" to "Email", "Senha" to "Password", "Precisa de ajuda com a conta?" to "Need help with your account?",
        "Crie sua conta" to "Create your account", "Bom ter você de volta" to "Welcome back",
        "Digite o código do seu autenticador para continuar." to "Enter your authenticator code to continue.",
        "Entre para acompanhar seus jogos e novidades." to "Sign in to follow your games and updates.",
        "Entre para acessar sua biblioteca e seu perfil." to "Sign in to access your library and profile.",
        "Criar conta" to "Create account", "Entrar" to "Sign in",
        "Já tenho uma conta" to "I already have an account", "Criar uma conta" to "Create an account",
        "Não foi possível usar essa imagem. Escolha outra foto." to "That image could not be used. Choose another photo.",
        "Foto selecionada. Salve o perfil para aplicar." to "Photo selected. Save your profile to apply it.",
        "Salvando…" to "Saving…", "Fechar" to "Close", "Alterar" to "Change",
        "Mais informações serão adicionadas assim que estiverem disponíveis." to "More information will be added when available.",
        "Disponível no PC" to "Available on PC",
        "Desenvolvido por " to "Developed by ", "Publicado por " to "Published by ",
        "Não foi possível iniciar uma sessão segura." to "Could not start a secure session.",
        "Entre na sua conta para continuar." to "Sign in to continue.",
        "A solicitação não foi autorizada." to "The request was not authorized.",
        "Muitas tentativas. Aguarde." to "Too many attempts. Please wait.",
        "Não foi possível concluir a solicitação." to "Could not complete the request.",
        "A API retornou uma resposta inválida." to "The API returned an invalid response."
    )

    private val spanish = mapOf(
        "Deadsmile" to "Deadsmile Games Companion",
        "DEADSMILE GAMES" to "DEADSMILE GAMES COMPANION",
        "Início" to "Inicio", "Jogos" to "Juegos", "Notícias" to "Noticias", "Vídeos" to "Vídeos",
        "Biblioteca" to "Biblioteca", "Conta" to "Cuenta", "Lista de desejos" to "Lista de deseos",
        "Configurações" to "Ajustes", "Detalhes" to "Detalles", "Voltar" to "Atrás",
        "Atualizar conteúdo" to "Actualizar contenido", "Detalhes do jogo" to "Detalles del juego",
        "Catálogo, notícias e biblioteca de PC" to "Catálogo, noticias y biblioteca de PC",
        "Catálogo, conta e novidades da plataforma" to "Catálogo, cuenta y novedades de la plataforma",
        "Catálogo oficial, notícias, vídeos e biblioteca da sua conta para PC." to "Catálogo oficial, noticias, vídeos y biblioteca de tu cuenta de PC.",
        "Explorar jogos" to "Explorar juegos", "Últimas notícias" to "Últimas noticias",
        "Ver catálogo" to "Ver catálogo", "Ver todas" to "Ver todas", "Ver todos" to "Ver todos",
        "Assistir" to "Ver", "Ver detalhes" to "Ver detalles", "Carregando informações…" to "Cargando información…",
        "Nenhuma notícia nova por enquanto." to "No hay noticias nuevas por ahora.",
        "Visitar site oficial" to "Visitar sitio oficial", "Abrir Microsoft Store" to "Abrir Microsoft Store",
        "Ver página no itch.io" to "Ver página en itch.io", "Ver opções de compra" to "Ver opciones de compra",
        "Sobre o jogo" to "Sobre el juego", "Artigo completo" to "Artículo completo",
        "Capturas de tela" to "Capturas de pantalla", "Imagens do artigo" to "Imágenes del artículo",
        "Jogos relacionados" to "Juegos relacionados", "Abrir conteúdo" to "Abrir contenido",
        "Publicado · disponível no launcher para Windows" to "Publicado · disponible en el launcher de Windows",
        "Publicado · download ainda não configurado" to "Publicado · descarga aún no configurada",
        "Em desenvolvimento" to "En desarrollo", "Jogo anunciado" to "Juego anunciado",
        "Lançado" to "Lanzado", "Anunciado" to "Anunciado", "Estado do jogo" to "Estado del juego",
        "Acesso pago" to "Acceso de pago", "Gratuito" to "Gratis",
        "Personalize sua experiência" to "Personaliza tu experiencia", "Aparência" to "Apariencia",
        "Seguir o sistema" to "Seguir el sistema", "Claro" to "Claro", "Escuro" to "Oscuro",
        "OLED (preto puro)" to "OLED (negro puro)", "Cores do sistema" to "Colores del sistema",
        "Usa as cores dinâmicas do Android quando disponíveis." to "Usa los colores dinámicos de Android cuando están disponibles.",
        "Cor de destaque" to "Color de acento", "Violeta" to "Violeta", "Rosa" to "Rosa",
        "Verde-água" to "Verde azulado", "Azul" to "Azul", "Comportamento" to "Comportamiento",
        "Reduzir animações" to "Reducir animaciones", "Encurta transições e efeitos de movimento." to "Reduce transiciones y efectos de movimiento.",
        "Notificações" to "Notificaciones", "Novidades do catálogo" to "Novedades del catálogo",
        "Avisos de novidades do catálogo, notícias e vídeos. A atualização em segundo plano depende das regras de bateria e rede do Android." to "Avisos del catálogo, noticias y vídeos. La comprobación en segundo plano depende de la batería y la red de Android.",
        "Novos jogos" to "Juegos nuevos", "Eventos da conta" to "Eventos de la cuenta",
        "Receber novidades" to "Recibir novedades", "Notificações ativadas." to "Notificaciones activadas.",
        "Notificações pausadas." to "Notificaciones pausadas.", "Sobre" to "Acerca de",
        "Deadsmile Games para Android" to "Deadsmile Games Companion para Android",
        "Sua lista está vazia" to "Tu lista está vacía", "Sua biblioteca" to "Tu biblioteca",
        "Entre para consultar os jogos associados à sua conta." to "Inicia sesión para ver los juegos asociados a tu cuenta.",
        "Nome de exibição" to "Nombre para mostrar", "Nome de usuário" to "Nombre de usuario",
        "Biografia" to "Biografía", "Site pessoal" to "Sitio personal", "Localização" to "Ubicación",
        "Escolher foto do dispositivo" to "Elegir una foto", "Editar perfil" to "Editar perfil",
        "Salvar perfil" to "Guardar perfil", "Cancelar" to "Cancelar", "Atualizar" to "Actualizar",
        "Sessões ativas" to "Sesiones activas", "Segurança" to "Seguridad",
        "Verificação em duas etapas" to "Verificación en dos pasos", "Código do autenticador" to "Código del autenticador",
        "Privacidade" to "Privacidad", "Sair da conta" to "Cerrar sesión", "Desativar" to "Desactivar",
        "E-mail" to "Correo electrónico", "Senha" to "Contraseña", "Crie sua conta" to "Crea tu cuenta",
        "Bom ter você de volta" to "Qué bueno verte de nuevo", "Criar conta" to "Crear cuenta",
        "Entrar" to "Iniciar sesión", "Já tenho uma conta" to "Ya tengo una cuenta",
        "Criar uma conta" to "Crear una cuenta", "Disponível no PC" to "Disponible en PC",
        "Desenvolvido por " to "Desarrollado por ", "Publicado por " to "Publicado por "
    )

    fun translate(context: Context, value: String): String {
        val language = runCatching { context.resources.configuration.locales.get(0)?.language }.getOrNull()
            ?: Locale.getDefault().language
        return translate(language, value)
    }

    internal fun translate(language: String, value: String): String {
        val table = when (language.lowercase(Locale.ROOT)) {
            "en" -> english
            "es" -> spanish
            else -> return value
        }
        return table[value] ?: when {
            value.startsWith("Desenvolvido por ") -> table["Desenvolvido por "] + value.removePrefix("Desenvolvido por ")
            value.startsWith("Publicado por ") -> table["Publicado por "] + value.removePrefix("Publicado por ")
            else -> value
        }
    }
}
