import { describe,it,expect,vi } from 'vitest';
import { render,screen,waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter,Routes,Route } from 'react-router-dom';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { PreferenceContext,usePreferenceState } from '../hooks/usePreferences';
import { ToastProvider } from '../components/Toast';
import App from '../App';
import Search from '../components/Search';
import { request } from '../services/api';
import type { ReactNode } from 'react';
vi.mock('../components/PriceChart',()=>({default:()=> <div>Chart test boundary</div>,AllocationChart:()=> <div>Allocation test boundary</div>}));
// Reference fixtures only. No fabricated history is shipped with the application.
const apple={id:1,symbol:'AAPL',company_name:'Apple Inc.',exchange:'NASDAQ',currency:'USD',asset_type:'STOCK'};
const system={spring:{status:'UP'},postgres:{status:'UP',response_ms:2},ml:{status:'UP',response_ms:3},provider:{name:'Alpha Vantage',status:'NOT_CONFIGURED',configured:false,data_type:'Historical daily'},last_successful_ingestion:null,timestamp:'2026-01-01T00:00:00Z',environment:'single-user local development'};
function fixtureFetch() {return vi.fn(async(input:RequestInfo|URL,_options?:RequestInit)=>{
 const url=new URL(String(input),'http://localhost');let data:unknown=[];
 if(url.pathname.includes('/assets/search'))data={items:[apple],page:0,size:100,total:1};
 else if(url.pathname.includes('/prices'))data={items:[],page:0,size:1260,total:0};
 else if(url.pathname.includes('/indicators'))data={points:[],annualized_volatility:null,sharpe_ratio:null,maximum_drawdown:null,observations:0,methodology:'Unadjusted daily close'};
 else if(url.pathname.includes('/models/status'))data={available:false,status:'NOT_TRAINED',model_version:null,message:'Prediction model not trained. Model training will be available in a later phase.'};
 else if(url.pathname.includes('/ingestion/protection'))data={live_enabled:false,key_configured:false,max_per_day:20,max_per_minute:4,min_interval_seconds:15,max_per_job:2,retry_limit:0,cache_hours:12,actual_requests:0};
 else if(url.pathname.includes('/models/operations'))data={training_runs:[],evaluations:[],deployment_status:'NOT_IMPLEMENTED'};
 else if(url.pathname.includes('/system/status'))data=system;
 else if(url.pathname.includes('/watchlists'))data=[{id:1,name:'Core watchlist',assets:[apple]}];
 else if(url.pathname.includes('/strategies'))data=[{id:1,name:'Buy & hold',strategy_type:'BUY_AND_HOLD',configuration:{},readiness:'NOT_EXECUTABLE'}];
 return new Response(JSON.stringify(data),{status:200,headers:{'Content-Type':'application/json'}});
 });}
