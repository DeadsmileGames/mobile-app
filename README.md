# Deadsmile Mobile

Aplicativo Android nativo da Deadsmile Games, em Kotlin + Jetpack Compose e Material 3 (Material You).

- Notícias: `GET /api/newswire`
- Vídeos: `GET /api/videos`
- Catálogo: `GET /api/games`
- Biblioteca autenticada em modo somente leitura: `GET /api/library` (sem executar jogos)
- Conta: login, cadastro, autenticação em duas etapas, edição do nome de usuário e logout.
- Tema dinâmico Android 12+, claro/escuro do sistema.
- Sessão em armazenamento criptografado Android Keystore; operações mutáveis usam token CSRF.
- Tráfego HTTPS obrigatório.
- GitHub Actions compila e publica o APK debug como artifact.

API oficial: `https://api-ust8.onrender.com/api`.

O APK de debug usa a chave de depuração padrão do Android; não contém credenciais privadas de release. JDK 17 e Gradle 8.13:
`gradle testDebugUnitTest assembleDebug`.
