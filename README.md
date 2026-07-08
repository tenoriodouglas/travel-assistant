# Travel Assistant ✈️📈

App Android nativo que mostra **preços de passagens aéreas como se fossem ativos de um exchange de crypto**: gráficos de velas (candlestick) e linha, feed ao vivo com atualização configurável, e comparação de preço por **companhia aérea** e por **plataforma de compra** para cada par origem→destino.

A ideia é acompanhar a oscilação de preço de uma rota da mesma forma que se acompanha um par de trading — para decidir a melhor hora de comprar.

## Telas

| Preços (Home) | Ajustes |
|---------------|---------|
| Campo **Origem** + campo **Destino** com **autocomplete** (digita a cidade → sugestões → clica). Botões de **período futuro** estilo cripto (7D / 15D / 1M / 3M / 6M / 1A) = janela de datas de embarque. O gráfico de **velas/linha** mostra o **preço por data de embarque** e atualiza automático ao trocar rota/período, com breakdown por companhia/plataforma (selo **MELHOR** no mais barato). | Intervalo do feed ao vivo (slider 5s–300s + presets). Padrão **30s**. |

Cada tick do feed re-samplea os preços, então a curva inteira oscila ao vivo — a sensação de terminal de trade, aplicada a datas futuras de voo.

## Visual

Interface *dark-first* inspirada em terminais de trading (Binance-like): fundo `#0B0E11`, verde de alta `#00E7A7`, vermelho de baixa `#FF5C6C`, dourado de destaque `#F0B90B`, números em fonte monoespaçada e indicador **LIVE** pulsante.

## Arquitetura

MVVM + Jetpack Compose, sem frameworks de DI pesados (container manual em `AppContainer`).

```
com.travelassistant.app
├── TravelApp.kt              # Application + AppContainer (DI manual) + DataStore
├── MainActivity.kt           # NavHost + bottom navigation (Preços / Ajustes)
├── data
│   ├── model/Models.kt       # Airport, Route, TimeRange, Candle, Provider, ProviderQuote, RouteBoard
│   ├── Airports.kt           # Catálogo de aeroportos + busca (autocomplete, sem acento)
│   ├── MarketRepository.kt   # Motor do feed: curva de preço por data futura, por par, ao vivo
│   └── SettingsRepository.kt # Intervalo do feed persistido em DataStore
├── ui
│   ├── theme/                # Paleta, tipografia e tema (estilo trading)
│   ├── components/           # PriceChart, Sparkline, AirportSearchField, RangeSelector, LIVE, chips
│   ├── home/                 # HomeScreen (busca + gráfico) + HomeViewModel
│   ├── settings/             # SettingsScreen + SettingsViewModel
│   └── navigation/           # Destinos e bottom bar
└── util/Format.kt            # Formatação de moeda (pt-BR) e porcentagem
```

### Feed ao vivo & dados

Para cada par origem→destino que o usuário abre, o `MarketRepository` constrói (sob demanda) uma curva de preço **por dia de embarque futuro** (baseline sazonal + *random walk* limitado) e a avança a cada *tick*, no intervalo configurável (`SettingsRepository.refreshIntervalSeconds` → `flatMapLatest`). O `board(origem, destino, período)` agrupa esses dias em candles OHLC. O `HomeViewModel` combina origem+destino+período+tick e recalcula o board ao vivo.

> **Preços simulados — não há API real conectada.** Não existe API pública oficial do Google Flights (a QPX Express foi encerrada em 2018). Para dados reais, as opções são **Amadeus Self-Service** (oficial, free tier, tem preço por data futura), **SerpApi – Google Flights** (não oficial, faz scraping) ou **Travelpayouts/Aviasales**. Como preço de passagem não muda a cada 30s e essas APIs têm limite de requisições, com dados reais o "feed ao vivo" vira *polling* periódico + histórico salvo. Plugar uma dessas APIs troca basicamente como `PairState.daily` é populado.

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
