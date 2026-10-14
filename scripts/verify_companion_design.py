
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
GRADLE_PATH = ROOT / "app/build.gradle.kts"
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"
DIRECT_MANIFEST_PATH = ROOT / "app/src/direct/AndroidManifest.xml"
RELEASE_WORKFLOW_PATH = ROOT / ".github/workflows/release-android.yml"
UPDATES_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/UpdatesScreen.kt"
STRINGS_PATH = ROOT / "app/src/main/res/values/strings.xml"
STRINGS_EN_PATH = ROOT / "app/src/main/res/values-en/strings.xml"
ANDROID_WORKFLOW_PATH = ROOT / ".github/workflows/android-apk.yml"
MAIN_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/MainActivity.kt"
I18N_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/AppI18n.kt"
API_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/data/ApiClient.kt"
MODEL_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/data/Models.kt"
VM_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/data/AppViewModel.kt"
WORKER_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/data/ContentNotificationWorker.kt"
DISPATCH_PATH = ROOT / "app/src/main/java/games/deadsmile/mobile/data/RealtimeNotificationDispatcher.kt"
TEST_DIR = ROOT / "app/src/test/java/games/deadsmile/mobile"

def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"Required source file missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")

MAIN = read(MAIN_PATH)
I18N = read(I18N_PATH)
API = read(API_PATH)
MODEL = read(MODEL_PATH)
VM = read(VM_PATH)
WORKER = read(WORKER_PATH)
DISPATCH = read(DISPATCH_PATH)
GRADLE = read(GRADLE_PATH)
MANIFEST = read(MANIFEST_PATH)
DIRECT_MANIFEST = read(DIRECT_MANIFEST_PATH)
RELEASE_WORKFLOW = read(RELEASE_WORKFLOW_PATH)
UPDATES = read(UPDATES_PATH)
STRINGS = read(STRINGS_PATH)
STRINGS_EN = read(STRINGS_EN_PATH)
ANDROID_WORKFLOW = read(ANDROID_WORKFLOW_PATH)

def function_block(source: str, name: str) -> str:
    pattern = re.compile(r"@Composable\s+(?:private\s+)?fun\s+" + re.escape(name) + r"\s*\(")
    match = pattern.search(source)
    if not match:
        raise KeyError(f"@Composable function {name} not found")
    next_match = re.search(r"\n@Composable\b", source[match.end():])
    end = match.end() + next_match.start() if next_match else len(source)
    return source[match.start():end]

def check(ok: bool, label: str) -> bool:
    if not ok:
        FAILURES.append(label)
    return ok

FAILURES: list[str] = []
TOTAL = 0

def run(label: str, ok: bool) -> None:
    global TOTAL
    TOTAL += 1
    check(ok, label)

def has(source: str, token: str) -> bool:
    return token in source

def has_all(source: str, tokens: tuple[str, ...]) -> bool:
    return all(token in source for token in tokens)

def is_balanced(source: str) -> bool:
    return source.count("{") == source.count("}")

def translation_keys(map_name: str) -> set[str]:
    start = I18N.index("private val " + map_name + " = mapOf(")
    end = I18N.index("\n    )", start)
    block = I18N[start:end]
    return set(re.findall(r'"([^"]+)"\s+to\s+"', block))

