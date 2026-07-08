# Publicação automática na Google Play

O workflow [`.github/workflows/deploy-play-store.yml`](../.github/workflows/deploy-play-store.yml) gera um **AAB assinado** e o envia para a **Play Store (track `alpha` — teste fechado)** a cada merge na branch `main`.

Enquanto os secrets abaixo não estiverem configurados, o workflow **não falha**: ele compila o AAB e o disponibiliza como *artifact* do run, apenas pulando o envio para a loja.

## 1. Gerar a upload key (uma vez)

```bash
keytool -genkey -v -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Guarde o arquivo `.jks` e as senhas em local seguro (não versione — o `.gitignore` já bloqueia `*.jks` e `key.properties`).

Gere o base64 do keystore para colar no secret:

```bash
base64 -w0 upload-keystore.jks   # Linux
base64 -i upload-keystore.jks    # macOS
```

## 2. Service account da Google Play (uma vez)

1. No [Google Cloud Console](https://console.cloud.google.com/), habilite a **Google Play Android Developer API**.
2. Crie um **service account** e gere uma chave **JSON**.
3. No [Play Console](https://play.google.com/console) → **Configuração → Acesso à API**, vincule esse service account e conceda permissão de **liberar em faixas de teste** (releases) para o app.

## 3. Secrets do repositório

Em **Settings → Secrets and variables → Actions**, crie:

| Secret | Conteúdo |
|--------|----------|
| `KEYSTORE_BASE64` | base64 do `upload-keystore.jks` (passo 1) |
| `KEYSTORE_PASSWORD` | senha do keystore (`storePassword`) |
| `KEY_PASSWORD` | senha da key (`keyPassword`) |
| `KEY_ALIAS` | alias da key (ex.: `upload`) |
| `PLAY_SERVICE_ACCOUNT_JSON` | conteúdo **inteiro** do JSON do service account |

## 4. Primeiro envio manual (obrigatório)

A API da Play **não cria o app** nem aceita o primeiríssimo upload. Antes de o CD funcionar:

1. Crie o app no Play Console com o `applicationId` **`com.travelassistant.app`**.
2. Faça **um** upload manual de um AAB assinado com a mesma upload key (pode ser o artifact gerado por um run do workflow) na faixa desejada e preencha os dados obrigatórios da ficha.
3. A partir daí, todo merge na `main` publica automaticamente na faixa `alpha`.

## Detalhes do workflow

- **Gatilho:** `push` na `main` (ou seja, todo PR mergeado) — e `workflow_dispatch` para rodar manualmente.
- **`versionCode`:** injetado como `github.run_number + 100` via `-PversionCodeOverride`, garantindo um número sempre crescente. O `versionName` continua em `app/build.gradle.kts`.
- **Faixa:** `alpha` (teste fechado). Para produção, troque `tracks: alpha` por `tracks: production` no workflow (recomendado só depois de validar em teste).
- **Assinatura:** o `app/build.gradle.kts` lê `key.properties` na raiz; o workflow o escreve a partir dos secrets. Sem keystore, o build cai para a debug key (AAB não publicável, mas útil para inspeção).
