import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api } from '../services/api';
export function dateAgo(days: number) { const d = new Date(); d.setUTCDate(d.getUTCDate() - days); return d.toISOString().slice(0,10); }
export function useMarket() {
 const [params,setParams] = useSearchParams();
 const assets = useQuery({ queryKey: ['assets'], queryFn: ({ signal }) => api.assets('',0,signal) });
 const id = Number(params.get('asset')) || assets.data?.items[0]?.id;
 const listed = assets.data?.items.find(a => a.id === id);
 const selected = useQuery({ queryKey: ['asset',id], queryFn: ({ signal }) => api.asset(id!,signal), enabled: !!id && !listed && !assets.isPending });
 const asset = listed || selected.data;
 const from = params.get('from') || dateAgo(365), to = params.get('to') || dateAgo(0);
 const prices = useQuery({ queryKey: ['prices',id,from,to], queryFn: ({ signal }) => api.prices(id!,from,to,signal), enabled: !!id });
 const indicators = useQuery({ queryKey: ['indicators',id,from,to], queryFn: ({ signal }) => api.indicators(id!,from,to,signal), enabled: !!id });
 const select = (assetId: number) => setParams(old => { const next = new URLSearchParams(old); next.set('asset',String(assetId)); return next; });
 const range = (start: string,end: string) => setParams(old => { const next = new URLSearchParams(old); next.set('from',start); next.set('to',end); return next; });
 return { assets,id,asset,from,to,prices,indicators,select,range };
}