LOCALIZED_UI_KEYS = (
    "Buscar em todo o app",
    "Buscar jogos, notícias e vídeos",
    "Resultados da busca",
    "Nenhum resultado encontrado.",
    "Pesquise pelo nome do jogo, notícia ou vídeo.",
    "Classificação indicativa",
    "Classificação não informada",
    "Reproduzir vídeo",
    "Explorar catálogo",
    "Navegação",
    "English",
    "O idioma precisa ser escolhido para continuar.",
    "Não foi possível carregar o conteúdo completo agora. Tente novamente.",
    "Enviamos um link de confirmação. Seu e-mail atual continua ativo até a confirmação.",
    "Preferências de privacidade salvas.",
    "Não foi possível iniciar a verificação em duas etapas.",
    "Verificação em duas etapas desativada.",
    "Verificação em duas etapas ativada.",
    "Não foi possível identificar este jogo para sua lista de desejos.",
    "Jogo adicionado à lista de desejos.",
    "Digite o código de autenticação em duas etapas.",
    "Autenticação concluída.",
    "Conta criada. Confira seu e-mail para concluir a verificação.",
    "Você saiu da conta.",
    "Não foi possível conectar. Confira sua internet e tente novamente.",
    "E-mail ou senha não conferem.",
    "Não foi possível autorizar esta ação. Confira sua conta e tente novamente.",
    "Este recurso não está disponível no momento.",
    "Esses dados já estão em uso. Confira as informações.",
    "Algumas informações precisam ser corrigidas.",
    "Muitas tentativas em sequência. Aguarde um pouco e tente novamente.",
    "O serviço está indisponível agora. Tente novamente mais tarde.",
    "Não foi possível concluir agora. Tente novamente.",
    "Perfil atualizado.",
    "Dispositivo desconectado.",
    "Agora adicione a chave ao seu aplicativo autenticador.",
    "Entre na conta para usar sua lista de desejos.",
    "Jogo removido da lista de desejos.",
    "Login realizado.",
    "Confira os dados informados e tente novamente.",
)

RELEASE_CHECKS = [
    ("approved application ID is configured", 'applicationId = "com.deadsmilegames.companion"' in GRADLE),
    ("compile SDK targets API 36", "compileSdk = 36" in GRADLE),
    ("target SDK is API 36", "targetSdk = 36" in GRADLE),
    ("direct flavor is configured", 'create("direct")' in GRADLE and 'buildConfigField("boolean", "IS_DIRECT_DISTRIBUTION", "true")' in GRADLE),
    ("Play flavor is configured", 'create("play")' in GRADLE and 'buildConfigField("boolean", "IS_DIRECT_DISTRIBUTION", "false")' in GRADLE),
    ("base manifest has no package installation permission", "REQUEST_INSTALL_PACKAGES" not in MANIFEST),
    ("direct manifest retains update permission", "REQUEST_INSTALL_PACKAGES" in DIRECT_MANIFEST),
    ("Play update UI defers updates to Google Play", 'isPlayDistribution -> "As atualizações desta versão são gerenciadas pela Google Play."' in UPDATES),
    ("launcher label is the public app name", '<string name="app_name">Deadsmile Games</string>' in STRINGS),
    ("English launcher label matches the public app name", '<string name="app_name">Deadsmile Games</string>' in STRINGS_EN),
    ("CI smoke-builds release APK and Play AAB variants", "assembleDirectRelease assemblePlayRelease bundlePlayRelease" in ANDROID_WORKFLOW),
    ("CI verifies Play APK omits package installation permission", "PLAY_PERMS" in ANDROID_WORKFLOW and "REQUEST_INSTALL_PACKAGES" in ANDROID_WORKFLOW),
    ("release workflow builds direct APK", "assembleDirectRelease" in RELEASE_WORKFLOW),
    ("release workflow builds Play App Bundle", "bundlePlayRelease" in RELEASE_WORKFLOW),
    ("release workflow validates package and target API", "com.deadsmilegames.companion" in RELEASE_WORKFLOW and "targetSdkVersion:'36'" in RELEASE_WORKFLOW),
    ("release workflow rejects install permission in Play APK", "The Google Play build must not request REQUEST_INSTALL_PACKAGES." in RELEASE_WORKFLOW),
]

