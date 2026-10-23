import { createContext, useContext, useState, useEffect } from 'react';
export interface Preferences { theme: 'dark' | 'light'; market: 'US' | 'IN'; chart: 'line' | 'candlestick'; refresh: number; watchlist: number | null }
export const defaults: Preferences = { theme: 'dark', market: 'US', chart: 'line', refresh: 0, watchlist: null };
export function readPreferences(): Preferences {
 try { const p = JSON.parse(localStorage.getItem('metis-preferences') || '{}'); return { theme: p.theme === 'light' ? 'light' : 'dark', market: p.market === 'IN' ? 'IN' : 'US', chart: p.chart === 'candlestick' ? 'candlestick' : 'line', refresh: [0,300000,900000].includes(p.refresh) ? p.refresh : 0, watchlist: Number.isSafeInteger(p.watchlist) && p.watchlist > 0 ? p.watchlist : null }; } catch { return defaults; }
}
export const PreferenceContext = createContext<{ preferences: Preferences; update: (p: Partial<Preferences>) => void }>({ preferences: defaults, update: () => {} });
export function usePreferenceState() {
 const [preferences, set] = useState(readPreferences);
 useEffect(() => { document.documentElement.dataset.theme = preferences.theme; try { localStorage.setItem('metis-preferences', JSON.stringify(preferences)); } catch { /* Browser storage can be disabled. */ } }, [preferences]);
 return { preferences, update: (p: Partial<Preferences>) => set(old => ({ ...old, ...p })) };
}
export const usePreferences = () => useContext(PreferenceContext);
