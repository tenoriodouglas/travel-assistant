# Dados reais de preço — Travelpayouts (grátis) ⭐ recomendado

O app funciona **out-of-the-box no modo simulado**. Para ligar **preços reais de graça**, o caminho mais fácil é o **Travelpayouts** (dados do Aviasales): cadastro simples, **sem OAuth**, só um token.

## Por que Travelpayouts

- **Gratuito** e **self-service** — cadastro em minutos, sem aprovação comercial.
- Endpoint **Prices for Dates** devolve os bilhetes mais baratos por **data de embarque** — casa direto com o gráfico de preço por data.
- Traz o código da **companhia aérea** de cada bilhete → breakdown real por cia, sem chamada extra.

> Skyscanner e Skiplagged **não têm** API gratuita self-service (Skyscanner é só parceiro aprovado; Skiplagged não tem API pública). Amadeus também é grátis (test), mas o cadastro é mais chato — veja `REAL_DATA_AMADEUS.md` se preferir.

## 1. Criar o token (grátis)

1. Crie conta em https://www.travelpayouts.com (é um programa de afiliados, gratuito).
2. No painel, vá em **Developers / API** (ou "Token de API") e copie o **API token**.

## 2. Configurar (sem commitar)

O token é lido de `local.properties` (que o `.gitignore` já bloqueia) ou de variável de ambiente — **nunca do código-fonte**.

`local.properties` na raiz do projeto:

```properties
travelpayouts.token=SEU_TOKEN_AQUI
```

Ou por variável de ambiente (CI):

```bash
export TRAVELPAYOUTS_TOKEN=...
```

Recompile. Com o token presente, o app passa a usar dados reais automaticamente (badge **DADOS REAIS**). Sem token → **SIMULADO**.

## Prioridade dos provedores

O `AppContainer` escolhe nesta ordem:

1. **Travelpayouts** (se `travelpayouts.token` existir) — grátis, recomendado
2. **Amadeus** (se as chaves existirem)
3. **Simulação** (padrão, sem nenhuma chave)

## Como funciona no app

- Ao escolher origem/destino/período, o `TravelpayoutsPriceRepository` busca os bilhetes mais baratos por mês na janela, agrupa por **data de embarque** (menor preço do dia) e monta a curva + as velas. O breakdown por companhia vem dos mesmos bilhetes.
- É **sob demanda** (não é feed de 30s). O botão **atualizar** força novo fetch.
- Se a API falhar ou não cobrir a rota, o app **cai para a simulação** com um aviso — nada quebra.

## Limitações (honestas)

- Os preços são **em cache** (de buscas do Aviasales), não cotação de compra ao vivo. Ótimo em rotas populares; pode faltar dado em rotas obscuras (aí entra o fallback).
- Cota gratuita generosa, mas evite abrir dezenas de rotas em sequência.
- Para produção séria, o ideal é intermediar as chamadas por um **backend próprio** (o token não deveria viver no APK). Aqui vai via `BuildConfig` por simplicidade de demonstração.
