(function () {
  if (window.__studioAddonsInitialized) return;
  window.__studioAddonsInitialized = true;

  // =========================================================================
  // 1. LIGHTROOM-STYLE SAVING HUD OVERLAY
  // =========================================================================
  const style = document.createElement('style');
  style.textContent = `
    #lr-save-backdrop {
      position: fixed;
      inset: 0;
      z-index: 2147483646;
      background: rgba(9, 9, 11, 0.62);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      display: none;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 0.22s ease;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    #lr-save-backdrop.visible {
      display: flex;
      opacity: 1;
    }
    #lr-save-card {
      width: min(310px, 84vw);
      background: rgba(24, 24, 27, 0.96);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 18px;
      padding: 22px 20px 20px;
      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.55);
      color: #fafafa;
      text-align: center;
    }
    .lr-ring-wrap {
      width: 48px;
      height: 48px;
      margin: 0 auto 14px;
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .lr-spinner {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      border: 3px solid rgba(255, 255, 255, 0.14);
      border-top-color: #38bdf8;
      animation: lrSpin 0.85s linear infinite;
    }
    .lr-check {
      display: none;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: rgba(16, 185, 129, 0.18);
      border: 2px solid #10b981;
      color: #10b981;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      font-weight: 700;
    }
    .lr-error-icon {
      display: none;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: rgba(239, 68, 68, 0.18);
      border: 2px solid #ef4444;
      color: #ef4444;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 700;
    }
    #lr-save-title {
      font-size: 15px;
      font-weight: 600;
      letter-spacing: 0.01em;
      margin-bottom: 4px;
      color: #ffffff;
    }
    #lr-save-sub {
      font-size: 12px;
      color: #a1a1aa;
      margin-bottom: 14px;
      word-break: break-word;
    }
    .lr-bar-track {
      width: 100%;
      height: 5px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 999px;
      overflow: hidden;
    }
    #lr-bar-fill {
      height: 100%;
      width: 28%;
      background: linear-gradient(90deg, #0284c7, #38bdf8);
      border-radius: 999px;
      transition: width 0.28s ease, background 0.28s ease;
    }
    @keyframes lrSpin {
      to { transform: rotate(360deg); }
    }

    /* =======================================================================
       2. FLOATING IG SPLIT TRIGGER & CAROUSEL SPLIT MODAL
       ======================================================================= */
    #ig-split-fab {
      position: fixed;
      right: 14px;
      bottom: 74px;
      z-index: 2147483000;
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 10px 14px;
      border-radius: 999px;
      border: 1px solid rgba(255, 255, 255, 0.18);
      background: linear-gradient(135deg, #18181b 0%, #27272a 100%);
      color: #ffffff;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 12.5px;
      font-weight: 600;
      box-shadow: 0 10px 26px rgba(0, 0, 0, 0.42);
      cursor: pointer;
      user-select: none;
      -webkit-tap-highlight-color: transparent;
    }
    #ig-split-fab:active {
      transform: scale(0.96);
    }
    #ig-split-fab .ig-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: linear-gradient(45deg, #f59e0b, #ec4899);
    }

    #ig-split-modal {
      position: fixed;
      inset: 0;
      z-index: 2147483640;
      background: rgba(9, 9, 11, 0.82);
      backdrop-filter: blur(10px);
      -webkit-backdrop-filter: blur(10px);
      display: none;
      flex-direction: column;
      justify-content: flex-end;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #fafafa;
    }
    #ig-split-modal.open {
      display: flex;
    }
    .ig-sheet {
      background: #141417;
      border-top: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 22px 22px 0 0;
      padding: 16px 18px 24px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 -16px 40px rgba(0, 0, 0, 0.6);
    }
    .ig-sheet-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
    }
    .ig-sheet-title {
      font-size: 16px;
      font-weight: 700;
    }
    .ig-close-btn {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      border: 1px solid rgba(255, 255, 255, 0.14);
      background: rgba(255, 255, 255, 0.06);
      color: #e4e4e7;
      font-size: 16px;
      cursor: pointer;
    }
    .ig-preview-box {
      background: #09090b;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 14px;
      padding: 12px;
      margin-bottom: 14px;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .ig-preview-stage {
      position: relative;
      max-width: 100%;
      max-height: 230px;
      display: inline-block;
      overflow: hidden;
      border-radius: 6px;
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.5);
    }
    #ig-preview-canvas {
      display: block;
      max-width: 100%;
      max-height: 230px;
      width: auto;
      height: auto;
    }
    #ig-grid-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      pointer-events: none;
    }
    .ig-slice-cell {
      flex: 1;
      height: 100%;
      border-right: 1.5px dashed rgba(251, 191, 36, 0.9);
      position: relative;
      box-sizing: border-box;
    }
    .ig-slice-cell:last-child {
      border-right: none;
    }
    .ig-slice-badge {
      position: absolute;
      top: 6px;
      left: 6px;
      background: rgba(0, 0, 0, 0.72);
      color: #fbbf24;
      border: 1px solid rgba(251, 191, 36, 0.45);
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 6px;
    }
    #ig-dim-info {
      margin-top: 8px;
      font-size: 11.5px;
      color: #a1a1aa;
    }
    .ig-label {
      font-size: 12px;
      font-weight: 600;
      color: #a1a1aa;
      margin-bottom: 6px;
      display: block;
    }
    .ig-seg-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      margin-bottom: 14px;
    }
    .ig-seg-btn {
      padding: 10px 8px;
      border-radius: 11px;
      border: 1px solid rgba(255, 255, 255, 0.12);
      background: #1f1f23;
      color: #d4d4d8;
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      text-align: center;
    }
    .ig-seg-btn.active {
      background: rgba(245, 158, 11, 0.18);
      border-color: #f59e0b;
      color: #fbbf24;
    }
    .ig-actions {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
      margin-top: 6px;
    }
    .ig-btn-primary {
      padding: 12px 12px;
      border-radius: 12px;
      border: none;
      background: #f59e0b;
      color: #09090b;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
    }
    .ig-btn-secondary {
      padding: 12px 12px;
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.18);
      background: #27272a;
      color: #ffffff;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
    }
  `;
  (document.head || document.documentElement).appendChild(style);

  // Build Lightroom HUD DOM
  const hudBackdrop = document.createElement('div');
  hudBackdrop.id = 'lr-save-backdrop';
  hudBackdrop.innerHTML = `
    <div id="lr-save-card">
      <div class="lr-ring-wrap">
        <div class="lr-spinner" id="lr-spinner"></div>
        <div class="lr-check" id="lr-check">✓</div>
        <div class="lr-error-icon" id="lr-error-icon">!</div>
      </div>
      <div id="lr-save-title">Menyimpan ke Galeri...</div>
      <div id="lr-save-sub">Memproses foto resolusi penuh...</div>
      <div class="lr-bar-track">
        <div id="lr-bar-fill"></div>
      </div>
    </div>
  `;

  let hudTimer = null;
  let hudProgressInterval = null;

  function showSavingHud(title, subtitle) {
    if (!hudBackdrop.parentNode && document.body) document.body.appendChild(hudBackdrop);
    clearTimeout(hudTimer);
    clearInterval(hudProgressInterval);

    const spinner = document.getElementById('lr-spinner');
    const check = document.getElementById('lr-check');
    const errIcon = document.getElementById('lr-error-icon');
    const titleEl = document.getElementById('lr-save-title');
    const subEl = document.getElementById('lr-save-sub');
    const fillEl = document.getElementById('lr-bar-fill');

    spinner.style.display = 'block';
    check.style.display = 'none';
    errIcon.style.display = 'none';
    titleEl.textContent = title || 'Menyimpan ke Galeri...';
    subEl.textContent = subtitle || 'Memproses foto resolusi penuh...';
    fillEl.style.background = 'linear-gradient(90deg, #0284c7, #38bdf8)';
    fillEl.style.width = '22%';

    hudBackdrop.classList.add('visible');

    let pct = 22;
    hudProgressInterval = setInterval(() => {
      if (pct < 86) {
        pct += (88 - pct) * 0.14;
        fillEl.style.width = pct.toFixed(1) + '%';
      }
    }, 180);
  }

  window.__showLightroomSavingHud = showSavingHud;

  window.__onGallerySaveProgress = function (requestId, current, total, fileName) {
    clearInterval(hudProgressInterval);
    const titleEl = document.getElementById('lr-save-title');
    const subEl = document.getElementById('lr-save-sub');
    const fillEl = document.getElementById('lr-bar-fill');
    if (titleEl) titleEl.textContent = `Menyimpan Slide ${current} dari ${total}...`;
    if (subEl) subEl.textContent = fileName || 'Menulis ke Galeri...';
    if (fillEl) {
      const pct = Math.min(95, Math.round((current / total) * 92));
      fillEl.style.width = pct + '%';
    }
  };

  window.__onGallerySaveFinished = function (requestId, success, info) {
    clearInterval(hudProgressInterval);
    clearTimeout(hudTimer);

    const spinner = document.getElementById('lr-spinner');
    const check = document.getElementById('lr-check');
    const errIcon = document.getElementById('lr-error-icon');
    const titleEl = document.getElementById('lr-save-title');
    const subEl = document.getElementById('lr-save-sub');
    const fillEl = document.getElementById('lr-bar-fill');

    if (!hudBackdrop.parentNode && document.body) document.body.appendChild(hudBackdrop);
    hudBackdrop.classList.add('visible');

    if (spinner) spinner.style.display = 'none';
    if (success) {
      if (check) check.style.display = 'flex';
      if (errIcon) errIcon.style.display = 'none';
      if (titleEl) titleEl.textContent = 'Tersimpan di Galeri';
      if (subEl) subEl.textContent = info || 'Pictures/EXIFFrameStudio';
      if (fillEl) {
        fillEl.style.background = '#10b981';
        fillEl.style.width = '100%';
      }
    } else {
      if (check) check.style.display = 'none';
      if (errIcon) errIcon.style.display = 'flex';
      if (titleEl) titleEl.textContent = 'Gagal Menyimpan';
      if (subEl) subEl.textContent = info || 'Terjadi kesalahan saat menyimpan';
      if (fillEl) {
        fillEl.style.background = '#ef4444';
        fillEl.style.width = '100%';
      }
    }

    hudTimer = setTimeout(() => {
      hudBackdrop.classList.remove('visible');
    }, success ? 1350 : 2200);
  };

  // Wrap AndroidGallery.saveBase64Image agar pemanggilan langsung otomatis menampilkan loading Lightroom
  if (window.AndroidGallery && typeof window.AndroidGallery.saveBase64Image === 'function') {
    const origSave = window.AndroidGallery.saveBase64Image.bind(window.AndroidGallery);
    window.AndroidGallery.saveBase64Image = function (dataUrl, fileName) {
      const reqId = 'save_' + Date.now();
      showSavingHud('Menyimpan ke Galeri...', fileName || 'Memproses foto resolusi penuh...');
      if (typeof window.AndroidGallery.saveBase64ImageWithId === 'function') {
        window.AndroidGallery.saveBase64ImageWithId(dataUrl, fileName || '', reqId);
      } else {
        origSave(dataUrl, fileName || '');
        setTimeout(() => window.__onGallerySaveFinished(reqId, true, fileName || ''), 700);
      }
    };
  }

  // =========================================================================
  // 2. HELPER: CAPTURE ACTIVE RENDERED FRAMED PHOTO FROM PAGE
  // =========================================================================
  function captureCurrentFramedCanvas() {
    const canvases = Array.from(document.querySelectorAll('canvas')).filter(c => {
      if (c.id === 'ig-preview-canvas') return false;
      const rect = c.getBoundingClientRect();
      return c.width >= 150 && c.height >= 150 && rect.width > 40 && rect.height > 40;
    });

    if (canvases.length > 0) {
      canvases.sort((a, b) => (b.width * b.height) - (a.width * a.height));
      return canvases[0];
    }

    const imgs = Array.from(document.querySelectorAll('img')).filter(img => {
      const rect = img.getBoundingClientRect();
      return img.complete && img.naturalWidth >= 200 && img.naturalHeight >= 200 && rect.width > 60;
    });

    if (imgs.length > 0) {
      imgs.sort((a, b) => (b.naturalWidth * b.naturalHeight) - (a.naturalWidth * a.naturalHeight));
      const bestImg = imgs[0];
      const off = document.createElement('canvas');
      off.width = bestImg.naturalWidth;
      off.height = bestImg.naturalHeight;
      const ctx = off.getContext('2d');
      ctx.drawImage(bestImg, 0, 0);
      return off;
    }

    return null;
  }

  // =========================================================================
  // 3. FIX SHARE BUTTON & WEB SHARE API (DIRECT ANDROID CHOOSER)
  // =========================================================================
  function blobOrFileToDataUrl(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  navigator.canShare = function () {
    return true;
  };

  navigator.share = async function (shareData) {
    if (!window.AndroidShare) {
      throw new Error('AndroidShare bridge tidak tersedia');
    }
    if (shareData && Array.isArray(shareData.files) && shareData.files.length > 0) {
      if (shareData.files.length === 1) {
        const f = shareData.files[0];
        const dataUrl = await blobOrFileToDataUrl(f);
        window.AndroidShare.shareBase64Image(dataUrl, f.name || `EXIF_${Date.now()}.jpg`);
      } else {
        const payload = [];
        for (let i = 0; i < shareData.files.length; i++) {
          const f = shareData.files[i];
          const dataUrl = await blobOrFileToDataUrl(f);
          payload.push({
            dataUrl,
            fileName: f.name || `EXIF_Slide_${i + 1}_${Date.now()}.jpg`
          });
        }
        window.AndroidShare.shareMultipleBase64Images(JSON.stringify(payload));
      }
      return;
    }

    if (shareData && typeof shareData.url === 'string' && shareData.url.startsWith('data:image/')) {
      window.AndroidShare.shareBase64Image(shareData.url, `EXIF_${Date.now()}.jpg`);
      return;
    }

    const canvas = captureCurrentFramedCanvas();
    if (canvas) {
      const dataUrl = canvas.toDataURL('image/jpeg', 0.95);
      window.AndroidShare.shareBase64Image(dataUrl, `EXIF_${Date.now()}.jpg`);
    }
  };

  document.addEventListener('click', function (e) {
    const btn = e.target.closest('button, a, [role="button"]');
    if (!btn) return;
    if (btn.closest('#ig-split-modal') || btn.id === 'ig-split-fab') return;

    const label = (
      (btn.innerText || '') + ' ' +
      (btn.getAttribute('aria-label') || '') + ' ' +
      (btn.getAttribute('title') || '') + ' ' +
      (btn.id || '') + ' ' +
      (typeof btn.className === 'string' ? btn.className : '')
    ).toLowerCase();

    const isShareBtn = /\b(share|bagikan)\b/.test(label);
    if (!isShareBtn) return;

    const href = btn.getAttribute('href') || '';
    if (href.startsWith('data:image/')) {
      e.preventDefault();
      e.stopPropagation();
      window.AndroidShare && window.AndroidShare.shareBase64Image(href, `EXIF_${Date.now()}.jpg`);
      return;
    }

    let nativeShareCalled = false;
    const origShare = navigator.share;
    navigator.share = async function (...args) {
      nativeShareCalled = true;
      return origShare.apply(this, args);
    };

    setTimeout(() => {
      navigator.share = origShare;
      if (!nativeShareCalled && window.AndroidShare) {
        const canvas = captureCurrentFramedCanvas();
        if (canvas) {
          const dataUrl = canvas.toDataURL('image/jpeg', 0.95);
          window.AndroidShare.shareBase64Image(dataUrl, `EXIF_${Date.now()}.jpg`);
        }
      }
    }, 140);
  }, true);

  // =========================================================================
  // 4. INSTAGRAM CAROUSEL SPLIT FEATURE (2, 3, ATAU 4 SLIDE)
  // =========================================================================
  let splitCount = 3;
  let splitAspect = 'original';
  let sourceCanvasRef = null;

  const fab = document.createElement('button');
  fab.id = 'ig-split-fab';
  fab.type = 'button';
  fab.innerHTML = `<span class="ig-dot"></span><span>Split IG</span>`;

  const modal = document.createElement('div');
  modal.id = 'ig-split-modal';
  modal.innerHTML = `
    <div class="ig-sheet">
      <div class="ig-sheet-header">
        <div class="ig-sheet-title">Split Carousel Instagram</div>
        <button type="button" class="ig-close-btn" id="ig-close-btn">✕</button>
      </div>

      <div class="ig-preview-box">
        <div class="ig-preview-stage">
          <canvas id="ig-preview-canvas"></canvas>
          <div id="ig-grid-overlay"></div>
        </div>
        <div id="ig-dim-info">Memuat pratinjau...</div>
      </div>

      <span class="ig-label">Jumlah Slide Instagram</span>
      <div class="ig-seg-row" id="ig-count-group">
        <button type="button" class="ig-seg-btn" data-count="2">2 Slide</button>
        <button type="button" class="ig-seg-btn active" data-count="3">3 Slide</button>
        <button type="button" class="ig-seg-btn" data-count="4">4 Slide</button>
      </div>

      <span class="ig-label">Rasio Per Slide</span>
      <div class="ig-seg-row" id="ig-aspect-group">
        <button type="button" class="ig-seg-btn active" data-aspect="original">Asli (Frame)</button>
        <button type="button" class="ig-seg-btn" data-aspect="4:5">4:5 Portrait</button>
        <button type="button" class="ig-seg-btn" data-aspect="1:1">1:1 Square</button>
      </div>

      <div class="ig-actions">
        <button type="button" class="ig-btn-secondary" id="ig-share-slides-btn">Share Slide</button>
        <button type="button" class="ig-btn-primary" id="ig-save-slides-btn">Simpan ke Galeri</button>
      </div>
    </div>
  `;

  function buildPanoramaCanvas(srcCanvas, slides, aspectMode) {
    const sw = srcCanvas.width;
    const sh = srcCanvas.height;

    if (aspectMode === 'original') {
      const out = document.createElement('canvas');
      out.width = sw;
      out.height = sh;
      out.getContext('2d').drawImage(srcCanvas, 0, 0);
      return out;
    }

    const slideRatio = aspectMode === '4:5' ? (4 / 5) : 1.0;
    const totalRatio = slideRatio * slides;

    let panoW, panoH;
    if (sw / sh >= totalRatio) {
      panoW = sw;
      panoH = Math.round(sw / totalRatio);
    } else {
      panoH = sh;
      panoW = Math.round(sh * totalRatio);
    }

    const out = document.createElement('canvas');
    out.width = panoW;
    out.height = panoH;
    const ctx = out.getContext('2d');

    let bgColor = '#ffffff';
    try {
      const px = srcCanvas.getContext('2d').getImageData(2, 2, 1, 1).data;
      bgColor = `rgb(${px[0]}, ${px[1]}, ${px[2]})`;
    } catch (_) {}

    ctx.fillStyle = bgColor;
    ctx.fillRect(0, 0, panoW, panoH);

    const scale = Math.min(panoW / sw, panoH / sh);
    const drawW = Math.round(sw * scale);
    const drawH = Math.round(sh * scale);
    const offsetX = Math.round((panoW - drawW) / 2);
    const offsetY = Math.round((panoH - drawH) / 2);

    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(srcCanvas, offsetX, offsetY, drawW, drawH);
    return out;
  }

  function renderSplitPreview() {
    if (!sourceCanvasRef) return;
    const pano = buildPanoramaCanvas(sourceCanvasRef, splitCount, splitAspect);
    const prevCanvas = document.getElementById('ig-preview-canvas');
    prevCanvas.width = pano.width;
    prevCanvas.height = pano.height;
    prevCanvas.getContext('2d').drawImage(pano, 0, 0);

    const overlay = document.getElementById('ig-grid-overlay');
    overlay.innerHTML = '';
    for (let i = 1; i <= splitCount; i++) {
      const cell = document.createElement('div');
      cell.className = 'ig-slice-cell';
      cell.innerHTML = `<span class="ig-slice-badge">${i}/${splitCount}</span>`;
      overlay.appendChild(cell);
    }

    const perW = Math.floor(pano.width / splitCount);
    const perH = pano.height;
    document.getElementById('ig-dim-info').textContent =
      `${splitCount} Slide • Ukuran per slide: ${perW} × ${perH} px`;

    document.getElementById('ig-save-slides-btn').textContent = `Simpan (${splitCount} Slide)`;
    document.getElementById('ig-share-slides-btn').textContent = `Share (${splitCount} Slide)`;
  }

  function generateSliceDataUrls() {
    if (!sourceCanvasRef) return [];
    const pano = buildPanoramaCanvas(sourceCanvasRef, splitCount, splitAspect);
    const totalW = pano.width;
    const totalH = pano.height;
    const baseSliceW = Math.floor(totalW / splitCount);
    const stamp = Date.now();
    const results = [];

    for (let i = 0; i < splitCount; i++) {
      const sx = i * baseSliceW;
      const sliceW = (i === splitCount - 1) ? (totalW - sx) : baseSliceW;

      const sliceCanvas = document.createElement('canvas');
      sliceCanvas.width = sliceW;
      sliceCanvas.height = totalH;
      const sctx = sliceCanvas.getContext('2d');
      sctx.imageSmoothingEnabled = true;
      sctx.imageSmoothingQuality = 'high';
      sctx.drawImage(pano, sx, 0, sliceW, totalH, 0, 0, sliceW, totalH);

      results.push({
        dataUrl: sliceCanvas.toDataURL('image/jpeg', 0.95),
        fileName: `EXIF_IG_${i + 1}of${splitCount}_${stamp}.jpg`
      });
    }
    return results;
  }

  function mountUi() {
    if (!document.body) return;
    if (!hudBackdrop.parentNode) document.body.appendChild(hudBackdrop);
    if (!fab.parentNode) document.body.appendChild(fab);
    if (!modal.parentNode) document.body.appendChild(modal);

    fab.addEventListener('click', () => {
      const captured = captureCurrentFramedCanvas();
      if (!captured) {
        alert('Silakan pilih foto terlebih dahulu sebelum melakukan split Instagram.');
        return;
      }
      sourceCanvasRef = captured;
      renderSplitPreview();
      modal.classList.add('open');
    });

    document.getElementById('ig-close-btn').addEventListener('click', () => {
      modal.classList.remove('open');
    });

    modal.addEventListener('click', (e) => {
      if (e.target === modal) modal.classList.remove('open');
    });

    document.querySelectorAll('#ig-count-group .ig-seg-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#ig-count-group .ig-seg-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        splitCount = parseInt(btn.getAttribute('data-count'), 10) || 3;
        renderSplitPreview();
      });
    });

    document.querySelectorAll('#ig-aspect-group .ig-seg-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#ig-aspect-group .ig-seg-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        splitAspect = btn.getAttribute('data-aspect') || 'original';
        renderSplitPreview();
      });
    });

    document.getElementById('ig-save-slides-btn').addEventListener('click', () => {
      modal.classList.remove('open');
      showSavingHud(`Menyimpan ${splitCount} Slide...`, 'Memotong & memproses resolusi penuh...');

      setTimeout(() => {
        const slices = generateSliceDataUrls();
        if (!slices.length) {
          window.__onGallerySaveFinished('', false, 'Gagal memotong gambar');
          return;
        }
        const reqId = 'split_' + Date.now();
        if (window.AndroidGallery && typeof window.AndroidGallery.saveBatchBase64Images === 'function') {
          window.AndroidGallery.saveBatchBase64Images(JSON.stringify(slices), reqId);
        } else {
          window.__onGallerySaveFinished(reqId, false, 'Bridge Galeri tidak ditemukan');
        }
      }, 60);
    });

    document.getElementById('ig-share-slides-btn').addEventListener('click', () => {
      const slices = generateSliceDataUrls();
      if (!slices.length) return;
      modal.classList.remove('open');
      if (window.AndroidShare && typeof window.AndroidShare.shareMultipleBase64Images === 'function') {
        window.AndroidShare.shareMultipleBase64Images(JSON.stringify(slices));
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mountUi);
  } else {
    mountUi();
  }
})();
