/* ============================================================================
 * EMERGENCY console tools — "A single colon is not a valid keyword" crash
 *
 * This is a FIELD PATCH, not the real fix. The durable fix (prevent bad keys +
 * self-heal on load) is described in docs/recovery/single-colon-keyword.md.
 *
 * A saved character built with a custom class whose element was named "" or
 * "'" gets a key that collapses to the empty keyword `:`. The server returns
 * the character as EDN; the browser's read-string chokes on the bare `:` and
 * the whole page crashes, so the character can't be viewed, edited, or deleted.
 *
 * Paste ONE of the two options below into the browser DevTools Console (F12)
 * on the site while logged in.
 *
 * VERIFIED end-to-end against the real app (see the e2e harness in this dir):
 *   - the app reads xhr.RESPONSE (not responseText) — Option A hooks both.
 *   - Option A repairs the load in-flight; the real character then renders.
 * ==========================================================================*/

/* ----------------------------------------------------------------------------
 * OPTION A — KEEP the character. Repairs the character response as it loads.
 *
 * HOW TO USE: the shim must be installed BEFORE the character is fetched, and a
 * full page reload wipes it. So:
 *   1. Go to your "My Characters" LIST page (it loads fine — uses summaries).
 *   2. Paste this whole block, press Enter.
 *   3. CLICK the broken character (in-app navigation, no reload) — it opens.
 *   4. Make any small edit and Save to persist the fix permanently.
 * -------------------------------------------------------------------------- */
(() => {
  const D = new Set([" ","\t","\n","\r","\f",",","{","}","[","]","(",")",'"',";"]);
  function fixEdn(s){let o="",i=0;while(i<s.length){const ch=s[i];
    if(ch==='"'){o+=ch;i++;while(i<s.length){const d=s[i];o+=d;i++;if(d==="\\"){if(i<s.length){o+=s[i];i++;}}else if(d==='"')break;}continue;}
    if(ch===":"){let j=i+1;while(j<s.length&&!D.has(s[j]))j++;const t=s.slice(i,j);o+=(t===":"?":orphaned-name":t);i=j;continue;}
    o+=ch;i++;}return o;}
  const P = XMLHttpRequest.prototype, open = P.open;
  P.open = function(m,u){ this.__u = u; return open.apply(this, arguments); };
  // The app reads BOTH .response and .responseText depending on path — hook both.
  for (const prop of ['response','responseText']) {
    let p = P, d;
    while (p && !(d = Object.getOwnPropertyDescriptor(p, prop))) p = Object.getPrototypeOf(p);
    if (!d || !d.get) continue;
    Object.defineProperty(P, prop, { configurable:true, get:function(){
      let v = d.get.call(this);
      if (typeof v === "string" && this.__u && /\/dnd\/5e\/characters\/\d+/.test(this.__u)) v = fixEdn(v);
      return v;
    }});
  }
  console.log("%cFix active. Open the character from your list now. Once it loads, make a small edit and Save to keep it.", "color:green;font-weight:bold");
})();

/* ----------------------------------------------------------------------------
 * OPTION B — DELETE the character (fallback, if you don't want to keep it).
 * Needs no character load. Token lives in localStorage["user"] (EDN), NOT a
 * cookie; auth scheme is buddy jws => "Authorization: Token <jwt>".
 * -------------------------------------------------------------------------- */
async function orcpubDeleteCharacter(id){
  id = id || (location.href.match(/characters?\/(\d+)/)||[])[1] || prompt("Character ID to delete:");
  const u = localStorage.getItem("user") || "";
  const tok = (u.match(/:token\s+"([^"]+)"/)||[])[1]
           || (document.cookie.match(/(?:^|;\s*)token=([^;]+)/)||[])[1];
  if (!tok) return alert("Couldn't find your login token — make sure you're logged in on this site.");
  if (!confirm("Permanently delete character "+id+"?")) return;
  const r = await fetch("/dnd/5e/characters/"+id, { method:"DELETE", headers:{ Authorization:"Token "+tok } });
  alert(r.ok ? "Deleted character "+id+"." : "Delete failed: "+r.status);
}
// To delete: paste the function above, then run  orcpubDeleteCharacter()
