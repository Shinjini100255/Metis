import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, RefreshCw } from 'lucide-react';
import { api } from '../services/api';
import { useToast } from './Toast';
import type { Asset } from '../types/api';
export function AssetPicker({ assets, id, onChange }: { assets: Asset[]; id?: number; onChange: (id:number)=>void }) {return <select aria-label="Select stock" value={id||''} onChange={e=>onChange(Number(e.target.value))}>{!assets.length&&<option value="">No assets available</option>}{assets.map(a=><option value={a.id} key={a.id}>{a.symbol} · {a.company_name}</option>)}</select>;}
export function IngestButton({ id }: { id?: number }) {
 const toast=useToast(),client=useQueryClient();const mutation=useMutation({mutationFn:()=>api.ingest(id!),onSuccess:r=>{toast(r.message);if(r.status==='SUCCESS') {client.invalidateQueries({queryKey:['prices']});client.invalidateQueries({queryKey:['indicators']});client.invalidateQueries({queryKey:['quotes']});client.invalidateQueries({queryKey:['portfolio']});}client.invalidateQueries({queryKey:['status']});client.invalidateQueries({queryKey:['ingestion']});},onError:e=>toast(e.message)});
 return <button className="primary" disabled={!id||mutation.isPending} onClick={()=>mutation.mutate()}>{mutation.isPending?<RefreshCw className="spin" size={15}/>:<Download size={15}/>} {mutation.isPending?'Fetching history…':'Fetch history'}</button>;
}
