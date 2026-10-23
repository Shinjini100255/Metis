// Uses installed Edge with a fresh, ignored profile. No browser download or real user profile.
import { spawn } from 'node:child_process';
import { mkdir,writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const root=resolve('.cache'); await mkdir(root,{recursive:true});
const executable=process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const browser=spawn(executable,['--headless','--disable-gpu','--no-first-run','--no-default-browser-check','--disable-background-networking','--remote-debugging-port=9223',`--user-data-dir=${resolve(root,'edge-cdp')}`,'about:blank'],{windowsHide:true,stdio:'ignore'});
let socket;
try {
 let pages;
 for(let i=0;i<30;i++){try{pages=await(await fetch('http://127.0.0.1:9223/json/list')).json();if(pages.length)break;}catch{}await pause(200);}
 if(!pages?.length)throw new Error('Edge debugging endpoint did not start');
 socket=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);
 await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j;});
 let sequence=0;const pending=new Map();
 socket.onmessage=event=>{const message=JSON.parse(String(event.data));if(message.id){const task=pending.get(message.id);if(task){pending.delete(message.id);clearTimeout(task.timer);message.error?task.reject(new Error(message.error.message)):task.resolve(message.result);}}};
 const cdp=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(new Error('CDP timeout: '+method));},12000);pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));});
 await cdp('Page.enable');
 const results=[];
 for(const [name,width,height] of [['desktop',1440,1100],['mobile',390,844]]) {
  await cdp('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<500});
  await cdp('Page.navigate',{url:'http://127.0.0.1:5173/'});await pause(2200);
  const {result}=await cdp('Runtime.evaluate',{expression:'JSON.stringify({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,title:document.querySelector("h1")?.textContent,loading:document.querySelectorAll(".loading").length})',returnByValue:true});
  const layout=JSON.parse(result.value);results.push({name,...layout});
  const screenshot=await cdp('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});await writeFile(resolve(root,`metis-${name}.png`),Buffer.from(screenshot.data,'base64'));
 }
 await cdp('Runtime.evaluate',{expression:`document.querySelector('[aria-label="Toggle theme"]').click()`});
 await pause(300);
 const {result:theme}=await cdp('Runtime.evaluate',{expression:'document.documentElement.dataset.theme',returnByValue:true});
 results.push({theme:theme.value});
 if(theme.value!=='light')throw new Error('Theme toggle failed');
 await writeFile(resolve(root,'browser-qa.json'),JSON.stringify(results,null,2));
 console.log(JSON.stringify(results));
 if(results.some(r=>r.scrollWidth>r.width))process.exitCode=1;
 await cdp('Browser.close').catch(()=>{});
} finally {socket?.close();if(browser.exitCode===null)browser.kill();}