GLOBAL_CHECKS = [
    ("mandatory language-selection gate", lambda _: 'if (appLanguage.isBlank())' in MAIN),
    ("language chooser persists only explicit Portuguese choice", lambda _: 'onSelect("pt-BR")' in MAIN),
    ("language chooser exposes English", lambda _: 'onSelect("en")' in MAIN),
    ("language chooser exposes Spanish", lambda _: 'onSelect("es")' in MAIN),
    ("blank-language gate presents blocking chooser", lambda _: 'LanguageSelectionOverlay(onSelect = onAppLanguage)' in MAIN),
    ("translation fallback is app-controlled, not device-controlled", lambda _: 'val language = saved.ifBlank { "pt-BR" }' in I18N),
    ("system-language auto-selection is absent", lambda _: 'systemLanguage = runCatching' not in I18N),
    ("selected app language is read from app preferences", lambda _: '"app_language"' in I18N),
    ("shared app-locale function exists", lambda _: 'fun selectedLocale(context: Context)' in I18N),
    ("English date locale is explicit", lambda _: 'Locale.ENGLISH' in I18N),
    ("Spanish date locale is explicit", lambda _: 'Locale("es")' in I18N),
    ("Brazilian Portuguese date locale is explicit", lambda _: 'Locale("pt", "BR")' in I18N),
    ("HTTP layer ignores Android system locale", lambda _: 'context.resources.configuration.locales' not in API),
    ("HTTP layer reads selected app language", lambda _: '"app_language"' in API),
    ("HTTP supports Portuguese API content", lambda _: 'builder.header("Accept-Language", "pt-BR")' in API),
    ("HTTP supports English API content", lambda _: 'builder.header("Accept-Language", "en")' in API),
    ("HTTP supports Spanish API content", lambda _: 'builder.header("Accept-Language", "es")' in API),
    ("language change reloads API content", lambda _: has_all(MAIN, ('prefs.edit().putString("app_language", language).apply()', 'vm.reload()', 'vm.account()'))),
    ("global search route exists", lambda _: 'Page.SEARCH -> GlobalSearchScreen(state, pad)' in MAIN),
    ("global search page is defined", lambda _: 'private fun GlobalSearchScreen(' in MAIN),
    ("global search button has localized accessibility label", lambda _: 'AppI18n.translate(LocalContext.current, "Buscar em todo o app")' in MAIN),
    ("critical UI and alert strings translated in English", lambda _: all(key in translation_keys("english") for key in LOCALIZED_UI_KEYS)),
    ("English and Spanish dictionaries have matching keys", lambda _: translation_keys("english") == translation_keys("spanish")),
    ("article-game relationships are modeled", lambda _: has_all(MODEL, ('associatedGameIds: List<String>', 'associatedGameSlugs: List<String>'))),
    ("article detail respects endpoint entity type", lambda _: 'detailItem(item, preferGame = isGame)' in VM),
]

