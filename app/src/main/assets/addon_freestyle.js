(function(){
if(window.__efsFree)return;window.__efsFree=true;
const E=window.__efs=window.__efs||{mode:'frame',img:null,exif:'',free:{bg:'#ffffff',blur:false,pad:8,rad:0,ox:0,oy:0},split:{n:3,ar:'original'},pv:{open:false,s:1,x:0,y:0}};

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
#efs-fpanel{display:none;width:100%;max-width:540px;margin:8px auto 12px;background:#18181b;border:1px solid rgba(255,255,255,.12);border-radius:16px;padding:13px 14px;box-sizing:border-box;color:#fafafa;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
#efs-fpanel.on{display:block}
.efs-row{margin-bottom:13px}.efs-row:last-child{margin-bottom:0}
.efs-hd{display:flex;justify-content:space-between;align-items:center;font-size:12.5px;font-weight:600;color:#d4d4d8;margin-bottom:6px}
.efs-rst{background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.14);color:#fbbf24;font-size:11px;font-weight:600;padding:4px 9px;border-radius:7px;cursor:pointer}
.efs-sws{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.efs-sw{width:38px;height:38px;border-radius:10px;border:2px solid rgba(255,255,255,.2);cursor:pointer;padding:0;font-size:10.5px;font-weight:700;color:#fff}
.efs-sw.on{border-color:#f59e0b;transform:scale(1.07)}
.efs-sl{width:100%;accent-color:#f59e0b;height:26px}
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
    readExif(f).then(ex=>{if(ex)E.exif=ex;if(E.mode!=='frame')drawCustom();});
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

function getWmBounds(wmC){
  if(!wmC||wmC.width<10||wmC.height<10)return null;
  const w=wmC.width,h=wmC.height,x=wmC.getContext('2d');
  let d;try{d=x.getImageData(0,0,w,h).data;}catch(_){return null;}
  let minX=w,minY=h,maxX=-1,maxY=-1;
  const step=w>1600?3:2;
  for(let y=0;y<h;y+=step){
    const row=y*w*4;
    for(let c=0;c<w;c+=step){
      if(d[row+c*4+3]>12){
        if(c<minX)minX=c;if(c>maxX)maxX=c;
        if(y<minY)minY=y;if(y>maxY)maxY=y;
      }
    }
  }
  if(maxX<minX||maxY<minY)return null;
  return{minX:Math.max(0,minX-4),minY:Math.max(0,minY-4),maxX:Math.min(w-1,maxX+4),maxY:Math.min(h-1,maxY+4)};
}

function prepareWmSprite(wmC,sx,sy,sw,sh,forceWhite,invertDark){
  const sp=document.createElement('canvas');sp.__isInternal=true;
  sp.width=Math.max(1,Math.round(sw));sp.height=Math.max(1,Math.round(sh));
  const x=sp.getContext('2d');
  x.drawImage(wmC,sx,sy,sw,sh,0,0,sp.width,sp.height);
  if(!forceWhite&&!invertDark)return sp;
  try{
    const id=x.getImageData(0,0,sp.width,sp.height),d=id.data;
    for(let i=0;i<d.length;i+=4){
      if(d[i+3]<8)continue;
      const r=d[i],g=d[i+1],b=d[i+2];
      const maxC=Math.max(r,g,b),minC=Math.min(r,g,b),sat=maxC-minC;
      const lum=0.299*r+0.587*g+0.114*b;
      if(forceWhite){
        if(sat<38){d[i]=255;d[i+1]=255;d[i+2]=255;}
      }else if(invertDark&&sat<38&&lum<125){
        const v=255-Math.round(lum*0.6);
        d[i]=v;d[i+1]=v;d[i+2]=v;
      }
    }
    x.putImageData(id,0,0);
  }catch(_){}
  return sp;
}
  function getRegionLum(ctx,x,y,w,h){
  try{
    const rx=Math.max(0,Math.floor(x)),ry=Math.max(0,Math.floor(y));
    const rw=Math.min(ctx.canvas.width-rx,Math.max(1,Math.floor(w)));
    const rh=Math.min(ctx.canvas.height-ry,Math.max(1,Math.floor(h)));
    const d=ctx.getImageData(rx,ry,rw,rh).data;
    let sum=0,cnt=0;
    for(let i=0;i<d.length;i+=32){sum+=0.299*d[i]+0.587*d[i+1]+0.114*d[i+2];cnt++;}
    return cnt?sum/cnt:128;
  }catch(_){return 128;}
}

function wakeNativeRender(){
  const nc=E.getNativeCanvas&&E.getNativeCanvas();
  if(!nc||nc.__wmCanvas)return;
  const inp=Array.from(document.querySelectorAll('input[type="text"],input:not([type])')).find(el=>!el.closest('#efs-bar,#efs-fpanel,#ig-mod'));
  if(inp){inp.dispatchEvent(new Event('input',{bubbles:true}));return;}
  window.dispatchEvent(new Event('resize'));
}

function drawCustom(){
  const cc=document.getElementById('efs-ccanvas');if(!cc)return null;
  const im=getOrigImg(),nc=E.getNativeCanvas&&E.getNativeCanvas();
  if(!im){if(nc){cc.width=nc.width;cc.height=nc.height;cc.getContext('2d').drawImage(nc,0,0);}return cc;}

  const iw=im.naturalWidth,ih=im.naturalHeight,bd=Math.max(iw,ih),f=E.free;
  const wmC=nc&&nc.__wmCanvas,pr=nc&&nc.__photoRect,wb=wmC&&getWmBounds(wmC);

  if(E.mode==='noframe'){
    cc.width=iw;cc.height=ih;
    const x=cc.getContext('2d');
    x.imageSmoothingEnabled=true;x.imageSmoothingQuality='high';
    x.drawImage(im,0,0,iw,ih);
    if(wmC&&pr&&wb){
      const k=iw/pr.w;
      const insidePhoto=wb.minY>=pr.y-3&&(wb.maxY<=pr.y+pr.h+3);
      if(insidePhoto){
        const lum=getRegionLum(x,(wb.minX-pr.x)*k,(wb.minY-pr.y)*k,(wb.maxX-wb.minX)*k,(wb.maxY-wb.minY)*k);
        const sp=prepareWmSprite(wmC,pr.x,pr.y,pr.w,pr.h,lum<=165,false);
        x.save();
        if(lum<=165){x.shadowColor='rgba(0,0,0,.85)';x.shadowBlur=Math.max(6,Math.round(bd*0.008));}
        x.drawImage(sp,f.ox,f.oy,iw,ih);
        x.restore();
      }else{
        const sx=Math.min(pr.x,wb.minX),sr=Math.max(pr.x+pr.w,wb.maxX);
        const sw=sr-sx,sh=wb.maxY-wb.minY+1;
        const dw=sw*k,dh=sh*k;
        const isTop=(wb.maxY<=pr.y+4);
        const dx=(iw-dw)/2+f.ox;
        const dy=(isTop?Math.max(16,Math.round(ih*0.028)):(ih-dh-Math.max(16,Math.round(ih*0.028))))+f.oy;
        const lum=getRegionLum(x,dx,dy,dw,dh);
        const sp=prepareWmSprite(wmC,sx,wb.minY,sw,sh,lum<=165,false);
        x.save();
        if(lum<=165){x.shadowColor='rgba(0,0,0,.85)';x.shadowBlur=Math.max(6,Math.round(bd*0.008));x.shadowOffsetY=Math.max(1,Math.round(bd*0.002));}
        else{x.shadowColor='rgba(255,255,255,.7)';x.shadowBlur=Math.max(4,Math.round(bd*0.005));}
        x.drawImage(sp,dx,dy,dw,dh);
        x.restore();
      }
    }
    return cc;
  }

  const pad=Math.round(bd*(f.pad/100));
  const k=pr?(iw/pr.w):1;
  const hasBotWm=wmC&&pr&&wb&&(wb.minY>=pr.y+pr.h-4);
  const hasTopWm=wmC&&pr&&wb&&(wb.maxY<=pr.y+4);
  const origBot=hasBotWm?Math.max(0,pr.ch-(pr.y+pr.h)):0;
  const origTop=hasTopWm?Math.max(0,pr.y):0;
  const top=hasTopWm?Math.max(pad,Math.round(origTop*k)):pad;
  const bot=hasBotWm?Math.max(pad,Math.round(origBot*k)):pad;
  const cw=iw+pad*2,ch=ih+top+bot;
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
    x.beginPath();x.moveTo(pad+rad,top);x.lineTo(pad+iw-rad,top);x.quadraticCurveTo(pad+iw,top,pad+iw,top+rad);
    x.lineTo(pad+iw,top+ih-rad);x.quadraticCurveTo(pad+iw,top+ih,pad+iw-rad,top+ih);
    x.lineTo(pad+rad,top+ih);x.quadraticCurveTo(pad,top+ih,pad,top+ih-rad);
    x.lineTo(pad,top+rad);x.quadraticCurveTo(pad,top,pad+rad,top);x.closePath();
    x.fillStyle='#000';x.fill();x.shadowColor='transparent';x.clip();
  }
  x.drawImage(im,pad,top,iw,ih);x.restore();

  if(wmC&&pr&&wb){
    const bgDark=f.blur||(getRegionLum(x,cw*0.5,hasTopWm?top*0.5:(top+ih+bot*0.5),20,10)<128);
    if(hasBotWm&&origBot>2){
      const sp=prepareWmSprite(wmC,pr.x,pr.y+pr.h,pr.w,origBot,f.blur,bgDark);
      const dh=origBot*k,dy=top+ih+(bot-dh)/2+f.oy;
      x.save();
      if(f.blur){x.shadowColor='rgba(0,0,0,.8)';x.shadowBlur=Math.max(5,Math.round(bd*0.006));}
      x.drawImage(sp,pad+f.ox,dy,iw,dh);
      x.restore();
    }else if(hasTopWm&&origTop>2){
      const sp=prepareWmSprite(wmC,pr.x,0,pr.w,origTop,f.blur,bgDark);
      const dh=origTop*k,dy=(top-dh)/2+f.oy;
      x.save();
      if(f.blur){x.shadowColor='rgba(0,0,0,.8)';x.shadowBlur=Math.max(5,Math.round(bd*0.006));}
      x.drawImage(sp,pad+f.ox,dy,iw,dh);
      x.restore();
    }else{
      const sp=prepareWmSprite(wmC,pr.x,pr.y,pr.w,pr.h,true,false);
      x.save();x.shadowColor='rgba(0,0,0,.8)';x.shadowBlur=Math.max(5,Math.round(bd*0.006));
      x.drawImage(sp,pad+f.ox,top+f.oy,iw,ih);
      x.restore();
    }
  }
  return cc;
}
E.drawCustom=drawCustom;

const bar=document.createElement('div');
bar.id='efs-bar';
  bar.innerHTML='<div class="efs-grp"><button type="button" class="efs-mbtn on" data-m="frame">Bingkai</button><button type="button" class="efs-mbtn" data-m="noframe">Tanpa Frame</button><button type="button" class="efs-mbtn" data-m="freestyle">Gaya Bebas</button></div><button type="button" id="efs-spbtn">Split IG</button>';

const cwrap=document.createElement('div');cwrap.id='efs-cwrap';cwrap.innerHTML='<canvas id="efs-ccanvas"></canvas>';
const fpanel=document.createElement('div');fpanel.id='efs-fpanel';
fpanel.innerHTML='<div class="efs-row"><div class="efs-hd"><span>Latar</span><button type="button" class="efs-rst" id="efs-wrst">Reset Posisi Watermark</button></div><div class="efs-sws" id="efs-sws"><button type="button" class="efs-sw on" data-b="#ffffff" style="background:#fff;color:#000"></button><button type="button" class="efs-sw" data-b="#09090b" style="background:#09090b"></button><button type="button" class="efs-sw" data-b="#27272a" style="background:#27272a"></button><button type="button" class="efs-sw" data-b="#f5f5f4" style="background:#f5f5f4"></button><button type="button" class="efs-sw" data-b="blur" style="background:linear-gradient(135deg,#38bdf8,#f59e0b)">Blur</button><input type="color" id="efs-cp" value="#ffffff" style="width:38px;height:38px;border:none;background:none;padding:0"/></div></div><div class="efs-row"><div class="efs-hd"><span>Jarak Tepi</span><span id="efs-pv2">8%</span></div><input type="range" class="efs-sl" id="efs-ps" min="0" max="24" value="8"/></div><div class="efs-row"><div class="efs-hd"><span>Sudut Foto</span><span id="efs-rv">0</span></div><input type="range" class="efs-sl" id="efs-rs" min="0" max="40" value="0"/></div>';

function syncDom(){
  if(!document.body)return;
  if(!bar.parentNode)document.body.appendChild(bar);
  const ncs=Array.from(document.querySelectorAll('canvas')).filter(c=>!c.__isInternal&&c.id!=='ig-pc'&&c.id!=='efs-ccanvas');
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
  cwrap.classList.toggle('on',m!=='frame');
  fpanel.classList.toggle('on',m==='freestyle');
  if(m!=='frame'){wakeNativeRender();drawCustom();}
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
    if(m.id==='efs-ccanvas'&&E.mode!=='frame'){dr=true;dx0=e.clientX;dy0=e.clientY;}
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
  document.getElementById('efs-wrst').addEventListener('click',()=>{E.free.ox=0;E.free.oy=0;drawCustom();});

  let raf=0;
  new MutationObserver(()=>{if(raf)return;raf=requestAnimationFrame(()=>{raf=0;syncDom();});}).observe(document.body,{childList:true,subtree:true});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initFree);else initFree();
})();
