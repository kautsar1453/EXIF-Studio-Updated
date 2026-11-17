(function () {
  if (window.__efsBooted) return;
  window.__efsBooted = true;

  const STATE = {
    mode: 'frame', // 'frame' | 'noframe' | 'freestyle'
    origImg: null,
    exifText: '',
    free: {
      bg: '#ffffff',
      blur: false,
      pad: 8,
      rad: 0,
      wm: 'bottom-center',
      ox: 0,
      oy: 0,
      txt: ''
    },
    split: {
      n: 3,
      ar: 'original'
    },
    pv: {
      open: false,
      s: 1,
      x: 0,
      y: 0
    }
  };

  // =========================================================
  // 1. CSS STYLES
  // =========================================================
  function injectStyles() {
    if (document.getElementById('efs-style')) return;
    const style = document.createElement('style');
    style.id = 'efs-style';
    style.textContent = `
      body {
        padding-top: 56px !important;
        box-sizing: border-box !important;
      }
      #efs-bar {
        position: fixed; top: 0; left: 0; right: 0; height: 54px;
        z-index: 2147482000;
        background: rgba(18, 18, 22, 0.97);
        backdrop-filter: blur(12px);
        border-bottom: 1px solid rgba(255, 255, 255, 0.12);
        display: flex; align-items: center; gap: 6px;
        padding: 6px 10px; box-sizing: border-box;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      }
      .efs-grp {
        display: flex; gap: 4px; background: rgba(255, 255, 255, 0.07);
        padding: 3px; border-radius: 11px; flex: 1; min-width: 0;
      }
      .efs-mbtn {
        flex: 1; height: 34px; border: none; border-radius: 8px;
        background: transparent; color: #a1a1aa;
        font-size: 12px; font-weight: 600; white-space: nowrap;
        padding: 0 6px; cursor: pointer;
      }
      .efs-mbtn.on { background: #fafafa; color: #09090b; }
      #efs-spbtn {
        height: 38px; padding: 0 12px; border-radius: 11px;
        border: 1px solid rgba(245, 158, 11, 0.55);
        background: rgba(245, 158, 11, 0.2);
        color: #fbbf24; font-size: 12px; font-weight: 700;
        white-space: nowrap; cursor: pointer; flex-shrink: 0;
      }

      /* Custom Canvas & Freestyle Panel */
      #efs-cwrap {
        display: none; flex-direction: column; align-items: center;
        width: 100%; padding: 12px 12px 6px; box-sizing: border-box;
      }
      #efs-cwrap.on { display: flex; }
      #efs-ccanvas {
        max-width: 100%; max-height: 52vh; width: auto; height: auto;
        border-radius: 8px; box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
        touch-action: none;
      }
      #efs-fpanel {
        display: none; width: 100%; max-width: 540px;
        margin: 8px auto 14px; background: #18181b;
        border: 1px solid rgba(255, 255, 255, 0.12);
        border-radius: 16px; padding: 14px; box-sizing: border-box;
        color: #fafafa;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      }
      #efs-fpanel.on { display: block; }
      .efs-row { margin-bottom: 14px; }
      .efs-row:last-child { margin-bottom: 0; }
      .efs-hd {
        display: flex; justify-content: space-between;
        font-size: 12.5px; font-weight: 600; color: #d4d4d8; margin-bottom: 7px;
      }
      .efs-sws { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
      .efs-sw {
        width: 38px; height: 38px; border-radius: 10px;
        border: 2px solid rgba(255, 255, 255, 0.2);
        cursor: pointer; padding: 0; font-size: 10px; font-weight: 700; color: #fff;
      }
      .efs-sw.on { border-color: #f59e0b; transform: scale(1.08); }
      .efs-sl { width: 100%; accent-color: #f59e0b; height: 26px; }
      .efs-inp {
        width: 100%; height: 42px; border-radius: 10px;
        border: 1px solid rgba(255, 255, 255, 0.16);
        background: #09090b; color: #fafafa;
        padding: 0 12px; font-size: 13px; box-sizing: border-box;
      }
      .efs-wmg { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
      .efs-wmb {
        min-height: 52px; padding: 10px 12px; border-radius: 12px;
        border: 1.5px solid rgba(255, 255, 255, 0.15);
        background: #27272a; color: #e4e4e7;
        font-size: 13px; font-weight: 600;
        display: flex; align-items: center; justify-content: center; gap: 8px;
        cursor: pointer;
      }
      .efs-wmb.on {
        background: rgba(245, 158, 11, 0.22);
        border-color: #f59e0b; color: #fbbf24;
      }
      .efs-wmb svg { width: 22px; height: 22px; flex-shrink: 0; }
      .efs-big-wm {
        min-height: 48px !important; min-width: 68px !important;
        padding: 10px 14px !important; font-size: 13.5px !important;
        border-radius: 12px !important;
      }
      .efs-big-wm svg { width: 22px !important; height: 22px !important; }

      /* Lightroom Saving HUD */
      #lr-hud {
        position: fixed; inset: 0; z-index: 2147483647;
        background: rgba(9, 9, 11, 0.72);
        backdrop-filter: blur(8px);
        display: none; align-items: center; justify-content: center;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      }
      #lr-hud.on { display: flex; }
      #lr-box {
        width: min(240px, 76vw);
        background: rgba(24, 24, 27, 0.98);
        border: 1px solid rgba(255, 255, 255, 0.15);
        border-radius: 18px; padding: 20px 18px 18px;
        box-shadow: 0 24px 48px rgba(0, 0, 0, 0.65);
        color: #fff; text-align: center;
      }
      .lr-rw {
        width: 44px; height: 44px; margin: 0 auto 12px;
        display: flex; align-items: center; justify-content: center;
      }
      #lr-sp {
        width: 40px; height: 40px; border-radius: 50%;
        border: 3px solid rgba(255, 255, 255, 0.15);
        border-top-color: #38bdf8;
        animation: lrSpin 0.8s linear infinite;
      }
      #lr-ok {
        display: none; width: 40px; height: 40px; border-radius: 50%;
        background: rgba(16, 185, 129, 0.2); border: 2px solid #10b981;
        color: #10b981; align-items: center; justify-content: center;
        font-size: 20px; font-weight: 700;
      }
      #lr-er {
        display: none; width: 40px; height: 40px; border-radius: 50%;
        background: rgba(239, 68, 68, 0.2); border: 2px solid #ef4444;
        color: #ef4444; align-items: center; justify-content: center;
        font-size: 19px; font-weight: 700;
      }
      #lr-tt { font-size: 14.5px; font-weight: 600; margin-bottom: 10px; }
      .lr-tr {
        width: 100%; height: 4px; background: rgba(255, 255, 255, 0.12);
        border-radius: 999px; overflow: hidden;
      }
      #lr-fl {
        height: 100%; width: 32%;
        background: linear-gradient(90deg, #0284c7, #38bdf8);
        border-radius: 999px; transition: width 0.2s ease;
      }
      @keyframes lrSpin { to { transform: rotate(360deg); } }

      /* Fullscreen Pinch-to-Zoom Preview */
      #efs-pv {
        position: fixed; inset: 0; z-index: 2147483645;
        background: #000; display: none;
        align-items: center; justify-content: center;
        overflow: hidden; touch-action: none; user-select: none;
        -webkit-user-select: none;
      }
      #efs-pv.on { display: flex; }
      #efs-pvs {
        width: 100vw; height: 100vh;
        display: flex; align-items: center; justify-content: center;
        overflow: hidden; touch-action: none;
      }
      #efs-pvi {
        max-width: 100vw; max-height: 100vh;
        width: auto; height: auto; object-fit: contain;
        transform-origin: center center;
        transform: translate3d(0, 0, 0) scale(1);
        will-change: transform; pointer-events: none;
        touch-action: none;
      }
      #efs-pvz {
        position: fixed; top: 14px; left: 14px; z-index: 2147483646;
        height: 36px; padding: 0 12px; border-radius: 999px;
        border: 1px solid rgba(255, 255, 255, 0.2);
        background: rgba(24, 24, 27, 0.85); color: #fbbf24;
        font-size: 12.5px; font-weight: 700; display: flex; align-items: center;
      }
      #efs-pvx {
        position: fixed; top: 14px; right: 14px; z-index: 2147483646;
        height: 36px; padding: 0 14px; border-radius: 999px;
        border: 1px solid rgba(255, 255, 255, 0.25);
        background: rgba(24, 24, 27, 0.88); color: #fff;
        font-size: 13px; font-weight: 600; cursor: pointer;
      }
      #efs-pvhint {
        position: fixed; bottom: 18px; left: 50%; transform: translateX(-50%);
        z-index: 2147483646; padding: 6px 14px; border-radius: 999px;
        background: rgba(24, 24, 27, 0.82); color: #d4d4d8;
        font-size: 11.5px; pointer-events: none; white-space: nowrap;
      }

      /* Split IG Bottom Sheet */
      #ig-mod {
        position: fixed; inset: 0; z-index: 2147483640;
        background: rgba(9, 9, 11, 0.85);
        backdrop-filter: blur(10px);
        display: none; flex-direction: column; justify-content: flex-end;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        color: #fafafa;
      }
      #ig-mod.on { display: flex; }
      .ig-sh {
        background: #141417;
        border-top: 1px solid rgba(255, 255, 255, 0.14);
        border-radius: 22px 22px 0 0;
        padding: 16px 18px 24px; max-height: 90vh; overflow-y: auto;
      }
      .ig-hd {
        display: flex; align-items: center; justify-content: space-between;
        margin-bottom: 12px;
      }
      .ig-xb {
        width: 34px; height: 34px; border-radius: 50%;
        border: 1px solid rgba(255, 255, 255, 0.16);
        background: rgba(255, 255, 255, 0.08);
        color: #e4e4e7; font-size: 16px; cursor: pointer;
      }
      .ig-pb {
        background: #09090b; border: 1px solid rgba(255, 255, 255, 0.12);
        border-radius: 14px; padding: 12px; margin-bottom: 14px;
        display: flex; flex-direction: column; align-items: center;
      }
      .ig-ps {
        position: relative; max-width: 100%; max-height: 220px;
        display: inline-block; overflow: hidden; border-radius: 6px;
      }
      #ig-pc {
        display: block; max-width: 100%; max-height: 220px;
        width: auto; height: auto;
      }
      #ig-ov { position: absolute; inset: 0; display: flex; pointer-events: none; }
      .ig-cl {
        flex: 1; height: 100%;
        border-right: 1.5px dashed rgba(251, 191, 36, 0.95);
        position: relative; box-sizing: border-box;
      }
      .ig-cl:last-child { border-right: none; }
      .ig-bg {
        position: absolute; top: 6px; left: 6px;
        background: rgba(0, 0, 0, 0.75); color: #fbbf24;
        font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 6px;
      }
      #ig-dm { margin-top: 8px; font-size: 11.5px; color: #a1a1aa; }
      .ig-sr {
        display: grid; grid-template-columns: repeat(3, 1fr);
        gap: 8px; margin-bottom: 12px;
      }
      .ig-sb {
        min-height: 44px; padding: 10px 8px; border-radius: 11px;
        border: 1px solid rgba(255, 255, 255, 0.14);
        background: #1f1f23; color: #d4d4d8;
        font-size: 13px; font-weight: 600; cursor: pointer;
      }
      .ig-sb.on {
        background: rgba(245, 158, 11, 0.2);
        border-color: #f59e0b; color: #fbbf24;
      }
      .ig-ac {
        display: grid; grid-template-columns: 1fr 1fr;
        gap: 10px; margin-top: 4px;
      }
      .ig-bp {
        min-height: 46px; border-radius: 12px; border: none;
        background: #f59e0b; color: #09090b;
        font-size: 13.5px; font-weight: 700; cursor: pointer;
      }
      .ig-bs {
        min-height: 46px; border-radius: 12px;
        border: 1px solid rgba(255, 255, 255, 0.2);
        background: #27272a; color: #fff;
        font-size: 13.5px; font-weight: 600; cursor: pointer;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  // =========================================================
  // 2. EXIF READER & FILE INPUT LISTENER
  // =========================================================
  function readExifFromFile(file) {
    return new Promise((resolve) => {
      try {
        if (!file || !file.type || !/jpe?g/i.test(file.type)) return resolve('');
        const reader = new FileReader();
        reader.onload = (e) => {
          try {
            const view = new DataView(e.target.result);
            if (view.byteLength < 4 || view.getUint16(0, false) !== 0xFFD8) return resolve('');
            let offset = 2;
            while (offset + 4 < view.byteLength) {
              const marker = view.getUint16(offset, false);
              offset += 2;
              if (marker === 0xFFE1) {
                const len = view.getUint16(offset, false);
                if (offset + 8 < view.byteLength && view.getUint32(offset + 2, false) === 0x45786966) {
                  const tiffStart = offset + 8;
                  const little = view.getUint16(tiffStart, false) === 0x4949;
                  const tags = {};
                  const readAscii = (valOff, count) => {
                    let str = '';
                    const start = count <= 4 ? valOff : tiffStart + view.getUint32(valOff, little);
                    for (let i = 0; i < count - 1 && start + i < view.byteLength; i++) {
                      const ch = view.getUint8(start + i);
                      if (!ch) break;
                      str += String.fromCharCode(ch);
                    }
                    return str.trim();
                  };
                  const readRational = (valOff) => {
                    const ptr = tiffStart + view.getUint32(valOff, little);
                    if (ptr + 8 > view.byteLength) return null;
                    const den = view.getUint32(ptr + 4, little);
                    return den ? view.getUint32(ptr, little) / den : null;
                  };
                  const parseIfd = (rel) => {
                    const dir = tiffStart + rel;
                    if (dir + 2 > view.byteLength) return;
                    const num = view.getUint16(dir, little);
                    for (let i = 0; i < num; i++) {
                      const ent = dir + 2 + i * 12;
                      if (ent + 12 > view.byteLength) break;
                      const tag = view.getUint16(ent, little);
                      const type = view.getUint16(ent + 2, little);
                      const count = view.getUint32(ent + 4, little);
                      const vo = ent + 8;
                      if (tag === 0x010F && type === 2) tags.make = readAscii(vo, count);
                      else if (tag === 0x0110 && type === 2) tags.model = readAscii(vo, count);
                      else if (tag === 0x8769) parseIfd(view.getUint32(vo, little));
                      else if (tag === 0x829A && type === 5) tags.exp = readRational(vo);
                      else if (tag === 0x829D && type === 5) tags.fnum = readRational(vo);
                      else if (tag === 0x8827 && type === 3) tags.iso = view.getUint16(vo, little);
                      else if (tag === 0x920A && type === 5) tags.fl = readRational(vo);
                    }
                  };
                  parseIfd(view.getUint32(tiffStart + 4, little));
                  const parts = [];
                  if (tags.model) {
                    let cam = tags.model;
                    if (tags.make && !cam.toLowerCase().startsWith(tags.make.toLowerCase())) cam = tags.make + ' ' + cam;
                    parts.push(cam);
                  }
                  if (tags.fl) parts.push(Math.round(tags.fl) + 'mm');
                  if (tags.fnum) parts.push('f/' + (Math.round(tags.fnum * 10) / 10));
                  if (tags.exp) parts.push(tags.exp >= 1 ? (Math.round(tags.exp * 10) / 10) + 's' : '1/' + Math.round(1 / tags.exp) + 's');
                  if (tags.iso) parts.push('ISO ' + tags.iso);
                  return resolve(parts.join(' • '));
                }
                offset += len;
              } else if ((marker & 0xFF00) === 0xFF00) {
                if (marker === 0xFFDA || marker === 0xFFD9) break;
                offset += view.getUint16(offset, false);
              } else break;
            }
            resolve('');
          } catch (_) { resolve(''); }
        };
        reader.onerror = () => resolve('');
        reader.readAsArrayBuffer(file.slice(0, 131072));
      } catch (_) { resolve(''); }
    });
  }

  document.addEventListener('change', (e) => {
    const inp = e.target;
    if (inp && inp.tagName === 'INPUT' && inp.type === 'file' && inp.files && inp.files[0]) {
      const f = inp.files[0];
      if (!f.type || !f.type.startsWith('image/')) return;
      readExifFromFile(f).then((ex) => {
        if (ex) {
          STATE.exifText = ex;
          if (!STATE.free.txt) STATE.free.txt = ex;
          const wt = document.getElementById('efs-wt');
          if (wt && !wt.value) wt.value = ex;
        }
        if (STATE.mode === 'freestyle') drawCustomCanvas();
      });
      const fr = new FileReader();
      fr.onload = (ev) => {
        const im = new Image();
        im.onload = () => {
          STATE.origImg = im;
          STATE.free.ox = 0;
          STATE.free.oy = 0;
          if (STATE.mode !== 'frame') drawCustomCanvas();
        };
        im.src = ev.target.result;
      };
      fr.readAsDataURL(f);
    }
  }, true);

  // =========================================================
  // 3. LIGHTROOM SAVING HUD
  // =========================================================
  let hudTimer = null, hudInterval = null, hudSafety = null;

  function ensureHud() {
    let hud = document.getElementById('lr-hud');
    if (!hud && document.body) {
      hud = document.createElement('div');
      hud.id = 'lr-hud';
      hud.innerHTML =
        '<div id="lr-box">' +
        '<div class="lr-rw"><div id="lr-sp"></div><div id="lr-ok">✓</div><div id="lr-er">!</div></div>' +
        '<div id="lr-tt">Menyimpan...</div>' +
        '<div class="lr-tr"><div id="lr-fl"></div></div>' +
        '</div>';
      document.body.appendChild(hud);
    }
    return hud;
  }

  function showSavingHud(text) {
    const hud = ensureHud();
    if (!hud) return;
    clearTimeout(hudTimer); clearTimeout(hudSafety); clearInterval(hudInterval);
    const sp = document.getElementById('lr-sp');
    const ok = document.getElementById('lr-ok');
    const er = document.getElementById('lr-er');
    const tt = document.getElementById('lr-tt');
    const fl = document.getElementById('lr-fl');
    if (sp) sp.style.display = 'block';
    if (ok) ok.style.display = 'none';
    if (er) er.style.display = 'none';
    if (tt) tt.textContent = text || 'Menyimpan...';
    if (fl) {
      fl.style.background = 'linear-gradient(90deg, #0284c7, #38bdf8)';
      fl.style.width = '32%';
    }
    hud.classList.add('on');
    let pct = 32;
    hudInterval = setInterval(() => {
      if (pct < 88) {
        pct += (90 - pct) * 0.16;
        if (fl) fl.style.width = pct.toFixed(1) + '%';
      }
    }, 140);
    hudSafety = setTimeout(() => {
      clearInterval(hudInterval);
      hud.classList.remove('on');
    }, 3500);
  }

  window.__showLightroomSavingHud = showSavingHud;

  window.__onGallerySaveProgress = function (id, cur, tot) {
    clearTimeout(hudSafety); clearInterval(hudInterval);
    const tt = document.getElementById('lr-tt');
    const fl = document.getElementById('lr-fl');
    if (tt) tt.textContent = 'Menyimpan ' + cur + '/' + tot + '...';
    if (fl) fl.style.width = Math.min(95, Math.round((cur / tot) * 92)) + '%';
  };

  window.__onGallerySaveFinished = function (id, success) {
    const hud = ensureHud();
    if (!hud) return;
    clearTimeout(hudSafety); clearInterval(hudInterval); clearTimeout(hudTimer);
    hud.classList.add('on');
    const sp = document.getElementById('lr-sp');
    const ok = document.getElementById('lr-ok');
    const er = document.getElementById('lr-er');
    const tt = document.getElementById('lr-tt');
    const fl = document.getElementById('lr-fl');
    if (sp) sp.style.display = 'none';
    if (success) {
      if (ok) ok.style.display = 'flex';
      if (er) er.style.display = 'none';
      if (tt) tt.textContent = 'Tersimpan';
      if (fl) { fl.style.background = '#10b981'; fl.style.width = '100%'; }
    } else {
      if (ok) ok.style.display = 'none';
      if (er) er.style.display = 'flex';
      if (tt) tt.textContent = 'Gagal Menyimpan';
      if (fl) { fl.style.background = '#ef4444'; fl.style.width = '100%'; }
    }
    hudTimer = setTimeout(() => hud.classList.remove('on'), success ? 1100 : 1800);
  };

  if (window.AndroidGallery && typeof window.AndroidGallery.saveBase64Image === 'function') {
    const origSave = window.AndroidGallery.saveBase64Image.bind(window.AndroidGallery);
    window.AndroidGallery.saveBase64Image = function (dataUrl, fileName) {
      const id = 's_' + Date.now();
      showSavingHud('Menyimpan...');
      if (window.AndroidGallery.saveBase64ImageWithId) {
        window.AndroidGallery.saveBase64ImageWithId(dataUrl, fileName || '', id);
      } else {
        origSave(dataUrl, fileName || '');
        setTimeout(() => window.__onGallerySaveFinished(id, true), 650);
      }
    };
  }

  // =========================================================
  // 4. CANVAS HELPERS & CUSTOM RENDERER
  // =========================================================
  function getNativeCanvas() {
    const canvases = Array.from(document.querySelectorAll('canvas')).filter(
      (c) =>
        c.id !== 'ig-pc' &&
        c.id !== 'efs-ccanvas' &&
        c.width >= 100 &&
        c.height >= 100
    );
    if (canvases.length) {
      canvases.sort((a, b) => b.width * b.height - a.width * a.height);
      return canvases[0];
    }
    const imgs = Array.from(document.querySelectorAll('img')).filter(
      (i) => i.id !== 'efs-pvi' && i.complete && i.naturalWidth >= 120
    );
    if (imgs.length) {
      imgs.sort((a, b) => b.naturalWidth * b.naturalHeight - a.naturalWidth * a.naturalHeight);
      const off = document.createElement('canvas');
      off.width = imgs[0].naturalWidth;
      off.height = imgs[0].naturalHeight;
      off.getContext('2d').drawImage(imgs[0], 0, 0);
      return off;
    }
    return null;
  }

  function getOriginalImage() {
    if (STATE.origImg && STATE.origImg.complete && STATE.origImg.naturalWidth > 0) return STATE.origImg;
    const imgs = Array.from(document.querySelectorAll('img')).filter(
      (i) => i.id !== 'efs-pvi' && i.complete && i.naturalWidth >= 120
    );
    if (imgs.length) {
      imgs.sort((a, b) => b.naturalWidth * b.naturalHeight - a.naturalWidth * a.naturalHeight);
      STATE.origImg = imgs[0];
      return imgs[0];
    }
    return null;
  }

  function getExifFallback() {
    if (STATE.exifText) return STATE.exifText;
    const els = Array.from(document.querySelectorAll('input,span,div,p')).filter(
      (el) => !el.closest('#efs-bar,#efs-fpanel,#ig-mod')
    );
    for (const el of els) {
      const t = (el.value || el.innerText || '').trim();
      if (t.length > 4 && t.length < 90 && /\b(iso|f\/|mm|1\/\d+|nikon|canon|sony|fuji|leica|apple|iphone|samsung|xiaomi)\b/i.test(t)) {
        STATE.exifText = t;
        return t;
      }
    }
    return '';
  }

  function drawCustomCanvas() {
    const cc = document.getElementById('efs-ccanvas');
    if (!cc) return null;
    const im = getOriginalImage();
    if (!im) {
      const nc = getNativeCanvas();
      if (nc) { cc.width = nc.width; cc.height = nc.height; cc.getContext('2d').drawImage(nc, 0, 0); return cc; }
      return null;
    }
    const iw = im.naturalWidth, ih = im.naturalHeight;
    if (STATE.mode === 'noframe') {
      cc.width = iw; cc.height = ih;
      const ctx = cc.getContext('2d');
      ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(im, 0, 0, iw, ih);
      return cc;
    }
    const f = STATE.free, baseDim = Math.max(iw, ih), pad = Math.round(baseDim * (f.pad / 100));
    const bot = (f.wm === 'bottom-center' || f.wm === 'bottom-left') ? Math.max(pad, Math.round(baseDim * 0.065)) : pad;
    const cw = iw + pad * 2, ch = ih + pad + bot;
    cc.width = cw; cc.height = ch;
    const ctx = cc.getContext('2d');
    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';

    if (f.blur) {
      ctx.save();
      ctx.filter = 'blur(' + Math.max(24, Math.round(baseDim * 0.025)) + 'px) brightness(0.75)';
      const sc = Math.max(cw / iw, ch / ih) * 1.15;
      ctx.drawImage(im, (cw - iw * sc) / 2, (ch - ih * sc) / 2, iw * sc, ih * sc);
      ctx.restore();
    } else {
      ctx.fillStyle = f.bg;
      ctx.fillRect(0, 0, cw, ch);
    }

    const rad = Math.round((f.rad / 40) * (Math.min(iw, ih) * 0.08));
    ctx.save();
    if (pad > 0) {
      ctx.shadowColor = 'rgba(0,0,0,0.36)';
      ctx.shadowBlur = Math.round(baseDim * 0.025);
      ctx.shadowOffsetY = Math.round(baseDim * 0.01);
    }
    if (rad > 0) {
      ctx.beginPath();
      ctx.moveTo(pad + rad, pad); ctx.lineTo(pad + iw - rad, pad); ctx.quadraticCurveTo(pad + iw, pad, pad + iw, pad + rad);
      ctx.lineTo(pad + iw, pad + ih - rad); ctx.quadraticCurveTo(pad + iw, pad + ih, pad + iw - rad, pad + ih);
      ctx.lineTo(pad + rad, pad + ih); ctx.quadraticCurveTo(pad, pad + ih, pad, pad + ih - rad);
      ctx.lineTo(pad, pad + rad); ctx.quadraticCurveTo(pad, pad, pad + rad, pad);
      ctx.closePath();
      ctx.fillStyle = '#000'; ctx.fill(); ctx.shadowColor = 'transparent'; ctx.clip();
    }
    ctx.drawImage(im, pad, pad, iw, ih);
    ctx.restore();

    const wt = (f.txt || getExifFallback() || '').trim();
    if (f.wm !== 'none' && wt) {
      const fs = Math.max(20, Math.round(baseDim * 0.021));
      ctx.save();
      ctx.font = '600 ' + fs + 'px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif';
      ctx.textBaseline = 'middle';
      const isDark = !f.blur && (f.bg === '#09090b' || f.bg === '#18181b' || f.bg === '#000000');
      let col = isDark ? '#f4f4f5' : '#18181b';
      if (f.blur || f.wm === 'inside-bottom') {
        col = '#ffffff';
        ctx.shadowColor = 'rgba(0,0,0,0.72)';
        ctx.shadowBlur = Math.round(fs * 0.45);
      }
      ctx.fillStyle = col;
      let bx = cw / 2, by = pad + ih + bot / 2;
      if (f.wm === 'bottom-left') { ctx.textAlign = 'left'; bx = pad + Math.max(12, Math.round(fs * 0.5)); }
      else if (f.wm === 'inside-bottom') { ctx.textAlign = 'center'; by = pad + ih - Math.round(fs * 1.6); }
      else ctx.textAlign = 'center';
      ctx.fillText(wt, bx + f.ox, by + f.oy);
      ctx.restore();
    }
    return cc;
  }

  function getOutputCanvas() {
    return STATE.mode === 'frame' ? (getNativeCanvas() || drawCustomCanvas()) : (drawCustomCanvas() || getNativeCanvas());
  }

  // =========================================================
  // 5. FULLSCREEN PREVIEW WITH 2-FINGER PINCH-TO-ZOOM
  // =========================================================
  function applyPreviewTransform() {
    const img = document.getElementById('efs-pvi');
    if (img) {
      img.style.transform = 'translate3d(' + STATE.pv.x + 'px, ' + STATE.pv.y + 'px, 0) scale(' + STATE.pv.s + ')';
    }
    const badge = document.getElementById('efs-pvz');
    if (badge) {
      badge.textContent = STATE.pv.s.toFixed(1) + 'x';
    }
  }

  function resetPreviewTransform() {
    STATE.pv.s = 1;
    STATE.pv.x = 0;
    STATE.pv.y = 0;
    applyPreviewTransform();
  }

  function closePreview() {
    if (!STATE.pv.open) return false;
    STATE.pv.open = false;
    const pv = document.getElementById('efs-pv');
    if (pv) pv.classList.remove('on');
    resetPreviewTransform();
    return true;
  }

  function ensurePreviewModal() {
    let pv = document.getElementById('efs-pv');
    if (!pv && document.body) {
      pv = document.createElement('div');
      pv.id = 'efs-pv';
      pv.innerHTML =
        '<div id="efs-pvz">1.0x</div>' +
        '<button type="button" id="efs-pvx">✕ Tutup</button>' +
        '<div id="efs-pvs"><img id="efs-pvi" alt="Preview" /></div>' +
        '<div id="efs-pvhint">Cubit 2 jari untuk zoom • Ketuk 2x</div>';
      document.body.appendChild(pv);

      document.getElementById('efs-pvx').addEventListener('click', closePreview);

      const stage = document.getElementById('efs-pvs');
      let pinching = false, panning = false;
      let startDist = 1, startScale = 1, startPanX = 0, startPanY = 0;
      let startMidX = 0, startMidY = 0, panStartX = 0, panStartY = 0;
      let lastTapTime = 0, lastTapX = 0, lastTapY = 0;

      const getDist = (t1, t2) => Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY) || 1;
      const getCenterOffset = (cx, cy) => {
        const r = stage.getBoundingClientRect();
        return { x: cx - (r.left + r.width / 2), y: cy - (r.top + r.height / 2) };
      };
      const clampPan = () => {
        const maxX = window.innerWidth * (STATE.pv.s - 1) * 0.6;
        const maxY = window.innerHeight * (STATE.pv.s - 1) * 0.6;
        STATE.pv.x = Math.max(-maxX, Math.min(maxX, STATE.pv.x));
        STATE.pv.y = Math.max(-maxY, Math.min(maxY, STATE.pv.y));
      };

      stage.addEventListener('touchstart', (e) => {
        if (!STATE.pv.open) return;
        if (e.cancelable) e.preventDefault();
        if (e.touches.length === 2) {
          pinching = true;
          panning = false;
          startDist = getDist(e.touches[0], e.touches[1]);
          startScale = STATE.pv.s;
          startPanX = STATE.pv.x;
          startPanY = STATE.pv.y;
          const mid = getCenterOffset(
            (e.touches[0].clientX + e.touches[1].clientX) / 2,
            (e.touches[0].clientY + e.touches[1].clientY) / 2
          );
          startMidX = mid.x;
          startMidY = mid.y;
        } else if (e.touches.length === 1) {
          const now = Date.now(), tx = e.touches[0].clientX, ty = e.touches[0].clientY;
          if (now - lastTapTime < 280 && Math.hypot(tx - lastTapX, ty - lastTapY) < 32) {
            if (STATE.pv.s > 1.05) {
              resetPreviewTransform();
            } else {
              const p = getCenterOffset(tx, ty);
              STATE.pv.s = 2.5;
              STATE.pv.x = -p.x * 1.2;
              STATE.pv.y = -p.y * 1.2;
              clampPan();
              applyPreviewTransform();
            }
            lastTapTime = 0;
            return;
          }
          lastTapTime = now; lastTapX = tx; lastTapY = ty;
          if (STATE.pv.s > 1.01) {
            panning = true;
            panStartX = tx - STATE.pv.x;
            panStartY = ty - STATE.pv.y;
          }
        }
      }, { passive: false });

      stage.addEventListener('touchmove', (e) => {
        if (!STATE.pv.open) return;
        if (e.cancelable) e.preventDefault();
        if (pinching && e.touches.length === 2) {
          const curDist = getDist(e.touches[0], e.touches[1]);
          const nextScale = Math.max(1, Math.min(5, startScale * (curDist / startDist)));
          const curMid = getCenterOffset(
            (e.touches[0].clientX + e.touches[1].clientX) / 2,
            (e.touches[0].clientY + e.touches[1].clientY) / 2
          );
          const ratio = nextScale / startScale;
          STATE.pv.s = nextScale;
          STATE.pv.x = curMid.x - (startMidX - startPanX) * ratio;
          STATE.pv.y = curMid.y - (startMidY - startPanY) * ratio;
          if (nextScale <= 1.02) {
            STATE.pv.x = 0;
            STATE.pv.y = 0;
          } else {
            clampPan();
          }
          applyPreviewTransform();
        } else if (panning && e.touches.length === 1 && STATE.pv.s > 1.01) {
          STATE.pv.x = e.touches[0].clientX - panStartX;
          STATE.pv.y = e.touches[0].clientY - panStartY;
          clampPan();
          applyPreviewTransform();
        }
      }, { passive: false });

      stage.addEventListener('touchend', (e) => {
        if (e.touches.length < 2) pinching = false;
        if (e.touches.length === 0) {
          panning = false;
          if (STATE.pv.s <= 1.03) resetPreviewTransform();
        }
      });
    }
    return pv;
  }

  function openPreview() {
    const c = getOutputCanvas();
    if (!c) return;
    const pv = ensurePreviewModal();
    if (!pv) return;
    const img = document.getElementById('efs-pvi');
    resetPreviewTransform();
    img.onload = resetPreviewTransform;
    img.src = c.toDataURL('image/jpeg', 0.95);
    STATE.pv.open = true;
    pv.classList.add('on');
  }

  window.__handleAndroidBack = function () {
    if (STATE.pv.open) return closePreview();
    const m = document.getElementById('ig-mod');
    if (m && m.classList.contains('on')) { m.classList.remove('on'); return true; }
    return false;
  };

  // =========================================================
  // 6. NATIVE SHARE & SAVE INTERCEPTORS
  // =========================================================
  const blobToDataUrl = (blob) => new Promise((resolve, reject) => {
    const r = new FileReader(); r.onloadend = () => resolve(r.result); r.onerror = reject; r.readAsDataURL(blob);
  });

  navigator.canShare = () => true;
  navigator.share = async function (data) {
    if (!window.AndroidShare) return;
    if (STATE.mode !== 'frame') {
      const c = getOutputCanvas();
      if (c) {
        window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg');
        return;
      }
    }
    if (data && Array.isArray(data.files) && data.files.length > 0) {
      if (data.files.length === 1) {
        const u = await blobToDataUrl(data.files[0]);
        window.AndroidShare.shareBase64Image(u, data.files[0].name || 'EXIF_' + Date.now() + '.jpg');
      } else {
        const arr = [];
        for (let i = 0; i < data.files.length; i++) {
          arr.push({ dataUrl: await blobToDataUrl(data.files[i]), fileName: data.files[i].name || 'EXIF_' + (i + 1) + '.jpg' });
        }
        window.AndroidShare.shareMultipleBase64Images(JSON.stringify(arr));
      }
      return;
    }
    if (data && typeof data.url === 'string' && data.url.startsWith('data:image/')) {
      window.AndroidShare.shareBase64Image(data.url, 'EXIF_' + Date.now() + '.jpg');
      return;
    }
    const c = getOutputCanvas();
    if (c) window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg');
  };

  const getBtnLabel = (b) => (
    (b.innerText || '') + ' ' +
    (b.getAttribute('aria-label') || '') + ' ' +
    (b.getAttribute('title') || '') + ' ' +
    (b.id || '') + ' ' +
    (typeof b.className === 'string' ? b.className : '')
  ).toLowerCase();

  ['touchstart', 'pointerdown'].forEach((evName) => {
    document.addEventListener(evName, (e) => {
      const b = e.target.closest('button,a,[role="button"]');
      if (!b || b.closest('#efs-bar,#efs-fpanel,#efs-pv')) return;
      if (/\b(simpan|save|download|unduh|export)\b/.test(getBtnLabel(b))) showSavingHud('Menyimpan...');
    }, true);
  });

  document.addEventListener('click', (e) => {
    const b = e.target.closest('button,a,[role="button"]');
    if (!b || b.closest('#ig-mod,#efs-bar,#efs-fpanel,#efs-pv')) return;
    const lbl = getBtnLabel(b);

    if (/\b(simpan|save|download|unduh|export)\b/.test(lbl)) {
      showSavingHud('Menyimpan...');
      if (STATE.mode !== 'frame') {
        e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
        const c = getOutputCanvas(), id = 'c_' + Date.now();
        if (c && window.AndroidGallery && window.AndroidGallery.saveBase64ImageWithId) {
          window.AndroidGallery.saveBase64ImageWithId(c.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg', id);
        } else {
          window.__onGallerySaveFinished(id, false);
        }
      }
      return;
    }

    if (/\b(share|bagikan)\b/.test(lbl)) {
      if (STATE.mode !== 'frame') {
        e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation();
        const c = getOutputCanvas();
        if (c && window.AndroidShare) {
          window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg');
        }
        return;
      }
      const href = b.getAttribute('href') || '';
      if (href.startsWith('data:image/')) {
        e.preventDefault(); e.stopPropagation();
        if (window.AndroidShare) window.AndroidShare.shareBase64Image(href, 'EXIF_' + Date.now() + '.jpg');
        return;
      }
      let shareCalled = false;
      const origShare = navigator.share;
      navigator.share = async function (...args) {
        shareCalled = true;
        return origShare.apply(this, args);
      };
      setTimeout(() => {
        navigator.share = origShare;
        if (!shareCalled && window.AndroidShare) {
          const c = getOutputCanvas();
          if (c) window.AndroidShare.shareBase64Image(c.toDataURL('image/jpeg', 0.95), 'EXIF_' + Date.now() + '.jpg');
        }
      }, 600);
    }
  }, true);

  // =========================================================
  // 7. SPLIT IG CAROUSEL MODAL
  // =========================================================
  let splitSourceCanvas = null;

  function buildPanorama(src, n, ar) {
    const sw = src.width, sh = src.height;
    if (ar === 'original') {
      const o = document.createElement('canvas'); o.width = sw; o.height = sh; o.getContext('2d').drawImage(src, 0, 0); return o;
    }
    const targetRatio = (ar === '4:5' ? 0.8 : 1.0) * n;
    let pw, ph;
    if (sw / sh >= targetRatio) { pw = sw; ph = Math.round(sw / targetRatio); }
    else { ph = sh; pw = Math.round(sh * targetRatio); }
    const o = document.createElement('canvas'); o.width = pw; o.height = ph;
    const ctx = o.getContext('2d');
    let bg = '#ffffff';
    try { const d = src.getContext('2d').getImageData(2, 2, 1, 1).data; bg = 'rgb(' + d[0] + ',' + d[1] + ',' + d[2] + ')'; } catch (_) {}
    ctx.fillStyle = bg; ctx.fillRect(0, 0, pw, ph);
    const k = Math.min(pw / sw, ph / sh), dw = Math.round(sw * k), dh = Math.round(sh * k);
    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(src, Math.round((pw - dw) / 2), Math.round((ph - dh) / 2), dw, dh);
    return o;
  }

  function renderSplitPreview() {
    if (!splitSourceCanvas) return;
    const n = STATE.split.n, pano = buildPanorama(splitSourceCanvas, n, STATE.split.ar);
    const pc = document.getElementById('ig-pc');
    if (!pc) return;
    pc.width = pano.width; pc.height = pano.height; pc.getContext('2d').drawImage(pano, 0, 0);
    const ov = document.getElementById('ig-ov'); ov.innerHTML = '';
    for (let i = 1; i <= n; i++) {
      const cell = document.createElement('div'); cell.className = 'ig-cl';
      cell.innerHTML = '<span class="ig-bg">' + i + '/' + n + '</span>';
      ov.appendChild(cell);
    }
    document.getElementById('ig-dm').textContent = n + ' Slide • ' + Math.floor(pano.width / n) + ' × ' + pano.height + ' px';
    document.getElementById('ig-svb').textContent = 'Simpan (' + n + ')';
    document.getElementById('ig-shb').textContent = 'Share (' + n + ')';
  }

  function sliceSplitImages() {
    if (!splitSourceCanvas) return [];
    const n = STATE.split.n, pano = buildPanorama(splitSourceCanvas, n, STATE.split.ar);
    const tw = pano.width, th = pano.height, bw = Math.floor(tw / n), stamp = Date.now(), out = [];
    for (let i = 0; i < n; i++) {
      const sx = i * bw, sw = i === n - 1 ? tw - sx : bw;
      const c = document.createElement('canvas'); c.width = sw; c.height = th;
      const ctx = c.getContext('2d'); ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(pano, sx, 0, sw, th, 0, 0, sw, th);
      out.push({ dataUrl: c.toDataURL('image/jpeg', 0.95), fileName: 'EXIF_IG_' + (i + 1) + 'of' + n + '_' + stamp + '.jpg' });
    }
    return out;
  }

  function ensureSplitModal() {
    let mod = document.getElementById('ig-mod');
    if (!mod && document.body) {
      mod = document.createElement('div'); mod.id = 'ig-mod';
      mod.innerHTML =
        '<div class="ig-sh">' +
        '<div class="ig-hd"><b style="font-size:16px">Split IG Carousel</b><button type="button" class="ig-xb" id="ig-x">✕</button></div>' +
        '<div class="ig-pb"><div class="ig-ps"><canvas id="ig-pc"></canvas><div id="ig-ov"></div></div><div id="ig-dm"></div></div>' +
        '<div class="ig-sr" id="ig-cg"><button type="button" class="ig-sb" data-n="2">2 Slide</button><button type="button" class="ig-sb on" data-n="3">3 Slide</button><button type="button" class="ig-sb" data-n="4">4 Slide</button></div>' +
        '<div class="ig-sr" id="ig-ag"><button type="button" class="ig-sb on" data-a="original">Asli</button><button type="button" class="ig-sb" data-a="4:5">4:5</button><button type="button" class="ig-sb" data-a="1:1">1:1</button></div>' +
        '<div class="ig-ac"><button type="button" class="ig-bs" id="ig-shb">Share</button><button type="button" class="ig-bp" id="ig-svb">Simpan</button></div>' +
        '</div>';
      document.body.appendChild(mod);
      document.getElementById('ig-x').addEventListener('click', () => mod.classList.remove('on'));
      mod.addEventListener('click', (e) => { if (e.target === mod) mod.classList.remove('on'); });
      document.querySelectorAll('#ig-cg .ig-sb').forEach((btn) => {
        btn.addEventListener('click', () => {
          document.querySelectorAll('#ig-cg .ig-sb').forEach((x) => x.classList.remove('on')); btn.classList.add('on');
          STATE.split.n = parseInt(btn.getAttribute('data-n'), 10) || 3; renderSplitPreview();
        });
      });
      document.querySelectorAll('#ig-ag .ig-sb').forEach((btn) => {
        btn.addEventListener('click', () => {
          document.querySelectorAll('#ig-ag .ig-sb').forEach((x) => x.classList.remove('on')); btn.classList.add('on');
          STATE.split.ar = btn.getAttribute('data-a') || 'original'; renderSplitPreview();
        });
      });
      ['touchstart', 'pointerdown'].forEach((evName) => {
        document.getElementById('ig-svb').addEventListener(evName, () => showSavingHud('Menyimpan...'));
      });
      document.getElementById('ig-svb').addEventListener('click', () => {
        mod.classList.remove('on'); showSavingHud('Menyimpan...');
        const slices = sliceSplitImages(), id = 'sp_' + Date.now();
        if (slices.length && window.AndroidGallery && window.AndroidGallery.saveBatchBase64Images) {
          window.AndroidGallery.saveBatchBase64Images(JSON.stringify(slices), id);
        } else window.__onGallerySaveFinished(id, false);
      });
      document.getElementById('ig-shb').addEventListener('click', () => {
        const slices = sliceSplitImages(); if (!slices.length) return;
        mod.classList.remove('on');
        if (window.AndroidShare && window.AndroidShare.shareMultipleBase64Images) {
          window.AndroidShare.shareMultipleBase64Images(JSON.stringify(slices));
        }
      });
    }
    return mod;
  }

  // =========================================================
  // 8. TOP BAR, MODE SWITCHER & SPA WATCHDOG
  // =========================================================
  function applyMode(m) {
    STATE.mode = m;
    const bar = document.getElementById('efs-bar');
    if (bar) bar.querySelectorAll('.efs-mbtn').forEach((b) => b.classList.toggle('on', b.getAttribute('data-m') === m));
    const cwrap = document.getElementById('efs-cwrap'), fpanel = document.getElementById('efs-fpanel');
    const nativeCanvases = Array.from(document.querySelectorAll('canvas')).filter((c) => c.id !== 'ig-pc' && c.id !== 'efs-ccanvas');
    if (m === 'frame') {
      if (cwrap) cwrap.classList.remove('on');
      if (fpanel) fpanel.classList.remove('on');
      nativeCanvases.forEach((c) => (c.style.display = ''));
    } else {
      nativeCanvases.forEach((c) => (c.style.display = 'none'));
      if (cwrap) cwrap.classList.add('on');
      if (fpanel) fpanel.classList.toggle('on', m === 'freestyle');
      const ti = document.getElementById('efs-wt');
      if (ti && !ti.value) { ti.value = STATE.free.txt || getExifFallback() || ''; STATE.free.txt = ti.value; }
      drawCustomCanvas();
    }
  }

  function ensureTopBarAndPanels() {
    if (!document.body) return;
    injectStyles(); ensureHud(); ensurePreviewModal(); ensureSplitModal();

    if (!document.getElementById('efs-bar')) {
      const bar = document.createElement('div'); bar.id = 'efs-bar';
      bar.innerHTML =
        '<div class="efs-grp">' +
        '<button type="button" class="efs-mbtn' + (STATE.mode === 'frame' ? ' on' : '') + '" data-m="frame">Bingkai</button>' +
        '<button type="button" class="efs-mbtn' + (STATE.mode === 'noframe' ? ' on' : '') + '" data-m="noframe">Tanpa Frame</button>' +
        '<button type="button" class="efs-mbtn' + (STATE.mode === 'freestyle' ? ' on' : '') + '" data-m="freestyle">Gaya Bebas</button>' +
        '</div>' +
        '<button type="button" id="efs-spbtn">Split IG</button>';
      document.body.appendChild(bar);
      bar.querySelectorAll('.efs-mbtn').forEach((btn) => btn.addEventListener('click', () => applyMode(btn.getAttribute('data-m'))));
      document.getElementById('efs-spbtn').addEventListener('click', () => {
        const c = getOutputCanvas();
        if (!c) {
          showSavingHud('Pilih foto dulu');
          setTimeout(() => { const h = document.getElementById('lr-hud'); if (h) h.classList.remove('on'); }, 1100);
          return;
        }
        splitSourceCanvas = c; renderSplitPreview();
        const mod = ensureSplitModal(); if (mod) mod.classList.add('on');
      });
    }

    if (!document.getElementById('efs-cwrap')) {
      const cwrap = document.createElement('div'); cwrap.id = 'efs-cwrap'; cwrap.innerHTML = '<canvas id="efs-ccanvas"></canvas>';
      const fpanel = document.createElement('div'); fpanel.id = 'efs-fpanel';
      fpanel.innerHTML =
        '<div class="efs-row"><div class="efs-hd"><span>Latar</span></div><div class="efs-sws" id="efs-sws"><button type="button" class="efs-sw on" data-b="#ffffff" style="background:#fff;color:#000"></button><button type="button" class="efs-sw" data-b="#09090b" style="background:#09090b"></button><button type="button" class="efs-sw" data-b="#27272a" style="background:#27272a"></button><button type="button" class="efs-sw" data-b="#f5f5f4" style="background:#f5f5f4"></button><button type="button" class="efs-sw" data-b="blur" style="background:linear-gradient(135deg,#38bdf8,#f59e0b)">Blur</button><input type="color" id="efs-cp" value="#ffffff" style="width:38px;height:38px;border:none;background:none;padding:0" /></div></div>' +
        '<div class="efs-row"><div class="efs-hd"><span>Jarak Tepi</span><span id="efs-pv2">8%</span></div><input type="range" class="efs-sl" id="efs-ps" min="0" max="24" value="8" /></div>' +
        '<div class="efs-row"><div class="efs-hd"><span>Sudut Foto</span><span id="efs-rv">0</span></div><input type="range" class="efs-sl" id="efs-rs" min="0" max="40" value="0" /></div>' +
        '<div class="efs-row"><div class="efs-hd"><span>Tata Letak Watermark</span></div><div class="efs-wmg" id="efs-wmg"><button type="button" class="efs-wmb on" data-w="bottom-center"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="18" x2="16" y2="18"/></svg><span>Bawah Tengah</span></button><button type="button" class="efs-wmb" data-w="bottom-left"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="6" y1="18" x2="13" y2="18"/></svg><span>Bawah Kiri</span></button><button type="button" class="efs-wmb" data-w="inside-bottom"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="8" y1="15" x2="16" y2="15"/></svg><span>Dalam Foto</span></button><button type="button" class="efs-wmb" data-w="none"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="5" y1="5" x2="19" y2="19"/></svg><span>Sembunyi</span></button></div></div>' +
        '<div class="efs-row"><input type="text" class="efs-inp" id="efs-wt" placeholder="Teks EXIF (geser langsung di foto)" /></div>';
      const nc = Array.from(document.querySelectorAll('canvas')).find((c) => c.id !== 'ig-pc' && c.id !== 'efs-ccanvas');
      if (nc && nc.parentElement) { nc.parentElement.insertBefore(cwrap, nc); nc.parentElement.insertBefore(fpanel, nc.nextSibling); }
      else { document.body.insertBefore(cwrap, document.body.firstChild); document.body.insertBefore(fpanel, cwrap.nextSibling); }

      document.querySelectorAll('#efs-sws .efs-sw').forEach((sw) => {
        sw.addEventListener('click', () => {
          document.querySelectorAll('#efs-sws .efs-sw').forEach((x) => x.classList.remove('on')); sw.classList.add('on');
          const v = sw.getAttribute('data-b');
          if (v === 'blur') STATE.free.blur = true; else { STATE.free.blur = false; STATE.free.bg = v; }
          drawCustomCanvas();
        });
      });
      document.getElementById('efs-cp').addEventListener('input', (e) => {
        document.querySelectorAll('#efs-sws .efs-sw').forEach((x) => x.classList.remove('on'));
        STATE.free.blur = false; STATE.free.bg = e.target.value; drawCustomCanvas();
      });
      document.getElementById('efs-ps').addEventListener('input', (e) => {
        STATE.free.pad = parseInt(e.target.value, 10) || 0; document.getElementById('efs-pv2').textContent = STATE.free.pad + '%'; drawCustomCanvas();
      });
      document.getElementById('efs-rs').addEventListener('input', (e) => {
        STATE.free.rad = parseInt(e.target.value, 10) || 0; document.getElementById('efs-rv').textContent = STATE.free.rad; drawCustomCanvas();
      });
      document.querySelectorAll('#efs-wmg .efs-wmb').forEach((btn) => {
        btn.addEventListener('click', () => {
          document.querySelectorAll('#efs-wmg .efs-wmb').forEach((x) => x.classList.remove('on')); btn.classList.add('on');
          STATE.free.wm = btn.getAttribute('data-w'); STATE.free.ox = 0; STATE.free.oy = 0; drawCustomCanvas();
        });
      });
      document.getElementById('efs-wt').addEventListener('input', (e) => { STATE.free.txt = e.target.value; drawCustomCanvas(); });
    }

    document.querySelectorAll('button,[role="button"]').forEach((b) => {
      if (b.closest('#efs-bar,#efs-fpanel,#ig-mod,#efs-pv')) return;
      const pt = b.parentElement && b.parentElement.parentElement ? (b.parentElement.parentElement.innerText || '').toLowerCase() : '';
      const bt = (b.innerText || b.getAttribute('aria-label') || '').toLowerCase();
      if (/\b(watermark|tata letak|layout|posisi|align|logo|teks)\b/.test(pt) || /\b(kiri|tengah|kanan|bawah|atas|minimal|klasik|modern|leica|fuji|sony)\b/.test(bt)) {
        b.classList.add('efs-big-wm');
      }
    });
  }

  function isPointInsidePhoto(cx, cy) {
    const candidates = Array.from(document.querySelectorAll('canvas,img')).filter(
      (el) => el.id !== 'ig-pc' && el.id !== 'efs-pvi' && el.offsetParent !== null
    );
    for (const el of candidates) {
      const r = el.getBoundingClientRect();
      if (r.width >= 100 && r.height >= 100 && cx >= r.left && cx <= r.right && cy >= r.top && cy <= r.bottom) {
        return true;
      }
    }
    return false;
  }

  let tapOnPhoto = false, tapStartX = 0, tapStartY = 0, tapStartTime = 0, draggingWm = false, dragLastX = 0, dragLastY = 0;

  document.addEventListener('pointerdown', (e) => {
    if (e.target.closest('button,a,input,select,textarea,label,[role="button"],#efs-bar,#efs-fpanel,#ig-mod,#efs-pv,#lr-hud')) {
      tapOnPhoto = false; return;
    }
    const target = e.target.closest('canvas,img');
    if (target && target.id === 'efs-ccanvas' && STATE.mode === 'freestyle' && STATE.free.wm !== 'none') {
      draggingWm = true; dragLastX = e.clientX; dragLastY = e.clientY;
    }
    if ((target && target.id !== 'ig-pc' && target.id !== 'efs-pvi') || isPointInsidePhoto(e.clientX, e.clientY)) {
      tapOnPhoto = true; tapStartX = e.clientX; tapStartY = e.clientY; tapStartTime = Date.now();
    } else {
      tapOnPhoto = false;
    }
  }, true);

  document.addEventListener('pointermove', (e) => {
    if (!draggingWm) return;
    const cc = document.getElementById('efs-ccanvas');
    if (!cc) return;
    const rect = cc.getBoundingClientRect();
    if (rect.width <= 0) return;
    const scale = cc.width / rect.width;
    STATE.free.ox += (e.clientX - dragLastX) * scale;
    STATE.free.oy += (e.clientY - dragLastY) * scale;
    dragLastX = e.clientX; dragLastY = e.clientY;
    drawCustomCanvas();
  }, true);

  document.addEventListener('pointerup', (e) => {
    draggingWm = false;
    if (tapOnPhoto && Math.hypot(e.clientX - tapStartX, e.clientY - tapStartY) < 12 && Date.now() - tapStartTime < 400) {
      tapOnPhoto = false;
      openPreview();
    }
    tapOnPhoto = false;
  }, true);

  function boot() {
    ensureTopBarAndPanels();
    setInterval(ensureTopBarAndPanels, 1000);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