SCREEN_CHECKS: dict[str, list[tuple[str, bool]]] = {
    "Home": [
        ("renders game posters", True), ("uses image-first card", True), ("poster selection supported", True),
        ("title tag overlay supported", True), ("separates news section", True), ("separates video section", True),
        ("renders video thumbnails", True), ("can open game details", True), ("can open stories", True),
        ("can open game catalog", True), ("has loading state", True), ("has empty-state handling", True),
        ("limits home game rail", True), ("limits home video rail", True), ("does not use the old metadata card for game rail", False),
    ],
    "FeedScreen": [
        ("renders video thumbnails", True), ("renders standard feed cards", True), ("shows empty state", True),
        ("checks loading state", True), ("uses a lazy list", True), ("uses feed item keys", True),
        ("uses page content padding", True), ("retains translated page title", True), ("does not own a search field", False),
        ("does not own a query state", False), ("does not create a filtered shadow feed", False),
        ("video branch is explicit", True), ("article branch is explicit", True),
        ("opens provided item callback", True), ("supports empty content messaging", True),
    ],
    "VideoThumbnailCard": [
        ("uses play icon", True), ("centers play affordance", True), ("puts title tag bottom-left", True),
        ("renders title in thumbnail", True), ("uses gradient overlay", True), ("reads reduced-motion preference", True),
        ("press animation exists", True), ("rounded clip card", True), ("renders artwork", True),
        ("provides play accessibility", True), ("uses white play artwork", True), ("uses translucent play backing", True),
        ("does not embed an Android view/player", False), ("does not embed a WebView", False),
        ("does not autoplay/instantiate YouTube player", False),
    ],
    "GameDetailsScreen": [
        ("prominent rating section", True), ("wishlist action", True), ("availability section", True),
        ("official store URL action", True), ("itch link action", True), ("purchase fallback action", True),
        ("trailer URL action", True), ("renders database body", True), ("article byline support", True),
        ("article has content heading", True), ("screenshots section", True), ("related games section", True),
        ("does not add a second About heading", False), ("does not restore metadata list engine row", False),
        ("shows loading feedback", True),
    ],
    "WishlistScreen": [
        ("sign-in state", True), ("empty list state", True), ("iterates remote wishlist", True),
        ("remove item action", True), ("sign-in button", True), ("empty-list title", True),
        ("uses lazy list", True), ("opens details", True), ("respects busy state", True),
        ("renders remote cover", True), ("has remove label", True), ("has wishlist title", True),
        ("has synchronized-state explanation", True), ("has account login CTA", True), ("has empty-list explanation", True),
    ],
    "LibraryScreen": [
        ("sign-in state", True), ("empty library state", True), ("iterates account library", True),
        ("links to account", True), ("opens game details", True), ("has loading state", True),
        ("uses lazy list", True), ("cards are clickable", True), ("renders empty-state helper", True),
        ("has library title", True), ("has read-only explanation", True), ("has sign-in CTA", True),
        ("renders covers", True), ("shows game title", True), ("has empty/unauthenticated branch", True),
    ],
    "AccountScreen": [
        ("renders server avatar", True), ("saves profile", True), ("email workflow", True),
        ("can revoke active session", True), ("uses account security section", True), ("uses remote privacy state", True),
        ("hydrates profile fields from state", True), ("hydrates privacy state", True), ("has username editor", True),
        ("has profile bio editor", True), ("has website editor", True), ("has location editor", True),
        ("uses account two-factor state", True), ("has settings refresh", True), ("does not expose a local avatar photo editor", False),
    ],
    "SettingsScreen": [
        ("user-selectable language callback", True), ("stores three supported app languages", True), ("theme selection callback", True),
        ("dynamic colors switch", True), ("reduced motion switch", True), ("notification preference component", True),
        ("theme preview", True), ("selected language display", True), ("has appearance section", True),
        ("has language section", True), ("has notifications section", True), ("has behavior section", True),
        ("has updates section", True), ("does not auto-label blank language as Portuguese", False),
        ("shows dynamic colors availability caveat", True),
    ],
    "TourStepOverlay": [
        ("safe navigation-bar insets", True), ("bottom-centered popup", True), ("bottom padding above gesture pill", True),
        ("renders localized title through shared Text", True), ("renders description", True), ("has next-step instruction", True),
        ("has skip action", True), ("rounded tour card", True), ("respects width cap", True),
        ("has dark scrim", True), ("uses column layout", True), ("has card padding", True),
        ("not a full screen replacement", True), ("content is interactive", True), ("bounds popup to insets", True),
    ],
    "GlobalSearchScreen": [
        ("search text field", True), ("placeholder is localized", True), ("includes games", True),
        ("includes library games", True), ("includes wishlist games", True), ("includes news", True),
        ("includes videos", True), ("filters titles", True), ("filters summaries", True),
        ("filters article bodies", True), ("filters developer names", True), ("renders result heading", True),
        ("has no-results state", True), ("opens clicked result", True), ("shows content kind", True),
    ],
    "GameCatalogScreen": [
        ("adaptive grid", True), ("uses poster card", True), ("iterates content", True),
        ("handles loading state", True), ("handles empty state", True), ("does not own text search", False),
        ("does not own query state", False), ("does not add details strip", False),
        ("poster proportion is portrait", True), ("poster is the whole tile", True),
        ("uses stable item keys", True), ("appropriate horizontal padding", True),
        ("shows games when available", True), ("can open a selected game", True), ("no summary below image", False),
    ],
    "GamePosterCard": [
        ("prefers store poster when supplied", True), ("uses GameArtwork", True), ("respects reduced motion", True),
        ("pressed animation is bounded", True), ("title is rendered as overlay", True), ("tag is bottom-left", True),
        ("uses dark gradient for title readability", True), ("rounded poster tile", True), ("click opens detail", True),
        ("limits overlay width", True), ("caps title lines", True), ("uses translucent title tag", True),
        ("does not render metadata below poster", False), ("does not render game summary", False),
        ("falls back to standard cover", True),
    ],
    "NotificationsScreen": [
        ("loads local notifications", True), ("clear action calls store", True), ("empty notification state", True),
        ("iterates notices", True), ("renders notice title", True), ("renders notice body", True),
        ("uses lazy list", True), ("header contains notification title", True), ("shows local-storage context", True),
        ("has clear button", True), ("clear button disabled when empty", True), ("shows empty-state helper", True),
        ("formats notification timestamp", True), ("uses notification icon", True), ("uses card rows", True),
    ],
}

