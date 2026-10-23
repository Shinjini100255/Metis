import type { Asset, Page, Price, Quote, Indicators, Watchlist, Portfolio, ModelStatus, Model, Prediction, Strategy, Backtest, SystemStatus, Ingestion } from '../types/api';
export class ApiError extends Error { constructor(public status: number, message: string, public code = 'NETWORK_ERROR') { super(message); } }
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    const response = await fetch('/api/v1' + path, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } });
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new ApiError(response.status, body.message || (response.status >= 500 ? 'The backend is unavailable. Check that Spring Boot and PostgreSQL are running, then retry.' : `API returned HTTP ${response.status}.`), body.code); }
    return response.status === 204 ? undefined as T : await response.json() as T;
  } catch (error) { if (error instanceof ApiError || (error instanceof DOMException && error.name === 'AbortError')) throw error; throw new ApiError(0, 'Cannot reach the METIS backend. Check that Spring Boot and PostgreSQL are running.'); }
}
const post = <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
export const api = {
 asset: (id: number, signal?: AbortSignal) => request<Asset>(`/assets/${id}`, { signal }),
 assets: (q = '', page = 0, signal?: AbortSignal) => request<Page<Asset>>(`/assets/search?q=${encodeURIComponent(q)}&page=${page}&size=100`, { signal }),
 addAsset: (symbol: string, company_name: string) => post<Asset>('/assets', { symbol, company_name }),
 prices: (id: number, from: string, to: string, signal?: AbortSignal) => request<Page<Price>>(`/assets/${id}/prices?from=${from}&to=${to}&size=1260`, { signal }),
 indicators: (id: number, from: string, to: string, signal?: AbortSignal) => request<Indicators>(`/assets/${id}/indicators?from=${from}&to=${to}`, { signal }),
 quotes: (signal?: AbortSignal) => request<Quote[]>('/quotes', { signal }),
 ingest: (id: number) => post<{ status: string; records_inserted: number; message: string }>(`/assets/${id}/ingest`),
 watchlists: (signal?: AbortSignal) => request<Watchlist[]>('/watchlists', { signal }),
 createWatchlist: (name: string) => post<Watchlist>('/watchlists', { name }),
 watch: (id: number, asset_id: number) => post<void>(`/watchlists/${id}/assets`, { asset_id }),
 unwatch: (id: number, asset: number) => request<void>(`/watchlists/${id}/assets/${asset}`, { method: 'DELETE' }),
 portfolios: (signal?: AbortSignal) => request<Portfolio[]>('/portfolios', { signal }),
 portfolio: (id: number, signal?: AbortSignal) => request<Portfolio>(`/portfolios/${id}`, { signal }),
 createPortfolio: (name: string, base_currency: string) => post<Portfolio>('/portfolios', { name, base_currency }),
 deletePortfolio: (id: number) => request<void>(`/portfolios/${id}`, { method: 'DELETE' }),
 saveHolding: (id: number, body: { asset_id: number; quantity: string; average_purchase_price: string }, holding?: number) => request<void>(`/portfolios/${id}/holdings${holding ? '/' + holding : ''}`, { method: holding ? 'PUT' : 'POST', body: JSON.stringify(body) }),
 deleteHolding: (id: number, holding: number) => request<void>(`/portfolios/${id}/holdings/${holding}`, { method: 'DELETE' }),
 modelOperations: (signal?: AbortSignal) => request<{training_runs: unknown[]; evaluations: unknown[]; deployment_status: string}>('/models/operations', { signal }),
 models: (signal?: AbortSignal) => request<Model[]>('/models', { signal }),
 modelStatus: (signal?: AbortSignal) => request<ModelStatus>('/models/status', { signal }),
 predictions: (asset?: number, signal?: AbortSignal) => request<Prediction[]>('/predictions' + (asset ? `?assetId=${asset}` : ''), { signal }),
 strategies: (signal?: AbortSignal) => request<Strategy[]>('/strategies', { signal }),
 backtests: (signal?: AbortSignal) => request<Backtest[]>('/backtests', { signal }),
 status: (signal?: AbortSignal) => request<SystemStatus>('/system/status', { signal }),
 ingestion: (signal?: AbortSignal) => request<Ingestion[]>('/system/ingestion', { signal }),
};
