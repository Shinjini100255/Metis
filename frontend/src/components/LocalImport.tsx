import { useState } from 'react';
import { useMutation,useQueryClient } from '@tanstack/react-query';
import { api } from '../services/api';
import { useToast } from './Toast';
import { Panel,ErrorState } from './UI';
export default function LocalImport({id}:{id?:number}) {
 const [file,setFile]=useState<File|null>(null),toast=useToast(),client=useQueryClient();
 const mutation=useMutation({mutationFn:async()=>{
  if(!file||!id)throw new Error('Choose an asset and a local CSV first.');
  if(file.size>1024*1024)throw new Error('Choose a CSV of at most 1 MB and 5000 rows.');
  const lines=(await file.text()).trim().split(/\r?\n/),headers=lines.shift()!.replace(/^\uFEFF/,'').split(',').map(x=>x.trim());
  const required=['trading_date','open','high','low','close','volume'];
  if(headers.join(',')!==required.join(','))throw new Error('CSV header must be trading_date,open,high,low,close,volume. Use plain values without quoted commas.');
  const bars=lines.map(line=>{const values=line.split(',').map(x=>x.trim());if(values.length!==6)throw new Error('Every CSV row must contain six plain values.');return Object.fromEntries(headers.map((h,i)=>[h,values[i]]));});
  return api.importLocal(id,bars);
 },onSuccess:r=>{toast(`Local import: ${r.records_inserted} inserted, ${r.records_skipped} skipped, ${r.records_rejected} rejected.`);['coverage','prices','indicators','quotes','features','ingestion'].forEach(key=>client.invalidateQueries({queryKey:[key]}));},onError:e=>toast(e.message)});
 return <Panel title="Import local market history"><p className="section-description">Use genuine unadjusted US daily OHLCV data for the selected asset. Existing records are preserved. Missing prices are never filled. Synthetic fixtures belong only in an isolated test environment.</p><form className="form-row" onSubmit={e=>{e.preventDefault();mutation.mutate();}}><label>Daily OHLCV CSV<input aria-label="Local OHLCV CSV" type="file" accept=".csv,text/csv" onChange={e=>setFile(e.target.files?.[0]||null)}/></label><button disabled={!file||!id||mutation.isPending}>{mutation.isPending?'Validating…':'Validate & import local CSV'}</button></form><small>Header: trading_date,open,high,low,close,volume · 5000 rows / 1 MB maximum · source: Local CSV</small>{mutation.isError&&<ErrorState error={mutation.error}/>}</Panel>;
}
