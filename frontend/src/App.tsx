import { lazy, Suspense } from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import { Loading, Empty } from './components/UI';
const Overview=lazy(()=>import('./pages/Overview'));
const Markets=lazy(()=>import('./pages/Markets'));
const Explorer=lazy(()=>import('./pages/Explorer'));
const PortfolioPage=lazy(()=>import('./pages/Portfolio'));
const Predictions=lazy(()=>import('./pages/Predictions'));
const Strategies=lazy(()=>import('./pages/Strategies'));
const Backtesting=lazy(()=>import('./pages/Backtesting'));
const Risk=lazy(()=>import('./pages/Risk'));
const Laboratory=lazy(()=>import('./pages/Laboratory'));
const Health=lazy(()=>import('./pages/Health'));
const Settings=lazy(()=>import('./pages/Settings'));
export default function App() {return <Suspense fallback={<Loading label="Opening METIS workspace"/>}><Routes><Route element={<AppLayout/>}><Route index element={<Overview/>}/><Route path="markets" element={<Markets/>}/><Route path="explorer" element={<Explorer/>}/><Route path="portfolio" element={<PortfolioPage/>}/><Route path="predictions" element={<Predictions/>}/><Route path="strategies" element={<Strategies/>}/><Route path="backtesting" element={<Backtesting/>}/><Route path="risk" element={<Risk/>}/><Route path="laboratory" element={<Laboratory/>}/><Route path="health" element={<Health/>}/><Route path="settings" element={<Settings/>}/><Route path="*" element={<Empty title="Page not found"><Link to="/">Return to Overview</Link></Empty>}/></Route></Routes></Suspense>;}
