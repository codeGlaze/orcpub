/* ============================================================================
 * e2e harness — validate the "single colon" fix against the REAL app.
 *
 * WHY THIS EXISTS: in the sandbox the browser cannot reach external HTTPS
 * (Chromium resets; curl/node reach it fine). So `node` is the tunnel: the
 * browser only talks to Playwright request-interception on localhost, and for
 * real assets the route handler fetches them through node's proxy egress. The
 * corrupt character is served to reproduce the bug; a fix is injected via
 * addInitScript to prove it clears the crash.
 *
 * PREP (once):
 *   curl -sS -H "Accept: application/edn" \
 *     https://www.dungeonmastersvault.com/dnd/5e/characters/17592342813688 \
 *     -o /tmp/char.raw               # the real corrupt character EDN
 *
 * RUN:
 *   NODE_USE_ENV_PROXY=1 NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt \
 *     node scripts/recovery/e2e-harness.mjs
 *
 * EXPECTED (proven): BASELINE crashes (pageerror, blank "New Character");
 * WITH FIX the real character ("Dymethrion … STR 16 …") renders, no error.
 * The `SHIM` fn below is the reference fix — it hooks BOTH `.response` and
 * `.responseText` because the app reads `.response` (this harness caught that).
 *
 * Adapt paths (chromium build, char id) as needed for a future session.
 * ==========================================================================*/
import fs from 'fs';
import { createRequire } from 'module';
const require = createRequire('/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const edn = fs.readFileSync('/tmp/char.raw');
const CID='17592342813688';
const REAL='https://www.dungeonmastersvault.com/pages/dnd/5e/characters/'+CID+'?frame=true';
async function bridge(route){
  const req=route.request(); const url=req.url();
  if(/\/dnd\/5e\/characters\/\d+(\?|$)/.test(url)&&!/\/pages\//.test(url)) return route.fulfill({status:200,headers:{'content-type':'application/edn'},body:edn});
  if(/googlesyndication|adsbygoogle|pagead|doubleclick|google-analytics|googletagmanager|\/\/t\.dungeonmastersvault/.test(url)) return route.abort();
  try{ const r=await fetch(url,{method:req.method(),headers:req.headers(),redirect:'manual'}); const buf=Buffer.from(await r.arrayBuffer()); const h={}; r.headers.forEach((v,k)=>{if(!/content-encoding|content-length|transfer-encoding|content-security-policy/i.test(k))h[k]=v;}); return route.fulfill({status:r.status,headers:h,body:buf}); }
  catch(e){ return route.fulfill({status:200,body:''}); }
}
const SHIM = () => {
  const D=new Set([" ","\t","\n","\r","\f",",","{","}","[","]","(",")",'"',";"]);
  function fixEdn(s){let o="",i=0;while(i<s.length){const ch=s[i];
    if(ch==='"'){o+=ch;i++;while(i<s.length){const d=s[i];o+=d;i++;if(d==="\\"){if(i<s.length){o+=s[i];i++;}}else if(d==='"')break;}continue;}
    if(ch===":"){let j=i+1;while(j<s.length&&!D.has(s[j]))j++;const t=s.slice(i,j);o+=(t===":"?":orphaned-name":t);i=j;continue;}
    o+=ch;i++;}return o;}
  const P=XMLHttpRequest.prototype, o=P.open;
  P.open=function(m,u){this.__u=u;return o.apply(this,arguments);};
  for (const prop of ['response','responseText']){
    let p=P,d; while(p&&!(d=Object.getOwnPropertyDescriptor(p,prop)))p=Object.getPrototypeOf(p);
    if(!d||!d.get) continue;
    Object.defineProperty(P,prop,{configurable:true,get:function(){
      let v=d.get.call(this);
      if(typeof v==='string'&&this.__u&&/\/characters\/\d+/.test(this.__u)){window.__hits=(window.__hits||0)+1;v=fixEdn(v);}
      return v;}});
  }
};
async function run(label, withShim){
  const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome',args:['--no-sandbox']});
  const page=await (await b.newContext()).newPage();
  const perr=[];
  page.on('pageerror', e=>perr.push(e.message||String(e)));
  await page.route('**/*', bridge);
  if(withShim) await page.addInitScript(SHIM);
  try{ await page.goto(REAL,{waitUntil:'domcontentloaded',timeout:40000}); }catch(e){}
  await page.waitForTimeout(12000);
  const hits=await page.evaluate(()=>window.__hits||0).catch(()=>0);
  const body=(await page.evaluate(()=>document.body?document.body.innerText:'').catch(()=>'')).replace(/\s+/g,' ');
  console.log(`\n== ${label} ==`);
  console.log('shim repaired reads:', hits);
  console.log('pageerrors:', [...new Set(perr)].slice(0,3));
  console.log('shows real character (MorkovGun/greatsword/plate/orphaned):', /MorkovGun|greatsword|plate|orphaned/i.test(body));
  console.log('body snippet:', JSON.stringify(body.slice(0,180)));
  await b.close();
  return { err: perr.length, hits, body };
}
const base=await run('BASELINE (no shim)', false);
const shim=await run('WITH FIXED SHIM (hooks .response)', true);
console.log('\n===== VERDICT =====');
console.log('baseline errored:', base.err>0?'YES ('+[...new Set([])] +base.err+')':'no');
console.log('fixed shim fired:', shim.hits>0?'YES ('+shim.hits+')':'NO');
console.log('fixed shim cleared the error:', (base.err>0 && shim.err===0)?'YES ✅':'(base='+base.err+', shim='+shim.err+')');
