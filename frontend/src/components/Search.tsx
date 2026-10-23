import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Search as SearchIcon } from 'lucide-react';
import { api } from '../services/api';
export default function Search() {
 const [q,setQ] = useState(''),[debounced,setDebounced]=useState(''),[open,setOpen]=useState(false);const navigate=useNavigate();
 useEffect(() => { const t=setTimeout(() => setDebounced(q.trim()),300);return () => clearTimeout(t); },[q]);
 const result=useQuery({queryKey:['search',debounced],queryFn:({signal})=>api.assets(debounced,0,signal),enabled:open&&debounced.length>0});
 return <div className="global-search" onBlur={e => { if(!e.currentTarget.contains(e.relatedTarget)) setOpen(false); }}><SearchIcon size={17}/><input aria-label="Search stocks" placeholder="Search assets, symbols…" value={q} onFocus={()=>setOpen(true)} onChange={e=>{setQ(e.target.value);setOpen(true);}}/><span className="search-hint">TICKER</span>{open&&q.trim()&&<div className="search-results">{result.isFetching ? <p role="status">Searching…</p> : result.isError ? <p role="alert">{result.error.message}</p> : !result.data?.items.length ? <p>No matching assets. Register a US ticker in Markets.</p> : result.data.items.slice(0,7).map(a=><button key={a.id} onClick={()=>{navigate(`/explorer?asset=${a.id}`);setOpen(false);setQ('');}}><strong>{a.symbol}</strong><span>{a.company_name}</span><small>{a.exchange}</small></button>)}</div>}</div>;
}
