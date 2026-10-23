import { useQuery } from '@tanstack/react-query';
import { BrainCircuit } from 'lucide-react';
import { api } from '../services/api';
import { useMarket } from '../hooks/useMarket';
import { AssetPicker } from '../components/MarketTools';
import { PageTitle,Panel,Metric,Empty,Loading,ErrorState,PhaseNote,Badge } from '../components/UI';
import { number,percent,time } from '../utils/format';
export default function Predictions() {
 const m=useMarket();const status=useQuery({queryKey:['model-status'],queryFn:({signal})=>api.modelStatus(signal)});
 const history=useQuery({queryKey:['predictions',m.id],queryFn:({signal})=>api.predictions(m.id,signal),enabled:!!m.id});
 return <><PageTitle title="Predictions" subtitle="Every prediction needs a model, a version, and evidence." action={<Badge>Model availability</Badge>}/><div className="filter-bar"><AssetPicker assets={m.assets.data?.items||[]} id={m.id} onChange={m.select}/><Badge>Historical inputs</Badge></div>{m.assets.isError&&<ErrorState error={m.assets.error} retry={()=>m.assets.refetch()}/>}
 <div className="metric-grid"><Metric label="Model status" value={status.data?.status.replaceAll('_',' ')||'—'} detail="Reported by FastAPI through Spring Boot"/><Metric label="Model version" value={status.data?.model_version||'—'} detail="No model artifact registered"/><Metric label="Input freshness" value={m.prices.data?.items.at(-1)?.trading_date||'—'} detail="Last cached trading date"/><Metric label="Confidence" value="—" detail="Available only with a trained model"/></div>
 {status.isError?<ErrorState error={status.error} retry={()=>status.refetch()}/>:status.isPending?<Loading/>:<Panel><Empty title={status.data.available?'Model available':status.data.status==='UNAVAILABLE'?'Model service unavailable':'Prediction model not trained'} icon={<BrainCircuit size={30}/>}>{status.data.message}</Empty></Panel>}
 <PhaseNote>Model training will be available in a later phase. Prediction execution remains unavailable until a trained, evaluated model and inference endpoint are implemented.</PhaseNote>
 <Panel title="Prediction history" action={<Badge>{history.data?.length||0} records</Badge>}>{history.isError?<ErrorState error={history.error} retry={()=>history.refetch()}/>:history.isLoading?<Loading/>:!history.data?.length?<Empty title="No predictions recorded">Prediction model not trained. Model training will be available in a later phase.</Empty>:<div className="table-scroll"><table><thead><tr>{['Target date','Model version ID','Predicted value','Probability','Created at'].map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{history.data.map(p=><tr key={p.id}><td>{p.prediction_target_date}</td><td>{p.model_version_id}</td><td>{number(p.predicted_value)}</td><td>{percent(p.probability)}</td><td>{time(p.created_at)}</td></tr>)}</tbody></table></div>}</Panel><p className="data-disclaimer">Probabilities are model estimates, not guarantees. Future evaluation must account for temporal leakage, costs, and regime changes. No investment decisions should rely on an unvalidated model.</p></>;
}
