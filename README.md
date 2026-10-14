# Deadsmile Mobile

Aplicativo Android **nativo** da Deadsmile Games, desenvolvido em **Kotlin**, **Jetpack Compose** e **Material 3 (Material You)**.

## Recursos

- Notícias e feed editorial da Deadsmile Games.
- Catálogo de vídeos.
- Catálogo de jogos.
- Biblioteca da conta autenticada em modo somente leitura: mostra os jogos da biblioteca, sem executar jogos no celular.
- Gerenciamento de conta e autenticação.
- Cores dinâmicas do Android 12+, com suporte aos temas claro e escuro.
- Tráfego HTTPS obrigatório e sem tráfego cleartext.
- Sessão armazenada usando Android Keystore; operações mutáveis autenticadas devem incluir proteção CSRF.

## API

API base: `https://api-ust8.onrender.com/api`

O app utiliza os endpoints disponíveis para notícias, vídeos, catálogo, biblioteca e conta. A biblioteca móvel é somente leitura.

## Build e APK

O workflow [Android APK](https://github.com/DeadsmileGames/mobile-app/actions/workflows/android-apk.yml) executa testes unitários, compila o APK debug e publica `app-debug.apk` como artefato do GitHub Actions.

1. Abra a execução mais recente em [Actions](https://github.com/DeadsmileGames/mobile-app/actions/workflows/android-apk.yml).
2. Selecione o artefato `Deadsmile-Mobile-debug-APK` para baixar o APK.
3. Instale o APK no Android. Por ser um build debug, pode ser necessário permitir a instalação para o app usado para abrir o arquivo.

O APK de debug é assinado com a chave de depuração do Android e **não é uma build de release assinada para distribuição pública**.

## Requisitos de desenvolvimento

- JDK 17
- Gradle 8.13
- Android SDK compatível com compileSdk 35

Com Gradle instalado, execute na raiz do repositório:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```
