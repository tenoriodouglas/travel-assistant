# Travel Assistant ✈️📈

App Android nativo que mostra **preços de passagens aéreas como se fossem ativos de um exchange de crypto**: gráficos de velas (candlestick) e linha, feed ao vivo com atualização configurável, e comparação de preço por **companhia aérea** e por **plataforma de compra** para cada par origem→destino.

A ideia é acompanhar a oscilação de preço de uma rota da mesma forma que se acompanha um par de trading — para decidir a melhor hora de comprar.

## Telas

| Mercados | Detalhe da rota | Ajustes |
|----------|-----------------|---------|
| Lista de rotas (os "pares"), com sparkline de tendência, preço e variação. Busca por aeroporto/cidade. | Preço grande, variação, estatísticas (abertura, máx, mín, melhor oferta), gráfico de **velas/linha** com grade de preços, e breakdown por companhia/plataforma ordenado do mais barato ao mais caro (com selo **MELHOR**). | Intervalo do feed ao vivo (slider 5s–300s + presets 10/15/30/60/120s). Padrão **30s**. |

## Visual

Interface *dark-first* inspirada em terminais de trading (Binance-like): fundo `#0B0E11`, verde de alta `#00E7A7`, vermelho de baixa `#FF5C6C`, dourado de destaque `#F0B90B`, números em fonte monoespaçada e indicador **LIVE** pulsante.

## Arquitetura

MVVM + Jetpack Compose, sem frameworks de DI pesados (container manual em `AppContainer`).

```
com.travelassistant.app
├── TravelApp.kt              # Application + AppContainer (DI manual) + DataStore
├── MainActivity.kt           # NavHost + bottom navigation (Mercados / Ajustes)
├── data
│   ├── model/Models.kt       # Route, Candle, PricePoint, Provider, ProviderQuote, RouteMarket, RouteDetail
│   ├── MarketRepository.kt   # Motor do feed ao vivo (random walk por rota e por provider)
│   └── SettingsRepository.kt # Intervalo do feed persistido em DataStore
├── ui
│   ├── theme/                # Paleta, tipografia e tema (estilo trading)
│   ├── components/           # PriceChart (velas/linha), Sparkline, LIVE, chips
│   ├── markets/              # MarketsScreen + MarketsViewModel
│   ├── detail/               # RouteDetailScreen + RouteDetailViewModel
│   ├── settings/             # SettingsScreen + SettingsViewModel
│   └── navigation/           # Destinos e bottom bar
└── util/Format.kt            # Formatação de moeda (pt-BR) e porcentagem
```

### Feed ao vivo

`MarketRepository` mantém, em memória, uma série de candles OHLC e cotações por provider para cada rota. Um único loop de coroutine avança o "tick" no intervalo escolhido pelo usuário (`SettingsRepository.refreshIntervalSeconds` → `flatMapLatest`, então mudar o intervalo reinicia a cadência na hora). Os preços evoluem por um *random walk* limitado.

Os `ViewModel`s expõem `StateFlow`s que as telas coletam com `collectAsStateWithLifecycle`, então cada tick atualiza os gráficos automaticamente.

> **Preços simulados.** Os valores são gerados localmente para demonstrar a interface. A arquitetura é *API-shaped*: trocar a simulação por uma API real de preços de passagens exige mexer basicamente no método `tick()` do `MarketRepository`.

## Como rodar

Pré-requisitos: **Android Studio** (Ladybug ou mais recente) e um device/emulador com **Android 8.0+ (API 26)**.

1. Abra a pasta do projeto no Android Studio (`File > Open`).
2. Deixe o Gradle sincronizar (baixa AGP, Compose e dependências AndroidX).
3. Rode no emulador ou device: **Run 'app'** (`Shift+F10`).

Ou pela linha de comando (com o Android SDK instalado e `ANDROID_HOME`/`local.properties` configurados):

```bash
./gradlew assembleDebug        # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # instala em um device conectado
```

## Stack

- Kotlin 2.1 · Jetpack Compose (BOM 2024.12) · Material 3
- Navigation Compose · Lifecycle ViewModel · Coroutines/Flow
- DataStore Preferences (persistência do intervalo)
- Gráficos desenhados na mão com `Canvas` do Compose (sem libs externas de chart)
- `compileSdk 35` · `minSdk 26` · `targetSdk 35`
