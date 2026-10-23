# Roadmap

## Acceptance gate for the current foundation

1. Install/start Docker Desktop with explicit user approval; inspect disk-image placement before the first build.
2. Run Compose configuration/build/start, PostgreSQL migration/integrity tests, Swagger, all CRUD/persistence workflows and database restart tests.
3. Configure a real provider key privately, ingest a small US watchlist manually, and validate dates/OHLC/volumes/provider failure handling. Audit splits/dividends and the compact-window limitation before model research.
4. Measure runtime/build resource usage on the 8 GB host and tune within the conservative limits.

## Phase 2: data quality and ML research

Add reliable adjusted data or corporate-action handling, explicit dataset versions and feature schemas. Implement temporal train/validation/test splits with walk-forward validation, leakage checks and simple baselines before XGBoost. Define model artifact serialization/trust, reproducibility and calibrated probability evaluation. Real inference must expose dataset freshness, model version, timestamp and measured limitations.

## Phase 3: strategy execution / backtesting

Implement buy-and-hold and lagged moving-average crossover first. Define fills, transaction costs, slippage, price adjustment, exchange calendar and benchmark semantics. Produce equity curves and trade ledgers from executed runs; verify against hand-computable fixtures. Add ML strategies only after model acceptance. Extend risk analytics to aligned portfolio histories and covariance; design FX explicitly.

## Phase 4: MLOps

Add optional MLflow tracking, reproducible training pipelines, versioned artifacts, evaluation/promotion gates, rollback and drift monitoring. Avoid automatic model promotion based on in-sample scores. Keep heavy services opt-in on an 8 GB development machine.

## Phase 5: deployment / observability

After authentication, authorization, configuration/secrets hardening and production threat review, consider CI delivery, optional Prometheus/Grafana and Kubernetes. Kubernetes, Redis, remote artifacts, model scheduling and brokerage/real-money trading are outside this foundation. Indian markets require a provider adapter, reliable symbol/currency/calendar conventions and real compatibility tests before support is claimed.
