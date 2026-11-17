(function(){
if(window.__efsCore)return;window.__efsCore=true;
const E=window.__efs=window.__efs||{mode:'frame',img:null,exif:'',free:{bg:'#ffffff',blur:false,pad:8,rad:0,wm:'bottom-center',ox:0,oy:0,txt:''},split:{n:3,ar:'original'},pv:{open:false,s:1,x:0,y:0}};

const css=document.createElement('style');
css.textContent=`
body{padding-top:56px!important}
#lr-hud{position:fixed;inset:0;z-index:2147483647;background:rgba(9,9,11,.72);backdrop-filter:blur(8px);display:none;align-items:center;justify-content:center;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
#lr-hud.on{display:flex}
#lr-box{width:min(240px,75vw);background:rgba(24,24,27,.98);border:1px solid rgba(255,255,255,.15);border-radius:18px;padding:20px 18px 18px;box-shadow:0 24px 48px rgba(0,0,0,.6);color:#fff;text-align:center}
.lr-rw{width:44px;height:44px;margin:0 auto 12px;display:flex;align-items:center;justify-content:center}
#lr-sp{width:40px;height:40px;border-radius:50%;border:3px solid rgba(255,255,255,.15);border-top-color:#38bdf8;animation:lrS .8s linear infinite}
#lr-ok{display:none;width:40px;height:40px;border-radius:50%;background:rgba(16,185,129,.18);border:2px solid #10b981;color:#10b981;align-items:center;justify-content:center;font-size:20px;font-weight:700}
#lr-er{display:none;width:40px;height:40px;border-radius:50%;background:rgba(239,68,68,.18);border:2px solid #ef4444;color:#ef4444;align-items:center;justify-content:center;font-size:19px;font-weight:700}
#lr-tt{font-size:14.5px;font-weight:600;margin-bottom:10px}
.lr-tr{width:100%;height:4px;background:rgba(255,255,255,.12);border-radius:999px;overflow:hidden}
#lr-fl{height:100%;width:32%;background:linear-gradient(90deg,#0284c7,#38bdf8);border-radius:999px;transition:width .2s ease}
@keyframes lrS{to{transform:rotate(360deg)}}
#efs-pv{position:fixed;inset:0;z-index:2147483645;background:#000;display:none;align-items:center;justify-content:center;overflow:hidden;touch-action:none;user-select:none}
#efs-pv.on{display:flex}
#efs-pvs{width:100vw;height:100vh;display:flex;align-items:center;justify-content:center;overflow:hidden;touch-action:none}
#efs-pvi{max-width:100vw;max-height:100vh;width:auto;height:auto;object-fit:contain;transform-origin:center center;transform:translate3d(0,0,0) scale(1);will-change:transform;pointer-events:none}
#efs-pvx{position:fixed;top:16px;right:16px;z-index:2147483646;width:42px;height:42px;border-radius:50%;border:1px solid rgba(255,255,255,.28);background:rgba(24,24,27,.82);color:#fff;font-size:18px;font-weight:700;display:flex;align-items:center;justify-content:center;cursor:pointer}
`;
(document.head||document.documentElement).appendChild(css);

const hud=document.createElement('div');
hud.id='lr-hud';
hud.innerHTML='<div id="lr-box"><div class="lr-rw"><div id="lr-sp"></div><div id="lr-ok">✓</div><div id="lr-er">!</div></div><div id="lr-tt">Menyimpan...</div><div class="lr-tr"><div id="lr-fl"></div></div></div>';
let hT=null,hI=null,hS=null;

function showHud(txt){
  if(!hud.parentNode&&document.body)document.body.appendChild(hud);
  clearTimeout(hT);clearTimeout(hS);clearInterval(hI);
  const sp=document.getElementById('lr-sp'),ok=document.getElementById('lr-ok'),er=document.getElementById('lr-er'),tt=document.getElementById('lr-tt'),fl=document.getElementById('lr-fl');
  if(sp)sp.style.display='block';if(ok)ok.style.display='none';if(er)er.style.display='none';
  if(tt)tt.textContent=txt||'Menyimpan...';
  if(fl){fl.style.background='linear-gradient(90deg,#0284c7,#38bdf8)';fl.style.width='32%';}
  hud.classList.add('on');
  let p=32;
  hI=setInterval(()=>{if(p<88){p+=(90-p)*0.16;if(fl)fl.style.width=p.toFixed(1)+'%';}},140);
  hS=setTimeout(()=>{clearInterval(hI);hud.classList.remove('on');},3200);
}
E.showHud=showHud;
window.__showLightroomSavingHud=showHud;
window.__onGallerySaveProgress=(id,c,t)=>{
  clearTimeout(hS);clearInterval(hI);
  const tt=document.getElementById('lr-tt'),fl=document.getElementById('lr-fl');
  if(tt)tt.textContent='Menyimpan '+c+'/'+t+'...';
  if(fl)fl.style.width=Math.min(95,Math.round((c/t)*92))+'%';
};
window.__onGallerySaveFinished=(id,okFlag)=>{
  clearTimeout(hS);clearInterval(hI);clearTimeout(hT);
  if(!hud.parentNode&&document.body)document.body.appendChild(hud);
  hud.classList.add('on');
  const sp=document.getElementById('lr-sp'),ok=document.getElementById('lr-ok'),er=document.getElementById('lr-er'),tt=document.getElementById('lr-tt'),fl=document.getElementById('lr-fl');
  if(sp)sp.style.display='none';
  if(okFlag){
    if(ok)ok.style.display='flex';if(er)er.style.display='none';if(tt)tt.textContent='Tersimpan';
    if(fl){fl.style.background='#10b981';fl.style.width='100%';}
  }else{
    if(ok)ok.style.display='none';if(er)er.style.display='flex';if(tt)tt.textContent='Gagal Menyimpan';
    if(fl){fl.style.background='#ef4444';fl.style.width='100%';}
  }
  hT=setTimeout(()=>hud.classList.remove('on'),okFlag?1050:1750);
};

if(window.AndroidGallery&&typeof window.AndroidGallery.saveBase64Image==='function'){
  const orig=window.AndroidGallery.saveBase64Image.bind(window.AndroidGallery);
  window.AndroidGallery.saveBase64Image=(u,n)=>{
    const id='s_'+Date.now();showHud('Menyimpan...');
    if(window.AndroidGallery.saveBase64ImageWithId)window.AndroidGallery.saveBase64ImageWithId(u,n||'',id);
    else{orig(u,n||'');setTimeout(()=>window.__onGallerySaveFinished(id,true),650);}
  };
}

E.getNativeCanvas=()=>{
  const cs=Array.from(document.querySelectorAll('canvas')).filter(c=>c.id!=='ig-pc'&&c.id!=='efs-ccanvas'&&c.width>=120&&c.height>=120&&c.getBoundingClientRect().width>30);
  if(cs.length){cs.sort((a,b)=>(b.width*b.height)-(a.width*a.height));return cs[0];}
  const ims=Array.from(document.querySelectorAll('img')).filter(i=>i.id!=='efs-pvi'&&i.complete&&i.naturalWidth>=150&&i.getBoundingClientRect().width>30);
  if(ims.length){ims.sort((a,b)=>(b.naturalWidth*b.naturalHeight)-(a.naturalWidth*a.naturalHeight));const o=document.createElement('canvas');o.width=ims[0].naturalWidth;o.height=ims[0].naturalHeight;o.getContext('2d').drawImage(ims[0],0,0);return o;}
  return null;
};
E.getOutCanvas=()=>E.mode==='frame'?(E.getNativeCanvas()||(E.drawCustom&&E.drawCustom())):((E.drawCustom&&E.drawCustom())||E.getNativeCanvas());

const pvMod=document.createElement('div');
pvMod.id='efs-pv';
pvMod.innerHTML='<button type="button" id="efs-pvx">✕</button><div id="efs-pvs"><img id="efs-pvi" alt="" /></div>';
function applyPv(){const i=document.getElementById('efs-pvi');if(i)i.style.transform='translate3d('+E.pv.x+'px,'+E.pv.y+'px,0) scale('+E.pv.s+')';}
function resetPv(){E.pv.s=1;E.pv.x=0;E.pv.y=0;applyPv();}
function openPv(){
  const c=E.getOutCanvas();if(!c)return;
  if(!pvMod.parentNode&&document.body)document.body.appendChild(pvMod);
  bindPinch();
  const i=document.getElementById('efs-pvi');
  resetPv();i.onload=resetPv;i.src=c.toDataURL('image/jpeg',0.95);
  E.pv.open=true;pvMod.classList.add('on');
}
function closePv(){if(!E.pv.open)return false;E.pv.open=false;pvMod.classList.remove('on');resetPv();return true;}
E.openPv=openPv;E.closePv=closePv;

window.__handleAndroidBack=()=>{
  if(E.pv.open)return closePv();
  const m=document.getElementById('ig-mod');if(m&&m.classList.contains('on')){m.classList.remove('on');return true;}
  return false;
};

let pBound=false;
function bindPinch(){
  if(pBound)return;
  const st=document.getElementById('efs-pvs'),xb=document.getElementById('efs-pvx');
  if(!st)return;pBound=true;
  if(xb)xb.addEventListener('click',closePv);
  let pin=false,pan=false,sd=1,ss=1,spx=0,spy=0,smx=0,smy=0,px0=0,py0=0,lt=0,lx=0,ly=0;
  const dist=(a,b)=>Math.hypot(b.clientX-a.clientX,b.clientY-a.clientY)||1;
  const off=(cx,cy)=>{const r=st.getBoundingClientRect();return{x:cx-(r.left+r.width/2),y:cy-(r.top+r.height/2)};};
  const clamp=()=>{const mx=window.innerWidth*(E.pv.s-1)*0.55,my=window.innerHeight*(E.pv.s-1)*0.55;E.pv.x=Math.max(-mx,Math.min(mx,E.pv.x));E.pv.y=Math.max(-my,Math.min(my,E.pv.y));};
  st.addEventListener('touchstart',e=>{
    if(!E.pv.open)return;
    if(e.touches.length===2){
      pin=true;pan=false;sd=dist(e.touches[0],e.touches[1]);ss=E.pv.s;spx=E.pv.x;spy=E.pv.y;
      const m=off((e.touches[0].clientX+e.touches[1].clientX)/2,(e.touches[0].clientY+e.touches[1].clientY)/2);smx=m.x;smy=m.y;
    }else if(e.touches.length===1){
      const now=Date.now(),tx=e.touches[0].clientX,ty=e.touches[0].clientY;
      if(now-lt<280&&Math.hypot(tx-lx,ty-ly)<28){
        if(E.pv.s>1.05)resetPv();
        else{const p=off(tx,ty);E.pv.s=2.5;E.pv.x=-p.x*1.2;E.pv.y=-p.y*1.2;clamp();applyPv();}
        lt=0;return;
      }
      lt=now;lx=tx;ly=ty;
      if(E.pv.s>1.01){pan=true;px0=tx-E.pv.x;py0=ty-E.pv.y;}
    }
  },{passive:false});
  st.addEventListener('touchmove',e=>{
    if(!E.pv.open)return;e.preventDefault();
    if(pin&&e.touches.length===2){
      const d=dist(e.touches[0],e.touches[1]),ns=Math.max(1,Math.min(5,ss*(d/sd)));
      const cm=off((e.touches[0].clientX+e.touches[1].clientX)/2,(e.touches[0].clientY+e.touches[1].clientY)/2),rt=ns/ss;
      E.pv.s=ns;E.pv.x=cm.x-(smx-spx)*rt;E.pv.y=cm.y-(smy-spy)*rt;
      if(ns<=1.02){E.pv.x=0;E.pv.y=0;}else clamp();
      applyPv();
    }else if(pan&&e.touches.length===1&&E.pv.s>1.01){
      E.pv.x=e.touches[0].clientX-px0;E.pv.y=e.touches[0].clientY-py0;clamp();applyPv();
    }
  },{passive:false});
  st.addEventListener('touchend',e=>{
    if(e.touches.length<2)pin=false;
    if(!e.touches.length){pan=false;if(E.pv.s<=1.03)resetPv();}
  });
}

const toData=b=>new Promise((res,rej)=>{const r=new FileReader();r.onloadend=()=>res(r.result);r.onerror=rej;r.readAsDataURL(b);});
navigator.canShare=()=>true;
navigator.share=async d=>{
  if(!window.AndroidShare)return;
  if(E.mode!=='frame'){const c=E.getOutCanvas();if(c){window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');return;}}
  if(d&&Array.isArray(d.files)&&d.files.length){
    if(d.files.length===1){const u=await toData(d.files[0]);window.AndroidShare.shareBase64Image(u,d.files[0].name||'EXIF_'+Date.now()+'.jpg');}
    else{const a=[];for(let i=0;i<d.files.length;i++)a.push({dataUrl:await toData(d.files[i]),fileName:d.files[i].name||'EXIF_'+(i+1)+'.jpg'});window.AndroidShare.shareMultipleBase64Images(JSON.stringify(a));}
    return;
  }
  if(d&&typeof d.url==='string'&&d.url.startsWith('data:image/')){window.AndroidShare.shareBase64Image(d.url,'EXIF_'+Date.now()+'.jpg');return;}
  const c=E.getOutCanvas();if(c)window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');
};

const btnLbl=b=>((b.innerText||'')+' '+(b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')+' '+(b.id||'')+' '+(typeof b.className==='string'?b.className:'')).toLowerCase();

['touchstart','pointerdown'].forEach(ev=>{
  document.addEventListener(ev,e=>{
    const b=e.target.closest('button,a,[role="button"]');
    if(!b||b.closest('#efs-bar,#efs-fpanel,#efs-pv'))return;
    if(/\b(simpan|save|download|unduh|export)\b/.test(btnLbl(b)))showHud('Menyimpan...');
  },true);
});

document.addEventListener('click',e=>{
  const b=e.target.closest('button,a,[role="button"]');
  if(!b||b.closest('#ig-mod,#efs-bar,#efs-fpanel,#efs-pv'))return;
  const l=btnLbl(b);
  if(/\b(simpan|save|download|unduh|export)\b/.test(l)){
    showHud('Menyimpan...');
    if(E.mode!=='frame'){
      e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
      const c=E.getOutCanvas(),id='c_'+Date.now();
      if(c&&window.AndroidGallery&&window.AndroidGallery.saveBase64ImageWithId)window.AndroidGallery.saveBase64ImageWithId(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg',id);
      else window.__onGallerySaveFinished(id,false);
    }
    return;
  }
  if(/\b(share|bagikan)\b/.test(l)){
    if(E.mode!=='frame'){
      e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
      const c=E.getOutCanvas();if(c&&window.AndroidShare)window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');
      return;
    }
    const h=b.getAttribute('href')||'';
    if(h.startsWith('data:image/')){e.preventDefault();e.stopPropagation();window.AndroidShare&&window.AndroidShare.shareBase64Image(h,'EXIF_'+Date.now()+'.jpg');return;}
    let called=false;const os=navigator.share;
    navigator.share=async(...a)=>{called=true;return os.apply(this,a);};
    setTimeout(()=>{navigator.share=os;if(!called&&window.AndroidShare){const c=E.getOutCanvas();if(c)window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg',0.95),'EXIF_'+Date.now()+'.jpg');}},150);
  }
},true);

function mountCore(){if(document.body){if(!hud.parentNode)document.body.appendChild(hud);if(!pvMod.parentNode)document.body.appendChild(pvMod);bindPinch();}}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mountCore);else mountCore();
})();