function Wrapper({children}:{children:ReactNode}) {const state=usePreferenceState();return <PreferenceContext.Provider value={state}><ToastProvider>{children}</ToastProvider></PreferenceContext.Provider>;}
function mount(path='/',children:ReactNode=<App/>) {const client=new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0},mutations:{retry:false}}});return render(<QueryClientProvider client={client}><Wrapper><MemoryRouter initialEntries={[path]}>{children}</MemoryRouter></Wrapper></QueryClientProvider>);}
const pages=[['/','Market overview'],['/markets','Markets'],['/explorer','Stock explorer'],['/predictions','Predictions'],['/strategies','Strategies'],['/backtesting','Backtesting'],['/portfolio','Paper portfolio'],['/risk','Risk analytics'],['/laboratory','Model laboratory'],['/health','System health'],['/settings','Settings']];
describe('workspace routes',()=>{
 it.each(pages)('%s renders a real page',async(path,title)=>{vi.stubGlobal('fetch',fixtureFetch());mount(path);expect(await screen.findByRole('heading',{level:1,name:new RegExp(title,'i')})).toBeInTheDocument();});
 it('navigates using the sidebar',async()=>{vi.stubGlobal('fetch',fixtureFetch());mount();await screen.findByRole('heading',{name:/Market overview/});await userEvent.click(screen.getByRole('link',{name:'Markets'}));expect(await screen.findByRole('heading',{level:1,name:/Markets/})).toBeInTheDocument();});
 it('switches theme and persists it in the browser',async()=>{vi.stubGlobal('fetch',fixtureFetch());mount();await screen.findByRole('heading',{name:/Market overview/});await userEvent.click(screen.getByRole('button',{name:'Toggle theme'}));expect(document.documentElement.dataset.theme).toBe('light');expect(JSON.parse(localStorage.getItem('metis-preferences')!).theme).toBe('light');});
 it('shows actual network failure and retry controls',async()=>{vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('offline')));mount('/markets');await screen.findByRole('heading',{level:1,name:/Markets/});expect((await screen.findAllByRole('alert')).length).toBeGreaterThan(0);expect(screen.getAllByText(/Cannot reach the METIS backend/).length).toBeGreaterThan(0);expect(screen.getAllByRole('button',{name:'Retry'}).length).toBeGreaterThan(0);});
 it('shows pending and empty states',async()=>{vi.stubGlobal('fetch',fixtureFetch());mount('/portfolio');await screen.findByRole('heading',{level:1,name:/Paper portfolio/});expect(await screen.findByText('Your portfolio starts here')).toBeInTheDocument();});
 it('rejects an incomplete holding form without sending a mutation',async()=>{const fetch=fixtureFetch();const base=fetch.getMockImplementation()!;fetch.mockImplementation(async(input,init)=>{if(String(input)==='/api/v1/portfolios')return new Response(JSON.stringify([{id:7,name:'Test portfolio',base_currency:'USD'}]));if(String(input)==='/api/v1/portfolios/7')return new Response(JSON.stringify({id:7,name:'Test portfolio',base_currency:'USD',holdings:[],invested_capital:0,market_value:0,known_value:0,unrealized_pl:0,unvalued_positions:0,currency_mismatches:0}));return base(input,init);});vi.stubGlobal('fetch',fetch);mount('/portfolio');await userEvent.click(await screen.findByRole('button',{name:'Add holding'}));expect(await screen.findByRole('alert')).toHaveTextContent('Choose a stock and enter positive quantity');expect(fetch.mock.calls.some(([,options])=>(options as RequestInit|undefined)?.method==='POST')).toBe(false);});
 it('queries the backend for global stock search and opens the result',async()=>{const fetch=fixtureFetch();vi.stubGlobal('fetch',fetch);mount('/',<><Search/><Routes><Route path="/" element={null}/><Route path="/explorer" element={<h1>Selected asset</h1>}/></Routes></>);await userEvent.type(screen.getByRole('textbox',{name:'Search stocks'}),'AAPL');await userEvent.click(await screen.findByRole('button',{name:/AAPL Apple Inc/}));expect(await screen.findByRole('heading',{name:'Selected asset'})).toBeInTheDocument();expect(fetch.mock.calls.some(([url])=>String(url).includes('q=AAPL'))).toBe(true);});
 it('preserves a structured API error without exposing stack traces',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'PROVIDER_NOT_CONFIGURED',message:'Configure the provider key.'}),{status:503})));await expect(request('/assets/1/ingest',{method:'POST'})).rejects.toMatchObject({status:503,code:'PROVIDER_NOT_CONFIGURED',message:'Configure the provider key.'});});
 it('keeps backtest execution disabled',async()=>{vi.stubGlobal('fetch',fixtureFetch());mount('/backtesting');expect(await screen.findByRole('button',{name:'Execution unavailable'})).toBeDisabled();});
 it('shows model absence without invented confidence',async()=>{vi.stubGlobal('fetch',fixtureFetch());mount('/predictions');expect(await screen.findByText('Prediction model not trained',{exact:true})).toBeInTheDocument();expect(screen.queryByText(/87%/)).not.toBeInTheDocument();});
});


