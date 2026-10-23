import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PreferenceContext, usePreferenceState } from './hooks/usePreferences';
import { ToastProvider } from './components/Toast';
import App from './App';
import './styles/index.css';
const client = new QueryClient({ defaultOptions: { queries: { staleTime:60000, retry:1, refetchOnWindowFocus:false }, mutations: { retry:0 } } });
function Root() { const state=usePreferenceState();return <QueryClientProvider client={client}><PreferenceContext.Provider value={state}><ToastProvider><BrowserRouter><App/></BrowserRouter></ToastProvider></PreferenceContext.Provider></QueryClientProvider>; }
createRoot(document.getElementById('root')!).render(<StrictMode><Root/></StrictMode>);