def structure_checks(name: str, block: str) -> list[tuple[str, bool]]:
    return [
        (f"{name}: function found", bool(block)),
        (f"{name}: marked Composable", block.startswith("@Composable")),
        (f"{name}: declared with expected name", f"fun {name}(" in block),
        (f"{name}: has layout modifiers", "Modifier" in block),
        (f"{name}: emits visible content", ("Text(" in block) or (name == "GameCatalogScreen" and "GamePosterCard" in block)),
        (f"{name}: has a balanced brace count", is_balanced(block)),
        (f"{name}: body is substantial", len(block) > 220),
        (f"{name}: no TODO placeholder", "TODO(" not in block and "TODO:" not in block),
        (f"{name}: no NotImplemented exception", "NotImplementedError" not in block and "NotImplementedException" not in block),
        (f"{name}: no global coroutine scope", "GlobalScope" not in block),
    ]

def check_screen(name: str) -> None:
    global TOTAL
    try:
        block = function_block(MAIN, name)
    except KeyError as exc:
        block = ""
        run(f"{name}: declaration exists", False)
    for label, predicate in GLOBAL_CHECKS:
        run(f"{name}: {label}", predicate(block))
    for label, result in structure_checks(name, block):
        run(label, result)
    checks = SCREEN_CHECKS[name]
    if len(checks) != 15:
        raise SystemExit(f"Internal audit error: {name} has {len(checks)} local checks, expected 15")
    for label, positive in checks:
        tokens = FEATURE_TOKENS[name][label]
        present = all(token in block for token in tokens)
        run(f"{name}: {label}", present if positive else not present)

