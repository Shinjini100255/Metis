import { useState } from 'react';
import { NavLink, Outlet, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Activity, BarChart3, Bell, BookOpen, BrainCircuit, ChartCandlestick, ChevronLeft, ChevronRight, FlaskConical, Globe2, Layers3, LayoutDashboard, Menu, Moon, Settings2, ShieldCheck, Sun, Wallet, X, ExternalLink } from 'lucide-react';
import Search from '../components/Search';
import { Badge } from '../components/UI';
import { usePreferences } from '../hooks/usePreferences';
import { api } from '../services/api';
export const navigation = [
 {path:'/',label:'Overview',icon:LayoutDashboard}, {path:'/markets',label:'Markets',icon:Globe2}, {path:'/explorer',label:'Stock Explorer',icon:ChartCandlestick},
 {path:'/predictions',label:'Predictions',icon:BrainCircuit}, {path:'/strategies',label:'Strategies',icon:Layers3}, {path:'/backtesting',label:'Backtesting',icon:BarChart3},
 {path:'/portfolio',label:'Portfolio',icon:Wallet}, {path:'/risk',label:'Risk Analytics',icon:ShieldCheck}, {path:'/laboratory',label:'Model Laboratory',icon:FlaskConical},
 {path:'/health',label:'System Health',icon:Activity}, {path:'/settings',label:'Settings',icon:Settings2},
];
export default function AppLayout() {
 const [collapsed,setCollapsed]=useState(false),[mobile,setMobile]=useState(false),[notifications,setNotifications]=useState(false);const {preferences,update}=usePreferences();
 const status=useQuery({queryKey:['status'],queryFn:({signal})=>api.status(signal),refetchInterval:preferences.refresh||false});
 return <div className={`app-shell ${collapsed?'sidebar-collapsed':''} ${mobile?'mobile-open':''}`}>
 <aside className="sidebar"><Link to="/" className="brand" aria-label="METIS home"><svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M3 25V7l13 12L29 7v18" stroke="currentColor" strokeWidth="2.5"/><path d="M9 25V19l7 6 7-6v6" stroke="currentColor" strokeWidth="2.5"/></svg><div><strong>METIS</strong><small>FINANCIAL INTELLIGENCE</small></div></Link>
 <div className="workspace-chip"><span className="workspace-avatar">M</span><div><strong>Research workspace</strong><small>Local development</small></div><Badge>01</Badge></div>
 <div className="nav-caption">WORKSPACE</div><nav aria-label="Main navigation">{navigation.map((n,i)=><div key={n.path}>{i===9&&<div className="nav-caption system-caption">PLATFORM</div>}<NavLink to={n.path} end={n.path==='/'} title={n.label} onClick={()=>setMobile(false)}><n.icon size={18}/><span>{n.label}</span></NavLink></div>)}</nav>
 <div className="sidebar-bottom"><div className="local-notice"><span className="status-dot"/><span>Local environment</span><ShieldCheck size={14}/></div><a href="http://localhost:8080/swagger-ui/index.html" target="_blank" rel="noreferrer"><BookOpen size={16}/><span>API documentation</span><ExternalLink size={12}/></a><button onClick={()=>setCollapsed(!collapsed)} className="collapse-button" aria-label={collapsed?'Expand sidebar':'Collapse sidebar'}>{collapsed?<ChevronRight size={17}/>:<ChevronLeft size={17}/>}<span>Collapse sidebar</span></button></div></aside>
 {mobile&&<button className="mobile-backdrop" aria-label="Close navigation" onClick={()=>setMobile(false)}/>}
 <div className="app-main"><div className="topbar"><button className="icon-button mobile-menu" aria-label="Open navigation" onClick={()=>setMobile(true)}><Menu size={20}/></button><Search/><div className="topbar-actions"><select aria-label="Market selection" value={preferences.market} onChange={e=>update({market:e.target.value as 'US'|'IN'})}><option value="US">US equities</option><option value="IN">India · unavailable</option></select><div className="topbar-divider"/><button className="icon-button" aria-label="Toggle theme" onClick={()=>update({theme:preferences.theme==='dark'?'light':'dark'})}>{preferences.theme==='dark'?<Sun size={18}/>:<Moon size={18}/>}</button><div className="notifications"><button className="icon-button" aria-label="Notifications" aria-expanded={notifications} onClick={()=>setNotifications(!notifications)}><Bell size={18}/><span className="notification-dot"/></button>{notifications&&<div className="notification-panel"><div className="row-between"><strong>Workspace notices</strong><button className="icon-button" aria-label="Close notifications" onClick={()=>setNotifications(false)}><X size={15}/></button></div><p>Historical data is fetched only when requested. No automatic training or trading is running.</p><Link to="/health" onClick={()=>setNotifications(false)}>View service health →</Link></div>}</div><span className="avatar">LD</span></div></div>
 <div className="contextbar"><span>METIS <span className="separator">/</span> Research terminal</span><div><span className={`status-dot ${status.data?.provider.configured?'':'muted'}`}/><span>{status.isError?'Backend unavailable':status.data?.provider.configured?'Historical data provider configured':'Data source not configured'}</span><span className="context-version">v0.1.0</span></div></div>
 <main id="main-content"><Outlet/></main><footer><span>METIS <span className="separator">/</span> Financial Intelligence & MLOps</span><span>Paper portfolios only <span className="separator">·</span> Historical data, never live</span></footer></div></div>;
}
