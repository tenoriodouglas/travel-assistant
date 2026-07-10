# Dados reais de preço — Amadeus Self-Service

O app funciona **out-of-the-box no modo simulado**. Para ligar **preços reais**, basta fornecer credenciais da **Amadeus for Developers** — nenhuma linha de código precisa mudar.

## Por que Amadeus

- **Oficial** e com **free tier** (ambiente `test`, ~2.000 chamadas/mês por API).
- Tem os endpoints que o app usa:
  - **Flight Cheapest Date Search** (`/v1/shopping/flight-dates`) — preço mais barato por **data de embarque futura** (uma chamada cobre várias datas).
  - **Flight Offers Search** (`/v2/shopping/flight-offers`) — ofertas por **companhia aérea** para uma data (usado no breakdown e como fallback quando a rota não é coberta pelo primeiro endpoint).

> Não existe API pública oficial do **Google Flights** (a QPX Express foi encerrada em 2018). Alternativas ao Amadeus seriam SerpApi (scraping do Google Flights, não oficial) ou Travelpayouts.

## 1. Criar as credenciais

1. Crie uma conta em https://developers.amadeus.com.
2. Em **Self-Service → My Self-Service Workspace → Create New App**.
3. Copie a **API Key** (client id) e o **API Secret** (client secret).
4. As chaves nascem no ambiente **test** (sandbox, grátis). Para produção, crie um app de produção e troque `amadeus.env` para `production`.

## 2. Configurar as chaves (sem commitar)

As chaves são lidas de `local.properties` (que o `.gitignore` já bloqueia) ou de variáveis de ambiente — **nunca do código-fonte**.

`local.properties` na raiz do projeto:

```properties
amadeus.clientId=SUA_API_KEY
amadeus.clientSecret=SEU_API_SECRET
amadeus.env=test
```

Ou por variáveis de ambiente (útil no CI):

```bash
export AMADEUS_CLIENT_ID=...
export AMADEUS_CLIENT_SECRET=...
export AMADEUS_ENV=test
```

Recompile o app. Se as chaves estiverem presentes, o app passa a buscar dados reais automaticamente (badge **DADOS REAIS** no topo); sem chaves, mostra **SIMULADO**.

## Como funciona no app

- Ao escolher origem/destino/período, o `AmadeusPriceRepository` busca a curva de preço por data futura e o breakdown por companhia. É **sob demanda** (não um feed de 30s — preço de passagem não muda a cada 30s e a API tem cota).
- O botão de **atualizar** (topo) força um novo fetch.
- Se a API falhar ou não cobrir a rota, o app **cai automaticamente para a simulação** e mostra um aviso, sem quebrar.

## Limitações do ambiente `test`

- Cobertura de rotas/datas é **parcial** (dados em cache). Algumas rotas retornam vazio no `test` — nesse caso o app usa o fallback de amostragem por data e, se ainda assim vier vazio, a simulação.
- Cotas mensais limitadas. Evite abrir muitas rotas em sequência.
- Para cobertura e preços completos, use credenciais de **produção** (`amadeus.env=production`).

## Segurança

- **Nunca** commite `local.properties` nem as chaves.
- Em apps de produção reais, o ideal é intermediar as chamadas por um **backend próprio** (as chaves não deveriam viver no APK). Aqui elas vão via `BuildConfig` por simplicidade de demonstração.
