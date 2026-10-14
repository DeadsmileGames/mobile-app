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

## Distribuição assinada e atualizações

O app verifica as releases públicas deste repositório ao abrir e inclui uma tela **Atualizações** com o histórico, as notas, a data e os APKs publicados. Uma atualização só é oferecida quando a versão da release é superior à instalada e o APK pertence ao pacote esperado e tem a mesma assinatura da instalação atual. O Android sempre apresenta a confirmação de instalação ao usuário; o Companion não instala pacotes silenciosamente.

O workflow [Release Android](https://github.com/DeadsmileGames/mobile-app/actions/workflows/release-android.yml) publica builds de produção ao receber uma tag semântica, por exemplo `v1.1.1`. Antes da primeira release, configure estes GitHub Actions secrets em **Settings → Secrets and variables → Actions**:

- `ANDROID_RELEASE_KEYSTORE_BASE64`: keystore de release codificado em Base64.
- `ANDROID_RELEASE_STORE_PASSWORD`: senha do keystore.
- `ANDROID_RELEASE_KEY_ALIAS`: alias da chave.
- `ANDROID_RELEASE_KEY_PASSWORD`: senha da chave.

Crie a chave em um ambiente seguro e guarde uma cópia de backup fora do repositório. Por exemplo, com JDK instalado:

```sh
keytool -genkeypair -v -keystore deadsmile-companion-release.jks -alias deadsmile-companion -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 deadsmile-companion-release.jks
```

Copie a saída Base64 para o secret correspondente; **nunca** faça commit do keystore, senhas ou do conteúdo Base64. Use exatamente a mesma chave para todas as versões futuras. O workflow falha intencionalmente se os secrets não estiverem configurados, e o APK debug de Actions não pode atualizar a build assinada de produção. As releases disponíveis serão exibidas no Companion assim que forem publicadas.

