export interface Asset { id: number; symbol: string; company_name: string; exchange: string; currency: string; asset_type: string }
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface Price { trading_date: string; open: number; high: number; low: number; close: number; volume: number; provider: string; ingested_at: string; adjusted_close: number | null }
export interface Quote { asset_id: number; symbol: string; company_name: string; exchange: string; currency: string; close: number | null; volume: number | null; trading_date: string | null; change_percent: number | null; provider: string | null }
export interface IndicatorPoint { trading_date: string; sma20: number | null; sma50: number | null; rsi14: number | null; daily_return: number | null }
export interface Indicators { points: IndicatorPoint[]; annualized_volatility: number | null; sharpe_ratio: number | null; maximum_drawdown: number | null; observations: number; methodology: string }
export interface Watchlist { id: number; name: string; assets: Asset[] }
export interface Holding { id: number; asset_id: number; symbol: string; quantity: number; average_purchase_price: number; currency: string; latest_price: number | null; price_date: string | null; invested_capital: number; position_value: number | null; unrealized_pl: number | null; valuation_status: string }
export interface Portfolio { id: number; name: string; base_currency: string; holdings?: Holding[]; invested_capital: number | null; market_value: number | null; known_value: number; unrealized_pl: number | null; unvalued_positions: number; currency_mismatches: number }
export interface ModelStatus { available: boolean; status: string; model_version?: string | null; message: string }
export interface Model { id: number; model_name: string; version: string; status: string; created_at: string }
export interface Prediction { id: number; asset_id: number; predicted_value: number; probability: number | null; prediction_target_date: string; created_at: string; model_version_id: number }
export interface Strategy { id: number; name: string; strategy_type: string; configuration: string | Record<string, number>; readiness: string }
export interface Backtest { id: number; status: string; start_date: string; end_date: string; initial_capital: number }
export interface ServiceHealth { status: string; response_ms?: number }
export interface SystemStatus { spring: ServiceHealth; postgres: ServiceHealth; ml: ServiceHealth; provider: { name: string; status: string; configured: boolean; data_type: string; last_request?: { status: string; completed_at: string; error_message: string | null } | null }; last_successful_ingestion: { completed_at: string; records_inserted: number } | null; environment: string; timestamp: string }
export interface Ingestion { id: number; symbol: string; provider: string; status: string; started_at: string; completed_at: string | null; records_inserted: number; error_message: string | null }
