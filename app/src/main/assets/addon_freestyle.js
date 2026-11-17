(function(){
if(window.__efsFree)return;window.__efsFree=true;
const E=window.__efs=window.__efs||{mode:'frame',img:null,exif:'',free:{bg:'#ffffff',blur:false,pad:8,rad:0,wm:'bottom-center',ox:0,oy:0,txt:''},split:{n:3,ar:'original'},pv:{open:false,s:1,x:0,y:0}};

const css=document.createElement('style');
css.textContent=`
#efs-bar{position:fixed;top:0;left:0;right:0;height:54px;z-index:2147482000;background:rgba(18,18,21,.97);backdrop-filter:blur(12px);border-bottom:1px solid rgba(255,255,255,.12);display:flex;align-items:center;gap:6px;padding:6px 10px;box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
.efs-grp{display:flex;gap:4px;background:rgba(255,255,255,.07);padding:3px;border-radius:11px;flex:1}
.efs-mbtn{flex:1;height:36px;border:none;border-radius:8px;background:transparent;color:#a1a1aa;font-size:12px;font-weight:600;white-space:nowrap;padding:0 6px;cursor:pointer}
.efs-mbtn.on{background:#fafafa;color:#09090b}
#efs-spbtn{height:40px;padding:0 13px;border-radius:11px;border:1px solid rgba(245,158,11,.5);background:rgba(245,158,11,.18);color:#fbbf24;font-size:12.5px;font-weight:700;white-space:nowrap;cursor:pointer}
#efs-cwrap{display:none;flex-direction:column;align-items:center;width:100%;padding:10px 12px 6px;box-sizing:border-box}
#efs-cwrap.on{display:flex}
#efs-ccanvas{max-width:100%;max-height:54vh;width:auto;height:auto;border-radius:8px;box-shadow:0 12px 32px rgba(0,0,0,.45);touch-action:none}
#efs-fpanel{display:none;width:100%;max-width:540px;margin:8px auto 14px;background:#18181b;border:1px solid rgba(255,255,255,.12);border-radius:16px;padding:13px 14px;box-sizing:border-box;color:#fafafa;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
#efs-fpanel.on{display:block}
.efs-row{margin-bottom:13px}.efs-row:last-child{margin-bottom:0}
.efs-hd{display:flex;justify-content:space-between;font-size:12.5px;font-weight:600;color:#d4d4d8;margin-bottom:6px}
.efs-sws{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.efs-sw{width:38px;height:38px;border-radius:10px;border:2px solid rgba(255,255,255,.2);cursor:pointer;padding:0;font-size:10.5px;font-weight:700;color:#fff}
.efs-sw.on{border-color:#f59e0b;transform:scale(1.07)}
.efs-sl{width:100%;accent-color:#f59e0b;height:26px}
.efs-inp{width:100%;height:42px;border-radius:10px;border:1px solid rgba(255,255,255,.16);background:#09090b;color:#fafafa;padding:0 12px;font-size:13.5px;box-sizing:border-box}
.efs-wmg{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}
.efs-wmb{min-height:52px;padding:10px 12px;border-radius:12px;border:1.5px solid rgba(255,255,255,.15);background:#27272a;color:#e4e4e7;font-size:13.5px;font-weight:600;display:flex;align-items:center;justify-content:center;gap:8px;cursor:pointer}
.efs-wmb.on{background:rgba(245,158,11,.22);border-color:#f59e0b;color:#fbbf24}
.efs-wmb svg{width:22px;height:22px;flex-shrink:0}
.efs-big-wm{min-height:50px!important;min-width:68px!important;padding:10px 14px!important;font-size:13.5px!important;border-radius:12px!important}
.efs-big-wm svg{width:22px!important;height:22px!important}
`;
(document.head||document.documentElement).appendChild(css);

function readExif(file){
  return new Promise(res=>{
    try{
      if(!file||!file.type||!/jpe?g/i.test(file.type))return res('');
      const r=new FileReader();
      r.onload=e=>{
        try{
          const v=new DataView(e.target.result);
          if(v.byteLength<4||v.getUint16(0,false)!==0xFFD8)return res('');
          let o=2;
          while(o+4<v.byteLength){
            const m=v.getUint16(o,false);o+=2;
            if(m===0xFFE1){
              const l=v.getUint16(o,false);
              if(o+8<v.byteLength&&v.getUint32(o+2,false)===0x45786966){
                const ts=o+8,le=v.getUint16(ts,false)===0x4949,t={};
                const asc=(vo,c)=>{let s='',st=c<=4?vo:ts+v.getUint32(vo,le);for(let i=0;i<c-1&&st+i<v.byteLength;i++){const ch=v.getUint8(st+i);if(!ch)break;s+=String.fromCharCode(ch);}return s.trim();};
                const rat=vo=>{const p=ts+v.getUint32(vo,le);if(p+8>v.byteLength)return null;const d=v.getUint32(p+4,le);return d?v.getUint32(p,le)/d:null;};
                const ifd=rel=>{const d=ts+rel;if(d+2>v.byteLength)return;const n=v.getUint16(d,le);for(let i=0;i<n;i++){const en=d+2+i*12;if(en+12>v.byteLength)break;const tg=v.getUint16(en,le),tp=v.getUint16(en+2,le),cnt=v.getUint32(en+4,le),vo=en+8;if(tg===0x010F&&tp===2)t.mk=asc(vo,cnt);else if(tg===0x0110&&tp===2)t.md=asc(vo,cnt);else if(tg===0x8769)ifd(v.getUint32(vo,le));else if(tg===0x829A&&tp===5)t.ex=rat(vo);else if(tg===0x829D&&tp===5)t.fn=rat(vo);else if(tg===0x8827&&tp===3)t.iso=v.getUint16(vo,le);else if(tg===0x920A&&tp===5)t.fl=rat(vo);}};
                ifd(v.getUint32(ts+4,le));
                const p=[];
                if(t.md){let c=t.md;if(t.mk&&!c.toLowerCase().startsWith(t.mk.toLowerCase()))c=t.mk+' '+c;p.push(c);}
                if(t.fl)p.push(Math.round(t.fl)+'mm');
                if(t.fn)p.push('f/'+(Math.round(t.fn*10)/10));
                if(t.ex)p.push(t.ex>=1?(Math.round(t.ex*10)/10)+'s':'1/'+Math.round(1/t.ex)+'s');
                if(t.iso)p.push('ISO '+t.iso);
                return res(p.join(' • '));
              }
              o+=l;
            }else if((m&0xFF00)===0xFF00){if(m===0xFFDA||m===0xFFD9)break;o+=v.getUint16(o,false);}else break;
          }
          res('');
        }catch(_){res('');}
      };
      r.onerror=()=>res('');
      r.readAsArrayBuffer(file.slice(0,131072));
    }catch(_){res('');}
  });
}

document.addEventListener('change',e=>{
  const inp=e.target;
  if(inp&&inp.tagName==='INPUT'&&inp.type==='file'&&inp.files&&inp.files[0]){
    const f=inp.files[0];
    if(!f.type||!f.type.startsWith('image/'))return;
    readExif(f).then(ex=>{if(ex){E.exif=ex;if(!E.free.txt)E.free.txt=ex;const ti=document.getElementById('efs-wt');if(ti&&!ti.value)ti.value=ex;}if(E.mode!=='frame')drawCustom();});
    const fr=new FileReader();
    fr.onload=ev=>{const im=new Image();im.onload=()=>{E.img=im;E.free.ox=0;E.free.oy=0;if(E.mode!=='frame')drawCustom();};im.src=ev.target.result;};
    fr.readAsDataURL(f);
  }
},true);

function getOrigImg(){
  if(E.img&&E.img.complete&&E.img.naturalWidth>0)return E.img;
  const ims=Array.from(document.querySelectorAll('img')).filter(i=>i.id!=='efs-pvi'&&i.complete&&i.naturalWidth>=150);
  if(ims.length){ims.sort((a,b)=>(b.naturalWidth*b.naturalHeight)-(a.naturalWidth*a.naturalHeight));E.img=ims[0];return ims[0];}
  return null;
}
function getExifFallback(){
  if(E.exif)return E.exif;
  const els=Array.from(document.querySelectorAll('input,span,div,p')).filter(el=>!el.closest('#efs-bar,#efs-fpanel,#ig-mod'));
  for(const el of els){
    const t=(el.value||el.innerText||'').trim();
    if(t.length>4&&t.length<90&&/\b(iso|f\/|mm|1\/\d+|nikon|canon|sony|fuji|leica|apple|iphone|samsung|xiaomi)\b/i.test(t)){E.exif=t;return t;}
  }
  return '';
}

function drawCustom(){
  const cc=document.getElementById('efs-ccanvas');if(!cc)return null;
  const im=getOrigImg();
  if(!im){const nc=E.getNativeCanvas&&E.getNativeCanvas();if(nc){cc.width=nc.width;cc.height=nc.height;cc.getContext('2d').drawImage(nc,0,0);}return cc;}
  const iw=im.naturalWidth,ih=im.naturalHeight;
  if(E.mode==='noframe'){cc.width=iw;cc.height=ih;const x=cc.getContext('2d');x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';x.drawImage(im,0,0,iw,ih);return cc;}
  const f=E.free,bd=Math.max(iw,ih),pad=Math.round(bd*(f.pad/100));
  const bot=(f.wm==='bottom-center'||f.wm==='bottom-left')?Math.max(pad,Math.round(bd*0.065)):pad;
  const cw=iw+pad*2,ch=ih+pad+bot;
  cc.width=cw;cc.height=ch;
  const x=cc.getContext('2d');x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';
  if(f.blur){
    x.save();x.filter='blur('+Math.max(24,Math.round(bd*0.025))+'px) brightness(0.75)';
    const sc=Math.max(cw/iw,ch/ih)*1.15;x.drawImage(im,(cw-iw*sc)/2,(ch-ih*sc)/2,iw*sc,ih*sc);x.restore();
  }else{x.fillStyle=f.bg;x.fillRect(0,0,cw,ch);}
  const rad=Math.round((f.rad/40)*(Math.min(iw,ih)*0.08));
  x.save();
  if(pad>0){x.shadowColor='rgba(0,0,0,.36)';x.shadowBlur=Math.round(bd*0.025);x.shadowOffsetY=Math.round(bd*0.01);}
  if(rad>0){
    x.beginPath();x.moveTo(pad+rad,pad);x.lineTo(pad+iw-rad,pad);x.quadraticCurveTo(pad+iw,pad,pad+iw,pad+rad);
    x.lineTo(pad+iw,pad+ih-rad);x.quadraticCurveTo(pad+iw,pad+ih,pad+iw-rad,pad+ih);
    x.lineTo(pad+rad,pad+ih);x.quadraticCurveTo(pad,pad+ih,pad,pad+ih-rad);
    x.lineTo(pad,pad+rad);x.quadraticCurveTo(pad,pad,pad+rad,pad);x.closePath();
    x.fillStyle='#000';x.fill();x.shadowColor='transparent';x.clip();
  }
  x.drawImage(im,pad,pad,iw,ih);x.restore();
  const wt=(f.txt||getExifFallback()||'').trim();
  if(f.wm!=='none'&&wt){
    const fs=Math.max(20,Math.round(bd*0.021));
    x.save();x.font='600 '+fs+'px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif';x.textBaseline='middle';
    const dark=!f.blur&&(f.bg==='#09090b'||f.bg==='#18181b'||f.bg==='#000000');
    let col=dark?'#f4f4f5':'#18181b';
    if(f.blur||f.wm==='inside-bottom'){col='#fff';x.shadowColor='rgba(0,0,0,.72)';x.shadowBlur=Math.round(fs*0.45);}
    x.fillStyle=col;
    let bx=cw/2,by=pad+ih+bot/2;
    if(f.wm==='bottom-left'){x.textAlign='left';bx=pad+Math.max(12,Math.round(fs*0.5));}
    else if(f.wm==='inside-bottom'){x.textAlign='center';by=pad+ih-Math.round(fs*1.6);}
    else x.textAlign='center';
    x.fillText(wt,bx+f.ox,by+f.oy);x.restore();
  }
  return cc;
}
E.drawCustom=drawCustom;

const bar=document.createElement('div');
bar.id='efs-bar';
bar.innerHTML='<div class="efs-grp"><button type="button" class="efs-mbtn on" data-m="frame">Bingkai</button><button type="button" class="efs-mbtn" data-m="noframe">Tanpa Frame</button><button type="button" class="efs-mbtn" data-m="freestyle">Gaya Bebas</button></div><button type="button" id="efs-spbtn">Split IG</button>';

const cwrap=document.createElement('div');cwrap.id='efs-cwrap';cwrap.innerHTML='<canvas id="efs-ccanvas"></canvas>';
const fpanel=document.createElement('div');fpanel.id='efs-fpanel';
fpanel.innerHTML='<div class="efs-row"><div class="efs-hd"><span>Latar</span></div><div class="efs-sws" id="efs-sws"><button type="button" class="efs-sw on" data-b="#ffffff" style="background:#fff;color:#000"></button><button type="button" class="efs-sw" data-b="#09090b" style="background:#09090b"></button><button type="button" class="efs-sw" data-b="#27272a" style="background:#27272a"></button><button type="button" class="efs-sw" data-b="#f5f5f4" style="background:#f5f5f4"></button><button type="button" class="efs-sw" data-b="blur" style="background:linear-gradient(135deg,#38bdf8,#f59e0b)">Blur</button><input type="color" id="efs-cp" value="#ffffff" style="width:38px;height:38px;border:none;background:none;padding:0"/></div></div><div class="efs-row"><div class="efs-hd"><span>Jarak Tepi</span><span id="efs-pv2">8%</span></div><input type="range" class="efs-sl" id="efs-ps" min="0" max="24" value="8"/></div><div class="efs-row"><div class="efs-hd"><span>Sudut Foto</span><span id="efs-rv">0</span></div><input type="range" class="efs-sl" id="efs-rs" min="0" max="40" value="0"/></div><div class="efs-row"><div class="efs-hd"><span>Tata Letak Watermark</span></div><div class="efs-wmg" id="efs-wmg"><button type="button" class="efs-wmb on" data-w="bottom-center"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="18" x2="16" y2="18"/></svg><span>Bawah Tengah</span></button><button type="button" class="efs-wmb" data-w="bottom-left"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="6" y1="18" x2="13" y2="18"/></svg><span>Bawah Kiri</span></button><button type="button" class="efs-wmb" data-w="inside-bottom"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="15" x2="16" y2="15"/></svg><span>Dalam Foto</span></button><button type="button" class="efs-wmb" data-w="none"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="5" y1="5" x2="19" y2="19"/></svg><span>Sembunyi</span></button></div></div><div class="efs-row"><input type="text" class="efs-inp" id="efs-wt" placeholder="Teks EXIF (geser posisi di foto)"/></div>';

function syncDom(){
  if(!document.body)return;
  if(!bar.parentNode)document.body.appendChild(bar);
  const ncs=Array.from(document.querySelectorAll('canvas')).filter(c=>c.id!=='ig-pc'&&c.id!=='efs-ccanvas');
  if(ncs.length&&ncs[0].parentElement){
    if(cwrap.parentNode!==ncs[0].parentElement){ncs[0].parentElement.insertBefore(cwrap,ncs[0]);ncs[0].parentElement.insertBefore(fpanel,ncs[0].nextSibling);}
    ncs.forEach(c=>{c.style.display=E.mode==='frame'?'':'none';});
  }else if(!cwrap.parentNode){document.body.insertBefore(cwrap,document.body.firstChild);document.body.insertBefore(fpanel,cwrap.nextSibling);}
  document.querySelectorAll('button,[role="button"]').forEach(b=>{
    if(b.closest('#efs-bar,#efs-fpanel,#ig-mod,#efs-pv'))return;
    const pt=(b.parentElement&&b.parentElement.parentElement)?(b.parentElement.parentElement.innerText||'').toLowerCase():'';
    const bt=(b.innerText||b.getAttribute('aria-label')||'').toLowerCase();
    if(/\b(watermark|tata letak|layout|posisi|align|logo|teks)\b/.test(pt)||/\b(kiri|tengah|kanan|bawah|atas|minimal|klasik|modern|leica|fuji|sony)\b/.test(bt))b.classList.add('efs-big-wm');
  });
}

function setMode(m){
  E.mode=m;
  bar.querySelectorAll('.efs-mbtn').forEach(b=>b.classList.toggle('on',b.getAttribute('data-m')===m));
  syncDom();
  if(m==='frame'){cwrap.classList.remove('on');fpanel.classList.remove('on');}
  else{
    cwrap.classList.add('on');fpanel.classList.toggle('on',m==='freestyle');
    const ti=document.getElementById('efs-wt');if(ti&&!ti.value){ti.value=E.free.txt||getExifFallback()||'';E.free.txt=ti.value;}
    drawCustom();
  }
}

let bound=false;
function initFree(){
  if(!document.body)return;
  syncDom();
  if(bound)return;bound=true;

  bar.querySelectorAll('.efs-mbtn').forEach(b=>b.addEventListener('click',()=>setMode(b.getAttribute('data-m'))));
  document.getElementById('efs-spbtn').addEventListener('click',()=>{E.openSplit&&E.openSplit();});

  let sx=0,sy=0,st=0,dr=false,dx0=0,dy0=0;
  document.addEventListener('pointerdown',e=>{
    const m=e.target.closest('canvas,img');if(!m||m.id==='ig-pc'||m.id==='efs-pvi')return;
    sx=e.clientX;sy=e.clientY;st=Date.now();
    if(m.id==='efs-ccanvas'&&E.mode==='freestyle'&&E.free.wm!=='none'){dr=true;dx0=e.clientX;dy0=e.clientY;}
  },true);
  document.addEventListener('pointermove',e=>{
    if(!dr)return;const cc=document.getElementById('efs-ccanvas');if(!cc)return;
    const r=cc.getBoundingClientRect();if(r.width<=0)return;const k=cc.width/r.width;
    E.free.ox+=(e.clientX-dx0)*k;E.free.oy+=(e.clientY-dy0)*k;dx0=e.clientX;dy0=e.clientY;drawCustom();
  },true);
  document.addEventListener('pointerup',e=>{
    dr=false;const m=e.target.closest('canvas,img');if(!m||m.id==='ig-pc'||m.id==='efs-pvi')return;
    if(Math.hypot(e.clientX-sx,e.clientY-sy)<12&&Date.now()-st<400){e.preventDefault();e.stopPropagation();E.openPv&&E.openPv();}
  },true);

  document.querySelectorAll('#efs-sws .efs-sw').forEach(s=>s.addEventListener('click',()=>{
    document.querySelectorAll('#efs-sws .efs-sw').forEach(x=>x.classList.remove('on'));s.classList.add('on');
    const v=s.getAttribute('data-b');if(v==='blur')E.free.blur=true;else{E.free.blur=false;E.free.bg=v;}drawCustom();
  }));
  document.getElementById('efs-cp').addEventListener('input',e=>{document.querySelectorAll('#efs-sws .efs-sw').forEach(x=>x.classList.remove('on'));E.free.blur=false;E.free.bg=e.target.value;drawCustom();});
  document.getElementById('efs-ps').addEventListener('input',e=>{E.free.pad=parseInt(e.target.value,10)||0;document.getElementById('efs-pv2').textContent=E.free.pad+'%';drawCustom();});
  document.getElementById('efs-rs').addEventListener('input',e=>{E.free.rad=parseInt(e.target.value,10)||0;document.getElementById('efs-rv').textContent=E.free.rad;drawCustom();});
  document.querySelectorAll('#efs-wmg .efs-wmb').forEach(b=>b.addEventListener('click',()=>{
    document.querySelectorAll('#efs-wmg .efs-wmb').forEach(x=>x.classList.remove('on'));b.classList.add('on');
    E.free.wm=b.getAttribute('data-w');E.free.ox=0;E.free.oy=0;drawCustom();
  }));
  document.getElementById('efs-wt').addEventListener('input',e=>{E.free.txt=e.target.value;drawCustom();});

  let raf=0;
  new MutationObserver(()=>{if(raf)return;raf=requestAnimationFrame(()=>{raf=0;syncDom();});}).observe(document.body,{childList:true,subtree:true});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initFree);else initFree();
})();
