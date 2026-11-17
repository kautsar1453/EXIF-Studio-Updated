(function(){
if(window.__efsSplit)return;window.__efsSplit=true;
const E=window.__efs=window.__efs||{mode:'frame',img:null,exif:'',free:{bg:'#ffffff',blur:false,pad:8,rad:0,ox:0,oy:0},split:{n:3,ar:'original'},pv:{open:false,s:1,x:0,y:0}};

const css=document.createElement('style');
css.textContent=`
#ig-mod{position:fixed;inset:0;z-index:2147483640;background:rgba(9,9,11,.85);backdrop-filter:blur(10px);display:none;flex-direction:column;justify-content:flex-end;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;color:#fafafa}
#ig-mod.on{display:flex}
.ig-sh{background:#141417;border-top:1px solid rgba(255,255,255,.14);border-radius:22px 22px 0 0;padding:16px 18px 24px;max-height:90vh;overflow-y:auto}
.ig-hd{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.ig-xb{width:36px;height:36px;border-radius:50%;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,.08);color:#e4e4e7;font-size:16px;cursor:pointer}
.ig-pb{background:#09090b;border:1px solid rgba(255,255,255,.1);border-radius:14px;padding:12px;margin-bottom:14px;display:flex;flex-direction:column;align-items:center}
.ig-ps{position:relative;max-width:100%;max-height:220px;display:inline-block;overflow:hidden;border-radius:6px}
#ig-pc{display:block;max-width:100%;max-height:220px;width:auto;height:auto}
#ig-ov{position:absolute;inset:0;display:flex;pointer-events:none}
.ig-cl{flex:1;height:100%;border-right:1.5px dashed rgba(251,191,36,.9);position:relative;box-sizing:border-box}.ig-cl:last-child{border-right:none}
.ig-bg{position:absolute;top:6px;left:6px;background:rgba(0,0,0,.74);color:#fbbf24;font-size:10px;font-weight:700;padding:2px 6px;border-radius:6px}
#ig-dm{margin-top:8px;font-size:11.5px;color:#a1a1aa}
.ig-sr{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:12px}
.ig-sb{min-height:46px;padding:10px 8px;border-radius:11px;border:1px solid rgba(255,255,255,.14);background:#1f1f23;color:#d4d4d8;font-size:13px;font-weight:600;cursor:pointer}
.ig-sb.on{background:rgba(245,158,11,.2);border-color:#f59e0b;color:#fbbf24}
.ig-ac{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:4px}
.ig-bp{min-height:48px;border-radius:12px;border:none;background:#f59e0b;color:#09090b;font-size:14px;font-weight:700;cursor:pointer}
.ig-bs{min-height:48px;border-radius:12px;border:1px solid rgba(255,255,255,.2);background:#27272a;color:#fff;font-size:14px;font-weight:600;cursor:pointer}
`;
(document.head||document.documentElement).appendChild(css);

let spSrc=null;
const spMod=document.createElement('div');
spMod.id='ig-mod';
spMod.innerHTML='<div class="ig-sh"><div class="ig-hd"><b style="font-size:16px">Split IG</b><button type="button" class="ig-xb" id="ig-x">✕</button></div><div class="ig-pb"><div class="ig-ps"><canvas id="ig-pc"></canvas><div id="ig-ov"></div></div><div id="ig-dm"></div></div><div class="ig-sr" id="ig-cg"><button type="button" class="ig-sb" data-n="2">2 Slide</button><button type="button" class="ig-sb on" data-n="3">3 Slide</button><button type="button" class="ig-sb" data-n="4">4 Slide</button></div><div class="ig-sr" id="ig-ag"><button type="button" class="ig-sb on" data-a="original">Asli</button><button type="button" class="ig-sb" data-a="4:5">4:5</button><button type="button" class="ig-sb" data-a="1:1">1:1</button></div><div class="ig-ac"><button type="button" class="ig-bs" id="ig-shb">Share</button><button type="button" class="ig-bp" id="ig-svb">Simpan</button></div></div>';

function mkPano(sc,n,ar){
  const sw=sc.width,sh=sc.height;
  if(ar==='original'){const o=document.createElement('canvas');o.__isInternal=true;o.width=sw;o.height=sh;o.getContext('2d').drawImage(sc,0,0);return o;}
  const tr=(ar==='4:5'?0.8:1)*n;
  let pw,ph;if(sw/sh>=tr){pw=sw;ph=Math.round(sw/tr);}else{ph=sh;pw=Math.round(sh*tr);}
  const o=document.createElement('canvas');o.__isInternal=true;o.width=pw;o.height=ph;const x=o.getContext('2d');
  let bg='#fff';try{const d=sc.getContext('2d').getImageData(2,2,1,1).data;bg='rgb('+d[0]+','+d[1]+','+d[2]+')';}catch(_){}
  x.fillStyle=bg;x.fillRect(0,0,pw,ph);
  const k=Math.min(pw/sw,ph/sh),dw=Math.round(sw*k),dh=Math.round(sh*k);
  x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';
  x.drawImage(sc,Math.round((pw-dw)/2),Math.round((ph-dh)/2),dw,dh);return o;
}
function drawSplit(){
  if(!spSrc)return;
  const n=E.split.n,p=mkPano(spSrc,n,E.split.ar),pc=document.getElementById('ig-pc');
  pc.width=p.width;pc.height=p.height;pc.getContext('2d').drawImage(p,0,0);
  const ov=document.getElementById('ig-ov');ov.innerHTML='';
  for(let i=1;i<=n;i++){const d=document.createElement('div');d.className='ig-cl';d.innerHTML='<span class="ig-bg">'+i+'/'+n+'</span>';ov.appendChild(d);}
  document.getElementById('ig-dm').textContent=n+' Slide • '+Math.floor(p.width/n)+' × '+p.height+' px';
  document.getElementById('ig-svb').textContent='Simpan ('+n+')';
  document.getElementById('ig-shb').textContent='Share ('+n+')';
}
function sliceUrls(){
  if(!spSrc)return[];
  const n=E.split.n,p=mkPano(spSrc,n,E.split.ar),tw=p.width,th=p.height,bw=Math.floor(tw/n),st=Date.now(),out=[];
  for(let i=0;i<n;i++){
    const sx=i*bw,sw=(i===n-1)?(tw-sx):bw,c=document.createElement('canvas');c.__isInternal=true;c.width=sw;c.height=th;
    const x=c.getContext('2d');x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';x.drawImage(p,sx,0,sw,th,0,0,sw,th);
    const dataUrl=E.canvasToSafeJpeg?E.canvasToSafeJpeg(c):c.toDataURL('image/jpeg',0.92);
    out.push({dataUrl,fileName:'EXIF_IG_'+(i+1)+'of'+n+'_'+st+'.jpg'});
  }
  return out;
}

E.openSplit=()=>{
  if(!spMod.parentNode&&document.body)document.body.appendChild(spMod);
  initSplit();
  const c=E.getOutCanvas&&E.getOutCanvas();
  if(!c)return;
  spSrc=c;drawSplit();spMod.classList.add('on');
};

let bound=false;
function initSplit(){
  if(!document.body)return;
  if(!spMod.parentNode)document.body.appendChild(spMod);
  if(bound)return;bound=true;
  document.getElementById('ig-x').addEventListener('click',()=>spMod.classList.remove('on'));
  spMod.addEventListener('click',e=>{if(e.target===spMod)spMod.classList.remove('on');});
  document.querySelectorAll('#ig-cg .ig-sb').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('#ig-cg .ig-sb').forEach(x=>x.classList.remove('on'));b.classList.add('on');E.split.n=parseInt(b.getAttribute('data-n'),10)||3;drawSplit();}));
  document.querySelectorAll('#ig-ag .ig-sb').forEach(b=>b.addEventListener('click',()=>{document.querySelectorAll('#ig-ag .ig-sb').forEach(x=>x.classList.remove('on'));b.classList.add('on');E.split.ar=b.getAttribute('data-a')||'original';drawSplit();}));
  ['touchstart','pointerdown'].forEach(ev=>document.getElementById('ig-svb').addEventListener(ev,()=>E.showHud&&E.showHud('Menyimpan...')));
  document.getElementById('ig-svb').addEventListener('click',()=>{
    spMod.classList.remove('on');E.showHud&&E.showHud('Menyimpan...');
    const sl=sliceUrls();
    if(E.saveBatchToGallery)E.saveBatchToGallery(sl);
    else{
      const id='sp_'+Date.now();
      if(sl.length&&window.AndroidGallery&&window.AndroidGallery.saveBatchBase64Images)window.AndroidGallery.saveBatchBase64Images(JSON.stringify(sl),id);
      else window.__onGallerySaveFinished(id,false);
    }
  });
  document.getElementById('ig-shb').addEventListener('click',()=>{
    const sl=sliceUrls();if(!sl.length)return;spMod.classList.remove('on');
    if(window.AndroidShare&&window.AndroidShare.shareMultipleBase64Images)window.AndroidShare.shareMultipleBase64Images(JSON.stringify(sl));
  });
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initSplit);else initSplit();
})();