describe('Phase 2 deliberate actions',()=>{
 it('opens the laboratory without starting training or ingesting prices',async()=>{
  const fetch=fixtureFetch();vi.stubGlobal('fetch',fetch);mount('/laboratory');
  expect(await screen.findByRole('button',{name:'Train & evaluate local dataset'})).toBeDisabled();
  await screen.findByText('No training runs');
  expect(fetch.mock.calls.some(([,options])=>options?.method==='POST')).toBe(false);
 });
 it('ingestion preview sends only a dry-run request',async()=>{
  const fetch=fixtureFetch(),base=fetch.getMockImplementation()!;
  fetch.mockImplementation(async(input,init)=>String(input).endsWith('/ingestion/plan')?new Response(JSON.stringify({estimated_requests:1,external_requests_made:0,protection:{live_enabled:false}})):base(input,init));
  vi.stubGlobal('fetch',fetch);mount('/explorer');
  await userEvent.click(await screen.findByRole('button',{name:'Plan ingestion'}));
  await screen.findByText(/Dry run: 1 estimated remote request/);
  const posts=fetch.mock.calls.filter(([,o])=>o?.method==='POST');
  expect(posts).toHaveLength(1);expect(String(posts[0][0])).toBe('/api/v1/ingestion/plan');
  expect(JSON.parse(posts[0][1]!.body as string).asset_ids).toEqual([1]);
 });
 it('sends training only after explicit action with chosen configuration',async()=>{
  const fetch=fixtureFetch(),base=fetch.getMockImplementation()!;
  fetch.mockImplementation(async(input,init)=>{
   if(String(input).endsWith('/data/coverage'))return new Response(JSON.stringify([{asset_id:1,symbol:'AAPL',observations:520,provider:'Local CSV'}]));
   if(String(input).endsWith('/training/jobs')&&init?.method==='POST')return new Response(JSON.stringify({id:'job-test',status:'RUNNING',message:'Training started from stored observations.'}),{status:202});
   return base(input,init);
  });
  vi.stubGlobal('fetch',fetch);mount('/laboratory');
  const button=await screen.findByRole('button',{name:'Train & evaluate local dataset'});await waitFor(()=>expect(button).toBeEnabled());
  expect(fetch.mock.calls.some(([,o])=>o?.method==='POST')).toBe(false);
  await userEvent.click(button);await screen.findByText('Training started from stored observations.');
  const posts=fetch.mock.calls.filter(([,o])=>o?.method==='POST');expect(posts).toHaveLength(1);
  expect(JSON.parse(posts[0][1]!.body as string)).toMatchObject({asset_id:1,configuration:{folds:3,train_window:160,holdout_size:40,seed:17}});
 });
});


describe('persisted model outputs',()=>{
 it('uses the selected classifier probability even when a regression record is newest',async()=>{
  const fetch=fixtureFetch(),base=fetch.getMockImplementation()!;
  fetch.mockImplementation(async(input,init)=>{
   const path=new URL(String(input),'http://localhost').pathname;
   if(path==='/api/v1/models')return new Response(JSON.stringify([{id:7,asset_id:1,model_name:'Fixture classifier',task:'classification',version:'isolated-v1',status:'READY',created_at:'2026-01-01'},{id:8,asset_id:1,model_name:'Fixture regressor',task:'regression',version:'isolated-v2',status:'READY',created_at:'2026-01-01'}]));
   if(path==='/api/v1/models/status')return new Response(JSON.stringify({available:true,status:'READY',message:'Isolated UI fixture only.'}));
   if(path==='/api/v1/predictions')return new Response(JSON.stringify([{id:2,asset_id:1,model_version_id:8,task:'regression',predicted_value:.01,probability:null,created_at:'2026-01-03',prediction_target_date:'2026-01-05',feature_date:'2026-01-02',status:'DEVELOPMENT_ONLY'},{id:1,asset_id:1,model_version_id:7,task:'classification',predicted_value:1,probability:.73,created_at:'2026-01-02',prediction_target_date:'2026-01-05',feature_date:'2026-01-02',status:'DEVELOPMENT_ONLY'}]));
   return base(input,init);
  });
  vi.stubGlobal('fetch',fetch);mount('/predictions');
  await waitFor(()=>expect(screen.getAllByText('73.00%')).toHaveLength(2));
  expect(screen.queryByText('+73.00%')).not.toBeInTheDocument();
 });
});
