import { createContext, useContext, useRef, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { X } from 'lucide-react';
const ToastContext = createContext<(message: string) => void>(() => {});
export const useToast = () => useContext(ToastContext);
export function ToastProvider({ children }: { children: ReactNode }) {
 const [message,setMessage] = useState(''); const timer=useRef<ReturnType<typeof setTimeout> | null>(null);
 useEffect(() => () => { if(timer.current) clearTimeout(timer.current); }, []);
 const toast = (text: string) => { setMessage(text); if(timer.current) clearTimeout(timer.current); timer.current=setTimeout(() => setMessage(''),6500); };
 return <ToastContext.Provider value={toast}>{children}{message && <div className="toast" role="status">{message}<button className="icon-button" aria-label="Dismiss notification" onClick={() => setMessage('')}><X size={15}/></button></div>}</ToastContext.Provider>;
}