FEATURE_TOKENS: dict[str, dict[str, tuple[str, ...]]] = {
    "Home": {
        "renders game posters": ("GamePosterCard(game",), "uses image-first card": ("GamePosterCard",),
        "poster selection supported": ("GamePosterCard",), "title tag overlay supported": ("GamePosterCard",),
        "separates news section": ('Section("Notícias"',), "separates video section": ('Section("Vídeos"',),
        "renders video thumbnails": ("VideoThumbnailCard",), "can open game details": ("openGame(game)",),
        "can open stories": ("openStory(story)",), "can open game catalog": ("openCatalog",),
        "has loading state": ("LoadingRow()",), "has empty-state handling": ("Empty(",),
        "limits home game rail": ("state.games.take(12)",), "limits home video rail": ("state.videos.take(8)",),
        "does not use the old metadata card for game rail": ('FeedCard(game, Modifier.width(178.dp)',),
    },
    "FeedScreen": {
        "renders video thumbnails": ("VideoThumbnailCard",), "renders standard feed cards": ("FeedCard(",),
        "shows empty state": ("content.isEmpty()", "Empty("), "checks loading state": ("loading && content.isEmpty()",),
        "uses a lazy list": ("LazyColumn(",), "uses feed item keys": ("items(content, key = { it.id })",),
        "uses page content padding": ("Modifier.fillMaxSize().padding(pad)",), "retains translated page title": ("Text(title,",),
        "does not own a search field": ("OutlinedTextField",), "does not own a query state": ("var query",),
        "does not create a filtered shadow feed": ("val filtered",), "video branch is explicit": ("if (isVideoFeed)",),
        "article branch is explicit": ("FeedCard(item,",), "opens provided item callback": ("open(item)",),
        "supports empty content messaging": ("Não há conteúdo para mostrar agora.",),
    },
    "VideoThumbnailCard": {
        "uses play icon": ("Icons.Outlined.PlayArrow",), "centers play affordance": ("align(Alignment.Center)",),
        "puts title tag bottom-left": ("align(Alignment.BottomStart)",), "renders title in thumbnail": ("Text(item.title",),
        "uses gradient overlay": ("verticalGradient",), "reads reduced-motion preference": ("LocalReduceMotion.current",),
        "press animation exists": ("animateFloatAsState",), "rounded clip card": ("RoundedCornerShape(20.dp)",),
        "renders artwork": ("GameArtwork(item",), "provides play accessibility": ('"Reproduzir vídeo"',),
        "uses white play artwork": ("tint = Color.White",), "uses translucent play backing": ("Color.Black.copy(alpha = .60f)",),
        "does not embed an Android view/player": ("AndroidView",), "does not embed a WebView": ("WebView",),
        "does not autoplay/instantiate YouTube player": ("YouTubePlayer",),
    },
    "GameDetailsScreen": {
        "prominent rating section": ("GameAgeRatingSection(item)",), "wishlist action": ("onToggleWishlist",),
        "availability section": ("GameAvailabilityCard(item)",), "official store URL action": ("item.microsoftStoreUrl",),
        "itch link action": ("item.itchUrl",), "purchase fallback action": ("item.purchaseUrl",),
        "trailer URL action": ("item.trailerUrl",), "renders database body": ("completeBody = item.body",),
        "article byline support": ("item.author",), "article has content heading": ('Text("Artigo completo"',),
        "screenshots section": ("item.screenshots",), "related games section": ('"Jogos relacionados"',),
        "does not add a second About heading": ('Text("Sobre o jogo"',),
        "does not restore metadata list engine row": ('"Motor gráfico:"',), "shows loading feedback": ("if (loading)",),
    },
    "WishlistScreen": {
        "sign-in state": ("state.user == null",), "empty list state": ("state.wishlist.isEmpty()",),
        "iterates remote wishlist": ("items(state.wishlist",), "remove item action": ("remove(game.id)",),
        "sign-in button": ("Button(onClick = signIn)",), "empty-list title": ('"Sua lista está vazia"',),
        "uses lazy list": ("LazyColumn(",), "opens details": ("openGame(game)",), "respects busy state": ("!state.busy",),
        "renders remote cover": ("SubcomposeAsyncImage",), "has remove label": ('"Remover da lista"',),
        "has wishlist title": ('"Lista de desejos"',), "has synchronized-state explanation": ('"Sincronizada com sua conta Deadsmile"',),
        "has account login CTA": ('"Entrar na conta"',), "has empty-list explanation": ('"Adicione jogos aos favoritos pela tela de detalhes."',),
    },
    "LibraryScreen": {
        "sign-in state": ("state.user == null",), "empty library state": ("state.library.isEmpty()",),
        "iterates account library": ("items(state.library",), "links to account": ("Button(onClick = goAccount)",),
        "opens game details": ("openGame(game)",), "has loading state": ('"Carregando sua biblioteca…"',),
        "uses lazy list": ("LazyColumn(",), "cards are clickable": ("Modifier.clickable { openGame(game) }",),
        "renders empty-state helper": ("Empty(",), "has library title": ('"Sua biblioteca"',),
        "has read-only explanation": ('"Jogos vinculados à sua conta · somente consulta"',),
        "has sign-in CTA": ('"Entrar na conta"',), "renders covers": ("SubcomposeAsyncImage",),
        "shows game title": ("Text(game.title",), "has empty/unauthenticated branch": ("if (state.user == null)",),
    },
    "AccountScreen": {
        "renders server avatar": ("AccountAvatar(profile?.avatarUrl",), "saves profile": ("vm.saveProfile(",),
        "email workflow": ("vm.changeEmail(",), "can revoke active session": ("vm.revokeSession(session.id)",),
        "uses account security section": ('"Segurança"',), "uses remote privacy state": ("state.privacy",),
        "hydrates profile fields from state": ("LaunchedEffect(state.accountProfile)",),
        "hydrates privacy state": ("LaunchedEffect(state.privacy)",), "has username editor": ('"Nome de usuário"',),
        "has profile bio editor": ('"Biografia"',), "has website editor": ('"Site pessoal"',),
        "has location editor": ('"Localização"',), "uses account two-factor state": ("state.accountTwoFactorEnabled",),
        "has settings refresh": ("vm::loadAccountSettings",), "does not expose a local avatar photo editor": ("avatarPicker",),
    },
    "SettingsScreen": {
        "user-selectable language callback": ("onAppLanguage(code)",),
        "stores three supported app languages": ('listOf("pt-BR" to "Português (Brasil)", "en" to "English", "es" to "Español")',),
        "theme selection callback": ("onThemeMode(modeIndex)",), "dynamic colors switch": ("onDynamicColors",),
        "reduced motion switch": ("onReduceMotion",), "notification preference component": ("NotificationPreference()",),
        "theme preview": ("ThemeOptionPreview(",), "selected language display": ("selectedLanguageLabel",),
        "has appearance section": ('"Aparência"',), "has language section": ('"Idioma"',),
        "has notifications section": ('"Notificações"',), "has behavior section": ('"Comportamento"',),
        "has updates section": ('"Atualizações"',),
        "does not auto-label blank language as Portuguese": ('else -> "Português (Brasil)"',),
        "shows dynamic colors availability caveat": ("quando disponíveis",),
    },
    "TourStepOverlay": {
        "safe navigation-bar insets": ("windowInsetsPadding(WindowInsets.navigationBars)",),
        "bottom-centered popup": ("Alignment.BottomCenter",), "bottom padding above gesture pill": ("padding(bottom = 4.dp)",),
        "renders localized title through shared Text": ("Text(title,",), "renders description": ("Text(description,",),
        "has next-step instruction": ('"Toque na aba destacada na barra inferior para continuar."',),
        "has skip action": ('"Pular tour"',), "rounded tour card": ("RoundedCornerShape(24.dp)",),
        "respects width cap": ("widthIn(max = 440.dp)",), "has dark scrim": ("Color.Black.copy(alpha = 0.68f)",),
        "uses column layout": ("Column(",), "has card padding": (".padding(20.dp)",),
        "not a full screen replacement": ("Box(",), "content is interactive": ("TextButton(onClick = onSkip",),
        "bounds popup to insets": ("WindowInsets.navigationBars",),
    },
    "GlobalSearchScreen": {
        "search text field": ("OutlinedTextField(",), "placeholder is localized": ('"Buscar jogos, notícias e vídeos"',),
        "includes games": ("state.games.forEach",), "includes library games": ("state.library.forEach",),
        "includes wishlist games": ("state.wishlist.forEach",), "includes news": ("state.news.forEach",),
        "includes videos": ("state.videos.forEach",), "filters titles": ("item.title.contains",),
        "filters summaries": ("item.summary.contains",), "filters article bodies": ("item.body.contains",),
        "filters developer names": ("item.developer.contains",), "renders result heading": ('"Resultados da busca"',),
        "has no-results state": ('"Nenhum resultado encontrado."',), "opens clicked result": ("onOpen(item, kind == \"game\")",),
        "shows content kind": ('"Notícia"', '"Vídeo"'),
    },
    "GameCatalogScreen": {
        "adaptive grid": ("GridCells.Adaptive",), "uses poster card": ("GamePosterCard(game",),
        "iterates content": ("gridItems(content",), "handles loading state": ("loading && content.isEmpty()",),
        "handles empty state": ('Empty("O catálogo não está disponível agora.',),
        "does not own text search": ("OutlinedTextField",), "does not own query state": ("var query",),
        "does not add details strip": ('Text("Ver detalhes"',), "poster proportion is portrait": ("aspectRatio(0.68f)",),
        "poster is the whole tile": ("GamePosterCard(game, Modifier.fillMaxWidth().aspectRatio(0.68f))",),
        "uses stable item keys": ("gridItems(content, key = { it.id })",), "appropriate horizontal padding": ("horizontal = 14.dp",),
        "shows games when available": ("if (content.isEmpty())",), "can open a selected game": ("{ open(game) }",),
        "no summary below image": ("Text(game.summary",),
    },
    "GamePosterCard": {
        "prefers store poster when supplied": ("item.posterImage?.takeIf",), "uses GameArtwork": ("GameArtwork(if (poster != item.image)",),
        "respects reduced motion": ("LocalReduceMotion.current",), "pressed animation is bounded": ("animateFloatAsState",),
        "title is rendered as overlay": ("Text(item.title",), "tag is bottom-left": ("Alignment.BottomStart",),
        "uses dark gradient for title readability": ("verticalGradient",), "rounded poster tile": ("RoundedCornerShape(18.dp)",),
        "click opens detail": ("onClick = open",), "limits overlay width": ("fillMaxWidth(.96f)",),
        "caps title lines": ("maxLines = 2",), "uses translucent title tag": ("Color.Black.copy(alpha = .70f)",),
        "does not render metadata below poster": ("Text(\"Ver detalhes\"",), "does not render game summary": ("Text(item.summary",),
        "falls back to standard cover": ("?: item.image",),
    },
    "NotificationsScreen": {
        "loads local notifications": ("LocalNotificationStore.load(context)",),
        "clear action calls store": ("LocalNotificationStore.clear(context)",), "empty notification state": ("notifications.isEmpty()",),
        "iterates notices": ("items(notifications, key = { it.id })",), "renders notice title": ("Text(notice.title",),
        "renders notice body": ("Text(notice.message",), "uses lazy list": ("LazyColumn(",),
        "header contains notification title": ('"Notificações"',), "shows local-storage context": ('"Salvas neste dispositivo"',),
        "has clear button": ('Text("Limpar")',), "clear button disabled when empty": ("enabled = notifications.isNotEmpty()",),
        "shows empty-state helper": ('"Você ainda não tem notificações.',),
        "formats notification timestamp": ("DateFormat.getDateTimeInstance",), "uses notification icon": ("Icons.Outlined.Notifications",),
        "uses card rows": ("Card(Modifier.fillMaxWidth()",),
    },
}

def main() -> int:
    for name in SCREEN_CHECKS:
        check_screen(name)
    for label, result in RELEASE_CHECKS:
        run("release: " + label, result)
    expected = 50 * len(SCREEN_CHECKS) + len(RELEASE_CHECKS)
    if TOTAL != expected:
        print(f"INTERNAL AUDIT ERROR: ran {TOTAL} checks; expected {expected}.", file=sys.stderr)
        return 2
    if FAILURES:
        print(f"COMPANION DESIGN AUDIT FAILED: {len(FAILURES)} / {TOTAL} checks")
        for failure in FAILURES:
            print(" - " + failure)
        return 1
    print(f"COMPANION DESIGN AUDIT PASSED: {TOTAL} static contracts across {len(SCREEN_CHECKS)} UI areas and {len(RELEASE_CHECKS)} release contracts")
    print("Note: static source checks do not replace emulator/device screenshot and accessibility validation.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
