/*
 * Image Shield — client-side image resize / recompress / adversarial cloaking.
 *
 * Everything runs in the browser. The source image is never uploaded anywhere.
 *
 * The "cloaking" routines are *approximations* of the ideas behind Glaze and
 * Nightshade (University of Chicago). The real tools use trained neural networks;
 * here we apply structured, perceptually-masked spatial perturbations. See the
 * disclaimer in index.html.
 */
(() => {
  "use strict";

  // ---- DOM ----
  const $ = (id) => document.getElementById(id);
  const dropzone = $("dropzone");
  const fileInput = $("fileInput");
  const previewCanvas = $("previewCanvas");
  const ctx = previewCanvas.getContext("2d", { willReadFrequently: true });
  const emptyState = $("emptyState");

  const els = {
    srcName: $("srcName"), srcDims: $("srcDims"), srcSize: $("srcSize"),
    sourceInfo: $("sourceInfo"),
    resizeMode: $("resizeMode"),
    scaleControls: $("scaleControls"), scalePct: $("scalePct"), scalePctOut: $("scalePctOut"),
    dimsControls: $("dimsControls"), widthInput: $("widthInput"),
    heightInput: $("heightInput"), lockAspect: $("lockAspect"),
    longestControls: $("longestControls"), longestInput: $("longestInput"),
    formatSelect: $("formatSelect"), qualityRow: $("qualityRow"),
    qualityInput: $("qualityInput"), qualityOut: $("qualityOut"),
    cloakMode: $("cloakMode"), cloakStrength: $("cloakStrength"),
    cloakStrengthOut: $("cloakStrengthOut"), perceptualMask: $("perceptualMask"),
    seedInput: $("seedInput"),
    processBtn: $("processBtn"), downloadBtn: $("downloadBtn"),
    resultInfo: $("resultInfo"),
  };

  // ---- state ----
  const state = {
    img: null,            // source HTMLImageElement
    srcW: 0, srcH: 0,
    srcBytes: 0,
    srcName: "image",
    resultBlob: null,     // last processed Blob
    resultURL: null,      // object URL for download
    view: "original",     // "original" | "result"
    resultBitmap: null,   // ImageBitmap of processed result for preview
  };

  // =====================================================================
  // File loading
  // =====================================================================
  function loadFile(file) {
    if (!file || !file.type.startsWith("image/")) {
      alert("Please choose an image file.");
      return;
    }
    state.srcBytes = file.size;
    state.srcName = file.name.replace(/\.[^.]+$/, "") || "image";
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      state.img = img;
      state.srcW = img.naturalWidth;
      state.srcH = img.naturalHeight;
      onImageLoaded();
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      alert("Could not load that image.");
    };
    img.src = url;
  }

  function onImageLoaded() {
    els.sourceInfo.classList.remove("hidden");
    els.srcName.textContent = state.srcName;
    els.srcDims.textContent = `${state.srcW} × ${state.srcH}`;
    els.srcSize.textContent = formatBytes(state.srcBytes);
    els.widthInput.value = state.srcW;
    els.heightInput.value = state.srcH;
    els.processBtn.disabled = false;
    // reset result
    clearResult();
    state.view = "original";
    setActiveViewButton();
    drawOriginal();
  }

  // =====================================================================
  // Preview drawing
  // =====================================================================
  function drawOriginal() {
    if (!state.img) return;
    previewCanvas.width = state.srcW;
    previewCanvas.height = state.srcH;
    ctx.drawImage(state.img, 0, 0);
    showCanvas();
  }

  function drawResult() {
    if (!state.resultBitmap) return;
    previewCanvas.width = state.resultBitmap.width;
    previewCanvas.height = state.resultBitmap.height;
    ctx.drawImage(state.resultBitmap, 0, 0);
    showCanvas();
  }

  function showCanvas() {
    emptyState.style.display = "none";
    previewCanvas.style.display = "block";
  }

  // =====================================================================
  // Resize math
  // =====================================================================
  function targetDimensions() {
    const mode = els.resizeMode.value;
    const w = state.srcW, h = state.srcH;
    if (mode === "scale") {
      const f = clampNum(parseFloat(els.scalePct.value), 1, 1000) / 100;
      return [Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f))];
    }
    if (mode === "dims") {
      return [
        clampNum(parseInt(els.widthInput.value, 10) || w, 1, 20000),
        clampNum(parseInt(els.heightInput.value, 10) || h, 1, 20000),
      ];
    }
    if (mode === "longest") {
      const edge = clampNum(parseInt(els.longestInput.value, 10) || Math.max(w, h), 1, 20000);
      const f = edge / Math.max(w, h);
      return [Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f))];
    }
    return [w, h];
  }

  // =====================================================================
  // Deterministic PRNG (mulberry32) so results are reproducible per seed
  // =====================================================================
  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  // =====================================================================
  // Cloaking: build an additive perturbation field and blend it in,
  // optionally masked by local texture so it hides in busy regions.
  // =====================================================================
  function applyCloak(imageData, opts) {
    const { mode, strength, mask, seed } = opts;
    if (mode === "none") return;

    const { data, width: W, height: H } = imageData;
    const rand = mulberry32(seed);

    // Precompute a per-pixel texture/edge magnitude (luma gradient) for masking.
    let edge = null;
    if (mask) edge = computeEdgeMap(data, W, H);

    const doGlaze = mode === "glaze" || mode === "both";
    const doShade = mode === "nightshade" || mode === "both";

    // Glaze-style: high-frequency, edge-following perturbation that targets the
    // texture/"style" signal. Implemented as oriented per-pixel noise.
    // Nightshade-style: lower-frequency structured field that shifts broad
    // color/feature statistics ("concept"), via smooth sinusoidal lattices.

    // Random phases/frequencies for the low-frequency Nightshade field.
    const fx1 = 0.6 + rand() * 1.8, fy1 = 0.6 + rand() * 1.8;
    const fx2 = 0.3 + rand() * 1.1, fy2 = 0.3 + rand() * 1.1;
    const ph1 = rand() * Math.PI * 2, ph2 = rand() * Math.PI * 2;
    const TWO_PI = Math.PI * 2;

    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const i = (y * 4 * W) + x * 4;

        let dr = 0, dg = 0, db = 0;

        if (doGlaze) {
          // High-frequency hash noise per channel (decorrelated).
          const n = hashNoise(x, y, seed);
          dr += (n.r - 0.5);
          dg += (n.g - 0.5);
          db += (n.b - 0.5);
        }

        if (doShade) {
          // Smooth low-frequency lattice, normalized to roughly [-0.5,0.5].
          const u = x / W, v = y / H;
          const s =
            0.6 * Math.sin(TWO_PI * (fx1 * u + fy1 * v) + ph1) +
            0.4 * Math.sin(TWO_PI * (fx2 * v - fy2 * u) + ph2);
          // push channels in slightly different directions to bias chroma
          dr += 0.5 * s;
          dg += 0.5 * s * 0.6;
          db += 0.5 * -s;
        }

        // Local masking: amplify perturbation where there is more texture,
        // attenuate it in flat areas where it would be visible.
        let amp = strength;
        if (edge) {
          const m = edge[y * W + x]; // 0..1
          amp *= 0.35 + 0.65 * m;
        }

        data[i]     = clamp255(data[i]     + dr * 2 * amp);
        data[i + 1] = clamp255(data[i + 1] + dg * 2 * amp);
        data[i + 2] = clamp255(data[i + 2] + db * 2 * amp);
        // alpha untouched
      }
    }
  }

  // Per-pixel decorrelated hash noise in [0,1] per channel.
  function hashNoise(x, y, seed) {
    const h = (a, b, c) => {
      let n = Math.imul(a + 0x9E3779B9, 0x85EBCA6B);
      n ^= Math.imul(b + 0xC2B2AE35, 0x27D4EB2F);
      n ^= Math.imul(c + 0x165667B1, 0x9E3779B9);
      n ^= n >>> 15;
      return (n >>> 0) / 4294967296;
    };
    return {
      r: h(x, y, seed),
      g: h(x, y, seed ^ 0x55555555),
      b: h(x, y, seed ^ 0xAAAAAAAA),
    };
  }

  // Sobel-ish luma gradient magnitude, normalized to 0..1.
  function computeEdgeMap(data, W, H) {
    const luma = new Float32Array(W * H);
    for (let p = 0, q = 0; p < data.length; p += 4, q++) {
      luma[q] = 0.299 * data[p] + 0.587 * data[p + 1] + 0.114 * data[p + 2];
    }
    const edge = new Float32Array(W * H);
    let max = 1e-6;
    for (let y = 1; y < H - 1; y++) {
      for (let x = 1; x < W - 1; x++) {
        const i = y * W + x;
        const gx = luma[i - 1] - luma[i + 1];
        const gy = luma[i - W] - luma[i + W];
        const m = Math.sqrt(gx * gx + gy * gy);
        edge[i] = m;
        if (m > max) max = m;
      }
    }
    for (let i = 0; i < edge.length; i++) edge[i] /= max;
    return edge;
  }

  // =====================================================================
  // Process pipeline: resize -> cloak -> encode
  // =====================================================================
  async function process() {
    if (!state.img) return;
    els.processBtn.disabled = true;
    els.processBtn.textContent = "Processing…";

    try {
      const [tw, th] = targetDimensions();

      // Draw resized image to an offscreen canvas.
      const off = document.createElement("canvas");
      off.width = tw; off.height = th;
      const octx = off.getContext("2d", { willReadFrequently: true });
      octx.imageSmoothingEnabled = true;
      octx.imageSmoothingQuality = "high";
      octx.drawImage(state.img, 0, 0, tw, th);

      // Cloak.
      const cloakMode = els.cloakMode.value;
      if (cloakMode !== "none") {
        const imageData = octx.getImageData(0, 0, tw, th);
        applyCloak(imageData, {
          mode: cloakMode,
          strength: clampNum(parseInt(els.cloakStrength.value, 10), 1, 64),
          mask: els.perceptualMask.checked,
          seed: (parseInt(els.seedInput.value, 10) || 0) >>> 0,
        });
        octx.putImageData(imageData, 0, 0);
      }

      // Encode.
      const format = els.formatSelect.value;
      const quality = clampNum(parseInt(els.qualityInput.value, 10), 1, 100) / 100;
      const blob = await canvasToBlob(off, format, format === "image/png" ? undefined : quality);

      clearResult();
      state.resultBlob = blob;
      state.resultURL = URL.createObjectURL(blob);
      state.resultBitmap = await createImageBitmap(blob);

      els.downloadBtn.disabled = false;
      els.resultInfo.textContent =
        `${tw}×${th} · ${prettyFormat(format)} · ${formatBytes(blob.size)}`;

      // Switch to result view.
      state.view = "result";
      setActiveViewButton();
      drawResult();
    } catch (err) {
      console.error(err);
      alert("Something went wrong while processing: " + err.message);
    } finally {
      els.processBtn.disabled = false;
      els.processBtn.textContent = "Apply & preview";
    }
  }

  function canvasToBlob(canvas, type, quality) {
    return new Promise((resolve, reject) => {
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error("Encoding failed"))),
        type,
        quality
      );
    });
  }

  function download() {
    if (!state.resultURL) return;
    const ext = extFor(els.formatSelect.value);
    const a = document.createElement("a");
    a.href = state.resultURL;
    a.download = `${state.srcName}-shielded.${ext}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  function clearResult() {
    if (state.resultURL) URL.revokeObjectURL(state.resultURL);
    state.resultURL = null;
    state.resultBlob = null;
    if (state.resultBitmap) { state.resultBitmap.close?.(); state.resultBitmap = null; }
    els.downloadBtn.disabled = true;
    els.resultInfo.textContent = "—";
  }

  // =====================================================================
  // Helpers
  // =====================================================================
  const clamp255 = (v) => (v < 0 ? 0 : v > 255 ? 255 : v);
  const clampNum = (v, lo, hi) => (isNaN(v) ? lo : Math.min(hi, Math.max(lo, v)));

  function formatBytes(n) {
    if (n < 1024) return n + " B";
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + " KB";
    return (n / (1024 * 1024)).toFixed(2) + " MB";
  }
  function prettyFormat(mime) {
    return { "image/jpeg": "JPEG", "image/webp": "WebP", "image/png": "PNG" }[mime] || mime;
  }
  function extFor(mime) {
    return { "image/jpeg": "jpg", "image/webp": "webp", "image/png": "png" }[mime] || "img";
  }

  function setActiveViewButton() {
    document.querySelectorAll(".seg button").forEach((b) => {
      b.classList.toggle("active", b.dataset.view === state.view);
    });
  }

  // =====================================================================
  // Wire up UI
  // =====================================================================
  function syncResizeControls() {
    const mode = els.resizeMode.value;
    els.scaleControls.classList.toggle("hidden", mode !== "scale");
    els.dimsControls.classList.toggle("hidden", mode !== "dims");
    els.longestControls.classList.toggle("hidden", mode !== "longest");
  }
  function syncQualityRow() {
    els.qualityRow.classList.toggle("hidden", els.formatSelect.value === "image/png");
  }

  // dropzone
  dropzone.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fileInput.click(); }
  });
  fileInput.addEventListener("change", (e) => {
    if (e.target.files[0]) loadFile(e.target.files[0]);
  });
  ["dragenter", "dragover"].forEach((ev) =>
    dropzone.addEventListener(ev, (e) => {
      e.preventDefault(); dropzone.classList.add("dragover");
    })
  );
  ["dragleave", "drop"].forEach((ev) =>
    dropzone.addEventListener(ev, (e) => {
      e.preventDefault(); dropzone.classList.remove("dragover");
    })
  );
  dropzone.addEventListener("drop", (e) => {
    const f = e.dataTransfer.files[0];
    if (f) loadFile(f);
  });

  // resize controls
  els.resizeMode.addEventListener("change", syncResizeControls);
  els.scalePct.addEventListener("input", () => (els.scalePctOut.textContent = els.scalePct.value + "%"));
  // aspect lock
  els.widthInput.addEventListener("input", () => {
    if (els.lockAspect.checked && state.srcW) {
      const w = parseInt(els.widthInput.value, 10);
      if (w > 0) els.heightInput.value = Math.round(w * state.srcH / state.srcW);
    }
  });
  els.heightInput.addEventListener("input", () => {
    if (els.lockAspect.checked && state.srcH) {
      const h = parseInt(els.heightInput.value, 10);
      if (h > 0) els.widthInput.value = Math.round(h * state.srcW / state.srcH);
    }
  });

  // format / quality
  els.formatSelect.addEventListener("change", syncQualityRow);
  els.qualityInput.addEventListener("input", () => (els.qualityOut.textContent = els.qualityInput.value));

  // cloak
  els.cloakStrength.addEventListener("input", () => (els.cloakStrengthOut.textContent = els.cloakStrength.value));

  // actions
  els.processBtn.addEventListener("click", process);
  els.downloadBtn.addEventListener("click", download);

  // view toggle
  document.querySelectorAll(".seg button").forEach((b) => {
    b.addEventListener("click", () => {
      state.view = b.dataset.view;
      setActiveViewButton();
      if (state.view === "result" && state.resultBitmap) drawResult();
      else drawOriginal();
    });
  });

  // init
  syncResizeControls();
  syncQualityRow();
})();
