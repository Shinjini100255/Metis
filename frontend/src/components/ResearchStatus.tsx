import { useQuery } from '@tanstack/react-query';
import { api } from '../services/api';
import { Panel,Metric,Badge,ErrorState } from './UI';
import { time } from '../utils/format';
export default function ResearchStatus() {
 const coverage=useQuery({queryKey:['coverage'],queryFn:({signal})=>api.coverage(signal)}),models=useQuery({queryKey:['models'],queryFn:({signal})=>api.models(signal)}),predictions=useQuery({queryKey:['latest-predictions'],queryFn:({signal})=>api.predictions(undefined,signal)}),jobs=useQuery({queryKey:['training-jobs'],queryFn:({signal})=>api.trainingJobs(signal)});
 const rows=coverage.data||[],last=models.data?.[0],pred=predictions.data?.[0];
 return <Panel title="Local research pipeline" action={<Badge>No automatic downloads or training</Badge>}><div className="metric-grid"><Metric label="Stored sessions" value={rows.reduce((s,r)=>s+Number(r.observations),0)} detail={`${rows.filter(r=>Number(r.observations)>0).length} asset series`}/><Metric label="Latest model" value={last?.model_name||'No trained model'} detail={last?`${last.development_only?'DEVELOPMENT FIXTURE ONLY · ':''}${last.version}`:'Import local history, then train explicitly'}/><Metric label="Latest prediction" value={pred?.task==='classification'?(Number(pred.predicted_value)>0?'Up':'Non-positive'):pred?.task==='regression'?`${(pred.predicted_value*100).toFixed(3)}%`:'Unavailable'} detail={pred?`${pred.status} · ${time(pred.created_at)}`:'Fresh features and a trained model required'}/><Metric label="Latest training" value={jobs.data?.[0]?.status||'No training runs'} detail={jobs.data?.[0]?.message||'One bounded CPU job at a time'}/></div>{coverage.isError&&<ErrorState error={coverage.error} retry={()=>coverage.refetch()}/>}</Panel>;
}
