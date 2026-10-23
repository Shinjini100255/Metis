import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, RefreshCw } from 'lucide-react';
import { api } from '../services/api';
import { useToast } from './Toast';
import type { Asset } from '../types/api';
export function AssetPicker({ assets, id, onChange }: { assets: Asset[]; id?: number; onChange: (id:number)=>void }) {return <select aria-label="Select stock" value={id||''} onChange={e=>onChange(Number(e.target.value))}>{!assets.length&&<option value="">No assets available</option>}{assets.map(a=><option value={a.id} key={a.id}>{a.symbol} · {a.company_name}</option>)}</select>;}
export function IngestButton({ id }: { id?: number }) {
 const toast=useToast(),client=useQueryClient();const mutation=useMutation({mutationFn:()=>api.plan(id!),onSuccess:r=>{toast(`Dry run: ${r.estimated_requests} estimated remote request(s). ${r.external_requests_made} sent. Live ingestion ${r.protection.live_enabled?'enabled; explicit authorization still required':'disabled'}. Compact history covers the latest 100 sessions only.`);client.invalidateQueries({queryKey:['protection']});},onError:e=>toast(e.message)});
 return <button className="primary" disabled={!id||mutation.isPending} onClick={()=>mutation.mutate()}>{mutation.isPending?<RefreshCw className="spin" size={15}/>:<Download size={15}/>} {mutation.isPending?'Planning…':'Plan ingestion'}</button>;
}
