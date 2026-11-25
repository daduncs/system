document.addEventListener('DOMContentLoaded', () => {
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const finePointer = window.matchMedia('(pointer: fine)').matches;

  const root = document.documentElement;
  const themeToggle = document.getElementById('themeToggle');
  const applyTheme = theme => {
    root.dataset.theme = theme;
    if (themeToggle) {
      themeToggle.setAttribute(
        'aria-label',
        theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'
      );
    }
    try {
      sessionStorage.setItem('system_preview_theme', theme);
    } catch {}
  };

  try {
    const savedTheme = sessionStorage.getItem('system_preview_theme');
    if (savedTheme === 'light' || savedTheme === 'dark') {
      applyTheme(savedTheme);
    }
  } catch {}

  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      applyTheme(root.dataset.theme === 'dark' ? 'light' : 'dark');
    });
  }

  const siteHeader = document.getElementById('siteHeader');
  const announcementBar = document.getElementById('announcementBar');
  const dismissAnnouncement = document.getElementById('dismissAnnouncement');
  const burger = document.getElementById('burger');
  const drawer = document.getElementById('drawer');

  let lastScrollY = window.scrollY;
  const handleHeaderScroll = () => {
    const currentY = window.scrollY;
    const isDrawerOpen = drawer && drawer.classList.contains('open');
    if (!isDrawerOpen && currentY > 120 && currentY > lastScrollY + 6) {
      siteHeader.classList.add('is-hidden');
    } else if (currentY < lastScrollY - 4 || currentY <= 120) {
      siteHeader.classList.remove('is-hidden');
    }
    lastScrollY = currentY;
  };
  window.addEventListener('scroll', handleHeaderScroll, { passive: true });

  if (dismissAnnouncement && announcementBar) {
    dismissAnnouncement.addEventListener('click', () => {
      announcementBar.hidden = true;
    });
  }

  const closeDrawer = () => {
    if (!burger || !drawer) return;
    burger.classList.remove('open');
    drawer.classList.remove('open');
    burger.setAttribute('aria-expanded', 'false');
  };

  if (burger && drawer) {
    burger.addEventListener('click', () => {
      const open = drawer.classList.toggle('open');
      burger.classList.toggle('open', open);
      burger.setAttribute('aria-expanded', String(open));
      if (open) siteHeader.classList.remove('is-hidden');
    });
    drawer.querySelectorAll('a').forEach(a => a.addEventListener('click', closeDrawer));
    window.addEventListener('resize', () => window.innerWidth > 1024 && closeDrawer());
  }

  const typeText = (el, text, msPerChar = 28, onDone) => {
    if (!el) return () => {};
    if (prefersReducedMotion) {
      el.textContent = text;
      if (onDone) onDone();
      return () => {};
    }
    let cancelled = false;
    let startTs = null;
    el.textContent = '';
    const step = ts => {
      if (cancelled) return;
      if (!startTs) startTs = ts;
      const count = Math.min(text.length, Math.floor((ts - startTs) / msPerChar) + 1);
      el.textContent = text.slice(0, count);
      if (count < text.length) {
        requestAnimationFrame(step);
      } else if (onDone) {
        onDone();
      }
    };
    requestAnimationFrame(step);
    return () => {
      cancelled = true;
    };
  };

  const heroSection = document.querySelector('.hero');
  const heroTypedTitle = document.getElementById('heroTypedTitle');
  if (heroSection) {
    requestAnimationFrame(() => heroSection.classList.add('is-ready'));
  }

  if (heroTypedTitle) {
    const rawLines = heroTypedTitle.dataset.typeLines || '';
    const lines = rawLines.split('|');
    const target = heroTypedTitle.querySelector('.typed-target');
    const cursor = heroTypedTitle.querySelector('.typing-cursor');
    if (target && lines.length === 2 && !prefersReducedMotion) {
      const joined = `${lines[0]}\n${lines[1]}`;
      let startTs = null;
      target.textContent = '';
      const stepHeroType = ts => {
        if (!startTs) startTs = ts;
        const count = Math.min(joined.length, Math.floor((ts - startTs) / 24) + 1);
        const slice = joined.slice(0, count);
        const brIdx = slice.indexOf('\n');
        if (brIdx === -1) {
          target.textContent = slice;
        } else {
          target.replaceChildren(
            document.createTextNode(slice.slice(0, brIdx)),
            document.createElement('br'),
            document.createTextNode(slice.slice(brIdx + 1))
          );
        }
        if (count < joined.length) {
          requestAnimationFrame(stepHeroType);
        } else if (cursor) {
          setTimeout(() => cursor.classList.add('is-done'), 600);
        }
      };
      requestAnimationFrame(stepHeroType);
    } else if (cursor) {
      cursor.classList.add('is-done');
    }
  }

  document.querySelectorAll('.module-list').forEach(list => {
    [...list.querySelectorAll('.module-pill')].forEach((pill, i) => {
      pill.style.setProperty('--i', String(i));
    });
  });

  const splitTargets = [...document.querySelectorAll('[data-split-reveal]')];
  if (!prefersReducedMotion && splitTargets.length) {
    splitTargets.forEach(el => {
      let wordIndex = 0;
      const nodes = [...el.childNodes];
      const frag = document.createDocumentFragment();
      nodes.forEach(node => {
        if (node.nodeType === Node.TEXT_NODE) {
          const parts = node.textContent.split(/(\s+)/);
          parts.forEach(part => {
            if (!part) return;
            if (/^\s+$/.test(part)) {
              frag.appendChild(document.createTextNode(part));
            } else {
              const wrap = document.createElement('span');
              wrap.className = 'split-word-wrap';
              const inner = document.createElement('span');
              inner.className = 'split-word';
              inner.style.setProperty('--word-delay', `${Math.min(wordIndex * 42, 420)}ms`);
              inner.textContent = part;
              wrap.appendChild(inner);
              frag.appendChild(wrap);
              wordIndex += 1;
            }
          });
        } else {
          frag.appendChild(node.cloneNode(true));
        }
      });
      el.replaceChildren(frag);
    });

    if ('IntersectionObserver' in window) {
      const splitObs = new IntersectionObserver(
        (entries, self) => {
          entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            entry.target.classList.add('is-revealed');
            self.unobserve(entry.target);
          });
        },
        { threshold: 0.05 }
      );
      splitTargets.forEach(el => splitObs.observe(el));
    } else {
      splitTargets.forEach(el => el.classList.add('is-revealed'));
    }
  } else {
    splitTargets.forEach(el => el.classList.add('is-revealed'));
  }

  const typedEls = [...document.querySelectorAll('.typed-text[data-type-text]')];
  if (!prefersReducedMotion && typedEls.length && 'IntersectionObserver' in window) {
    typedEls.forEach(el => {
      el.textContent = '';
    });
    const typeObs = new IntersectionObserver(
      (entries, self) => {
        entries.forEach(entry => {
          if (!entry.isIntersecting) return;
          const el = entry.target;
          self.unobserve(entry.target);
          const fullText = el.dataset.typeText || '';
          const msPerChar = fullText.length > 40 ? 13 : 36;
          const cursor = el.parentElement ? el.parentElement.querySelector('.typing-cursor--fade') : null;
          typeText(el, fullText, msPerChar, () => {
            if (cursor) setTimeout(() => cursor.classList.add('is-done'), 500);
          });
        });
      },
      { threshold: 0.12 }
    );
    typedEls.forEach(el => typeObs.observe(el));
  } else {
    document.querySelectorAll('.typing-cursor--fade').forEach(c => c.classList.add('is-done'));
  }

  const reveals = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && !prefersReducedMotion) {
    const revealObs = new IntersectionObserver(
      (entries, self) => {
        entries.forEach((entry, idx) => {
          if (!entry.isIntersecting) return;
          setTimeout(() => entry.target.classList.add('in'), idx * 45);
          self.unobserve(entry.target);
        });
      },
      { threshold: 0.02, rootMargin: '0px' }
    );
    reveals.forEach(el => revealObs.observe(el));
  } else {
    reveals.forEach(el => el.classList.add('in'));
  }

  const heroCanvas = document.getElementById('heroParticles');
  const purpleStops = [
    [109, 40, 217],
    [139, 92, 246],
    [184, 124, 248],
    [233, 213, 255],
    [168, 85, 247],
    [124, 58, 237]
  ];

  const samplePurple = angle => {
    const norm = ((angle / (Math.PI * 2)) % 1 + 1) % 1;
    const scaled = norm * purpleStops.length;
    const i0 = Math.floor(scaled) % purpleStops.length;
    const i1 = (i0 + 1) % purpleStops.length;
    const f = scaled - Math.floor(scaled);
    const c0 = purpleStops[i0];
    const c1 = purpleStops[i1];
    return [
      c0[0] + (c1[0] - c0[0]) * f,
      c0[1] + (c1[1] - c0[1]) * f,
      c0[2] + (c1[2] - c0[2]) * f
    ];
  };

  const initRadialParticleField = (canvas, container, opts = {}) => {
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const cell = opts.cell || 44;
    const restX = opts.restX || 0.54;
    const restY = opts.restY || 0.54;
    const noiseAmp = opts.noiseAmp !== undefined ? opts.noiseAmp : 1;
    const forceDark = Boolean(opts.forceDark);

    let particles = [];
    let width = 0;
    let height = 0;
    let fx = 0;
    let fy = 0;
    let targetFx = 0;
    let targetFy = 0;
    let pointerActive = false;
    let fieldActive = true;
    let fieldFrame = null;

    const buildGrid = () => {
      particles = [];
      const cols = Math.ceil(width / cell) + 2;
      const rows = Math.ceil(height / cell) + 2;
      const startX = (width - (cols - 1) * cell) * 0.5;
      const startY = (height - (rows - 1) * cell) * 0.5;
      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const hx = startX + c * cell + (Math.random() - 0.5) * cell * 0.76;
          const hy = startY + r * cell + (Math.random() - 0.5) * cell * 0.76;
          particles.push({
            hx,
            hy,
            phaseX: Math.random() * Math.PI * 2,
            phaseY: Math.random() * Math.PI * 2,
            speedX: 0.3 + Math.random() * 0.4,
            speedY: 0.3 + Math.random() * 0.4,
            jitter: 0.85 + Math.random() * 0.3
          });
        }
      }
    };

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      width = canvas.clientWidth || (container ? container.clientWidth : window.innerWidth) || 1200;
      height = canvas.clientHeight || (container ? container.clientHeight : window.innerHeight) || 600;
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      buildGrid();
      if (!pointerActive) {
        targetFx = width * restX;
        targetFy = height * restY;
        fx = targetFx;
        fy = targetFy;
      }
    };

    const draw = (now = 0) => {
      ctx.clearRect(0, 0, width, height);
      const t = prefersReducedMotion ? 0 : now * 0.001;

      if (!pointerActive) {
        targetFx = width * restX + Math.sin(t * 0.38) * 34;
        targetFy = height * restY + Math.cos(t * 0.29) * 26;
      }
      fx += (targetFx - fx) * 0.065;
      fy += (targetFy - fy) * 0.065;

      const light = !forceDark && root.dataset.theme === 'light';
      const mutedRgb = light ? [116, 112, 134] : [132, 124, 160];
      const radiusBase = Math.min(width, height) * 0.72;

      for (const p of particles) {
        const driftX = prefersReducedMotion ? 0 : Math.sin(t * p.speedX + p.phaseX) * 5;
        const driftY = prefersReducedMotion ? 0 : Math.cos(t * p.speedY + p.phaseY) * 5;
        const px = p.hx + driftX;
        const py = p.hy + driftY;

        const dx = px - fx;
        const dy = py - fy;
        const dist = Math.hypot(dx, dy) || 0.001;
        const angle = Math.atan2(dy, dx);
        const normR = dist / radiusBase;

        const noiseR =
          (Math.sin(angle * 2 + t * 0.55) * 0.085 +
            Math.cos(angle * 3 - t * 0.4) * 0.055 +
            Math.sin(angle * 5 + t * 0.3) * 0.025) *
          noiseAmp;

        const ringCenter = 0.56 + noiseR;
        const delta = normR - ringCenter;
        const sigma = delta < 0 ? 0.22 : 0.30;
        const band = Math.exp(-(delta * delta) / (2 * sigma * sigma));

        const push = band * 16;
        const drawX = px + (dx / dist) * push;
        const drawY = py + (dy / dist) * push;
        if (drawX < -16 || drawX > width + 16 || drawY < -16 || drawY > height + 16) continue;

        const len = (1.4 + band * 5.6) * p.jitter;
        const thick = 1.35 + band * 1.25;
        const alpha = (light ? 0.22 : 0.20) + band * (light ? 0.72 : 0.78);

        const [pr, pg, pb] = samplePurple(angle);
        const r = Math.round(mutedRgb[0] + (pr - mutedRgb[0]) * band);
        const g = Math.round(mutedRgb[1] + (pg - mutedRgb[1]) * band);
        const b = Math.round(mutedRgb[2] + (pb - mutedRgb[2]) * band);

        ctx.save();
        ctx.translate(drawX, drawY);
        ctx.rotate(angle);
        ctx.globalAlpha = alpha;
        ctx.fillStyle = `rgb(${r},${g},${b})`;
        ctx.beginPath();
        ctx.roundRect(-len * 0.5, -thick * 0.5, len, thick, thick * 0.5);
        ctx.fill();
        ctx.restore();
      }
      ctx.globalAlpha = 1;

      if (fieldActive && !prefersReducedMotion) {
        fieldFrame = requestAnimationFrame(draw);
      }
    };

    resize();
    window.addEventListener('resize', () => {
      resize();
      if (prefersReducedMotion) draw(0);
    });

    if (finePointer && !prefersReducedMotion && container) {
      container.addEventListener(
        'mousemove',
        e => {
          const rect = container.getBoundingClientRect();
          pointerActive = true;
          targetFx = e.clientX - rect.left;
          targetFy = e.clientY - rect.top;
        },
        { passive: true }
      );
      container.addEventListener('mouseleave', () => {
        pointerActive = false;
      });
    }

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(entries => {
        fieldActive = entries[0].isIntersecting;
        if (fieldActive && !fieldFrame && !prefersReducedMotion) {
          fieldFrame = requestAnimationFrame(draw);
        } else if (!fieldActive && fieldFrame) {
          cancelAnimationFrame(fieldFrame);
          fieldFrame = null;
        }
      }).observe(canvas);
    }

    draw(0);
  };

  if (heroCanvas) {
    initRadialParticleField(heroCanvas, heroSection, {
      cell: 44,
      restX: 0.54,
      restY: 0.54,
      noiseAmp: 1
    });
  }

  const splitIconImg = new Image();
  let butterflyCoords = null;
  splitIconImg.onload = () => {
    try {
      const off = document.createElement('canvas');
      off.width = 96;
      off.height = 96;
      const octx = off.getContext('2d');
      octx.drawImage(splitIconImg, 0, 0, 96, 96);
      const data = octx.getImageData(0, 0, 96, 96).data;
      const pts = [];
      for (let y = 0; y < 96; y += 3) {
        for (let x = 0; x < 96; x += 3) {
          if (data[(y * 96 + x) * 4 + 3] > 128) {
            pts.push({ x: (x - 48) / 48, y: (y - 48) / 48 });
          }
        }
      }
      if (pts.length > 20) butterflyCoords = pts;
    } catch {}
  };
  splitIconImg.src = 'assets/icon.png';

  const makeHexagonCoords = count => {
    const clusters = [];
    for (let k = 0; k < 6; k++) {
      const a = (k / 6) * Math.PI * 2 - Math.PI / 2;
      clusters.push({ cx: Math.cos(a) * 0.62, cy: Math.sin(a) * 0.62 });
    }
    return Array.from({ length: count }, (_, i) => {
      const c = clusters[i % 6];
      const theta = Math.random() * Math.PI * 2;
      const rad = Math.sqrt(Math.random()) * 0.19;
      return { x: c.cx + Math.cos(theta) * rad, y: c.cy + Math.sin(theta) * rad };
    });
  };

  document.querySelectorAll('.split-canvas').forEach((canvas, idx) => {
    const col = canvas.closest('.split-col');
    const shape = canvas.dataset.morphShape || 'hexagon';
    const ctx = canvas.getContext('2d');
    const dotCount = 210;
    const hexTargets = makeHexagonCoords(dotCount);
    const morphColors = ['#b87cf8', '#dfc2ff', '#9d5cf6', '#7c3aed'];

    const dots = Array.from({ length: dotCount }, (_, i) => {
      const rx = Math.random();
      const ry = Math.random();
      return {
        rx,
        ry,
        x: rx * 600,
        y: ry * 440,
        r: 1.1 + Math.random() * 1.4,
        phase: Math.random() * Math.PI * 2,
        speed: 0.008 + Math.random() * 0.012,
        color: morphColors[i % morphColors.length],
        fallbackTarget: hexTargets[i],
        seed: i
      };
    });

    let w = 600;
    let h = 440;
    let hovering = false;
    let mouseOffsetX = 0;
    let mouseOffsetY = 0;
    let morphBlend = 0;
    let active = false;
    let raf = null;

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = canvas.clientWidth || 600;
      h = canvas.clientHeight || 440;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      dots.forEach(d => {
        if (!hovering && morphBlend < 0.02) {
          d.x = d.rx * w;
          d.y = d.ry * h;
        }
      });
    };

    const draw = () => {
      ctx.clearRect(0, 0, w, h);
      const light = root.dataset.theme === 'light';
      const targetBlend = hovering && !prefersReducedMotion ? 1 : 0;
      morphBlend += (targetBlend - morphBlend) * 0.08;

      const scale = Math.min(w, h) * 0.44;
      const cx = w * 0.5 + mouseOffsetX * 18 * morphBlend;
      const cy = h * 0.5 + mouseOffsetY * 18 * morphBlend;

      for (const d of dots) {
        if (!prefersReducedMotion) d.phase += d.speed;
        const idleX = d.rx * w + Math.cos(d.phase + idx) * 8;
        const idleY = d.ry * h + Math.sin(d.phase) * 8;

        let shapePt = d.fallbackTarget;
        if (shape === 'butterfly' && butterflyCoords && butterflyCoords.length) {
          shapePt = butterflyCoords[d.seed % butterflyCoords.length];
        }
        const targetX = cx + shapePt.x * scale;
        const targetY = cy + shapePt.y * scale;

        const destX = idleX + (targetX - idleX) * morphBlend;
        const destY = idleY + (targetY - idleY) * morphBlend;
        d.x += (destX - d.x) * 0.16;
        d.y += (destY - d.y) * 0.16;

        const idleAlpha = 0.22 + 0.16 * Math.sin(d.phase);
        if (morphBlend > 0.06) {
          ctx.globalAlpha = idleAlpha * (1 - morphBlend) + 0.88 * morphBlend;
          ctx.fillStyle = d.color;
        } else {
          ctx.globalAlpha = idleAlpha;
          ctx.fillStyle = light ? '#45474d' : '#b7bfd9';
        }
        ctx.beginPath();
        ctx.arc(d.x, d.y, d.r + morphBlend * 0.9, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;

      if (active && !prefersReducedMotion) {
        raf = requestAnimationFrame(draw);
      }
    };

    if (col && finePointer && !prefersReducedMotion) {
      col.addEventListener('mouseenter', () => {
        hovering = true;
        if (!raf) raf = requestAnimationFrame(draw);
      });
      col.addEventListener('mousemove', e => {
        hovering = true;
        const rect = col.getBoundingClientRect();
        mouseOffsetX = (e.clientX - rect.left) / rect.width - 0.5;
        mouseOffsetY = (e.clientY - rect.top) / rect.height - 0.5;
        if (!raf) raf = requestAnimationFrame(draw);
      });
      col.addEventListener('mouseleave', () => {
        hovering = false;
        mouseOffsetX = 0;
        mouseOffsetY = 0;
      });
    }

    resize();
    draw();
    window.addEventListener('resize', () => {
      resize();
      draw();
    });

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(
        entries => {
          active = entries[0].isIntersecting;
          if (active && !raf) {
            if (prefersReducedMotion) draw();
            else raf = requestAnimationFrame(draw);
          } else if (!active && raf) {
            cancelAnimationFrame(raf);
            raf = null;
          }
        },
        { threshold: 0.01 }
      ).observe(col || canvas);
    } else {
      draw();
    }
  });

  const ctaCard = document.getElementById('downloadCtaCard');
  const ctaCanvas = document.getElementById('ctaParticles');
  if (ctaCanvas) {
    initRadialParticleField(ctaCanvas, ctaCard, {
      cell: 42,
      restX: 0.74,
      restY: 0.52,
      noiseAmp: 0.55,
      forceDark: true
    });
  }

  const trailerCard = document.getElementById('trailerCard');
  const iconWave = document.getElementById('iconWave');
  const footerWordmarkWrap = document.getElementById('footerWordmarkWrap');
  const floatLetters = [...document.querySelectorAll('.wm-float')];

  let ctaLocked = false;
  let curTrailerScale = 0.5;
  let curCtaScale = 0.85;
  let curFloatProgress = 0;

  if (!prefersReducedMotion) {
    let scrollChoreoRaf = null;

    const updateScrollChoreography = () => {
      const vh = window.innerHeight || 900;
      let stillMoving = false;

      if (heroSection) {
        if (window.scrollY > 10) heroSection.classList.add('is-scrolled');
        else heroSection.classList.remove('is-scrolled');

        const rect = heroSection.getBoundingClientRect();
        const centerTopProgress = Math.max(0, Math.min(1, (-rect.top) / Math.max(1, rect.height * 0.55)));
        heroSection.style.setProperty('--hero-particle-opacity', (1 - centerTopProgress).toFixed(3));
      }

      if (trailerCard) {
        const rect = trailerCard.getBoundingClientRect();
        const p = Math.min(1, Math.max(0, (vh - rect.top) / (vh * 0.88)));
        const target = 0.5 + p * 0.5;
        curTrailerScale += (target - curTrailerScale) * 0.16;
        trailerCard.style.setProperty('--trailer-scale', curTrailerScale.toFixed(4));
        if (Math.abs(target - curTrailerScale) > 0.0008) stillMoving = true;
      }

      if (iconWave) {
        const rect = iconWave.getBoundingClientRect();
        const driftX = Math.max(-110, Math.min(110, (vh * 0.55 - rect.top) * -0.14));
        iconWave.style.setProperty('--wave-scroll-x', `${driftX.toFixed(2)}px`);
      }

      if (ctaCard) {
        if (!ctaLocked) {
          const rect = ctaCard.getBoundingClientRect();
          const p = Math.min(1, Math.max(0, (vh * 0.85 - rect.top) / Math.max(1, rect.height * 0.72)));
          if (p >= 0.995) ctaLocked = true;
          const target = ctaLocked ? 1 : 0.85 + p * 0.15;
          curCtaScale += (target - curCtaScale) * 0.14;
          ctaCard.style.setProperty('--cta-scale', curCtaScale.toFixed(4));
          if (Math.abs(target - curCtaScale) > 0.0008) stillMoving = true;
        } else {
          ctaCard.style.setProperty('--cta-scale', '1');
        }
      }

      if (footerWordmarkWrap && floatLetters.length) {
        const rect = footerWordmarkWrap.getBoundingClientRect();
        const startY = vh;
        const endY = vh - rect.height * 1.1;
        const rawP = (startY - rect.top) / Math.max(1, startY - endY);
        const targetP = Math.min(1, Math.max(0, rawP));
        curFloatProgress += (targetP - curFloatProgress) * 0.08;
        floatLetters.forEach(el => {
          const fy = parseFloat(el.dataset.floatY || '0');
          const fr = parseFloat(el.dataset.floatRot || '0');
          el.style.setProperty('--lift-y', `${(fy * curFloatProgress).toFixed(2)}px`);
          el.style.setProperty('--lift-rot', `${(fr * curFloatProgress).toFixed(2)}deg`);
        });
        if (Math.abs(targetP - curFloatProgress) > 0.001) stillMoving = true;
      }

      if (stillMoving) {
        scrollChoreoRaf = requestAnimationFrame(updateScrollChoreography);
      } else {
        scrollChoreoRaf = null;
      }
    };

    const kickChoreography = () => {
      if (!scrollChoreoRaf) scrollChoreoRaf = requestAnimationFrame(updateScrollChoreography);
    };

    window.addEventListener('scroll', kickChoreography, { passive: true });
    window.addEventListener('resize', kickChoreography, { passive: true });
    kickChoreography();
  } else {
    if (trailerCard) trailerCard.style.setProperty('--trailer-scale', '1');
    if (ctaCard) ctaCard.style.setProperty('--cta-scale', '1');
  }

  if (footerWordmarkWrap && floatLetters.length && !prefersReducedMotion) {
    let footerInView = false;
    let bobRaf = null;
    const tickFooterBob = ts => {
      const t = ts * 0.001;
      floatLetters.forEach((el, i) => {
        const amp = parseFloat(el.dataset.bobAmp || '4');
        const dur = parseFloat(el.dataset.bobDur || '2.6');
        const bobY = Math.sin((t * Math.PI * 2) / dur + i * 0.9) * amp;
        const bobRot = Math.cos((t * Math.PI * 2) / (dur * 1.2) + i) * 1.2;
        el.style.setProperty('--bob-y', `${bobY.toFixed(2)}px`);
        el.style.setProperty('--bob-rot', `${bobRot.toFixed(2)}deg`);
      });
      if (footerInView) bobRaf = requestAnimationFrame(tickFooterBob);
    };
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(entries => {
        footerInView = entries[0].isIntersecting;
        if (footerInView && !bobRaf) bobRaf = requestAnimationFrame(tickFooterBob);
        else if (!footerInView && bobRaf) {
          cancelAnimationFrame(bobRaf);
          bobRaf = null;
        }
      }).observe(footerWordmarkWrap);
    }
  }

  const trailerCursor = document.getElementById('trailerCursor');
  const trailerPlayBtn = document.getElementById('trailerPlayBtn');
  const trailerModal = document.getElementById('trailerModal');
  const trailerModalClose = document.getElementById('trailerModalClose');
  const trailerIframe = document.getElementById('trailerIframe');
  const openTrailerFromCarousel = document.getElementById('openTrailerFromCarousel');
  const defaultTrailerId = 'Wdv_b5RYLt8';
  const defaultTrailerTitle = "This is System's official release trailer";

  if (trailerCard && trailerCursor && finePointer) {
    trailerCard.addEventListener('mousemove', e => {
      const rect = trailerCard.getBoundingClientRect();
      trailerCursor.style.left = `${e.clientX - rect.left}px`;
      trailerCursor.style.top = `${e.clientY - rect.top}px`;
      trailerCursor.classList.add('is-visible');
      trailerCard.classList.add('has-cursor');
    });
    trailerCard.addEventListener('mouseleave', () => {
      trailerCursor.classList.remove('is-visible');
      trailerCard.classList.remove('has-cursor');
    });
  }

  const openTrailerModal = (videoId = defaultTrailerId, title = defaultTrailerTitle) => {
    if (!trailerModal || !trailerIframe) return;
    trailerIframe.title = title;
    trailerIframe.src = `https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoId)}?autoplay=1&rel=0&modestbranding=1`;
    trailerModal.hidden = false;
    trailerModal.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  };

  const closeTrailerModal = () => {
    if (!trailerModal || !trailerIframe) return;
    trailerModal.hidden = true;
    trailerModal.setAttribute('aria-hidden', 'true');
    trailerIframe.removeAttribute('src');
    document.body.style.overflow = '';
  };

  if (trailerCard) {
    trailerCard.addEventListener('click', () => openTrailerModal(defaultTrailerId, defaultTrailerTitle));
    trailerCard.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openTrailerModal(defaultTrailerId, defaultTrailerTitle);
      }
    });
  }
  if (trailerPlayBtn) {
    trailerPlayBtn.addEventListener('click', e => {
      e.stopPropagation();
      openTrailerModal(defaultTrailerId, defaultTrailerTitle);
    });
  }
  if (openTrailerFromCarousel) {
    openTrailerFromCarousel.addEventListener('click', () => openTrailerModal(defaultTrailerId, defaultTrailerTitle));
  }

  document.querySelectorAll('.video-card[data-yt-id]').forEach(card => {
    const launch = () => openTrailerModal(card.dataset.ytId, card.dataset.ytTitle || 'System Client Video');
    card.addEventListener('click', launch);
    card.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        launch();
      }
    });
  });
  if (trailerModalClose) trailerModalClose.addEventListener('click', closeTrailerModal);
  if (trailerModal) {
    trailerModal.addEventListener('click', e => {
      if (e.target === trailerModal) closeTrailerModal();
    });
  }

  window.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
      closeTrailerModal();
      closeDrawer();
    }
  });

  if (iconWave) {
    const bubbles = [...iconWave.querySelectorAll('.icon-bubble')];
    bubbles.forEach((b, i) => b.style.setProperty('--idx', String(i)));

    const renderWave = (phase = 0) => {
      bubbles.forEach((b, i) => {
        const y = prefersReducedMotion ? 0 : Math.sin(i * 0.55 - phase) * 34;
        b.style.setProperty('--wave-y', `${y.toFixed(2)}px`);
      });
    };

    renderWave(0);
    if (!prefersReducedMotion && 'IntersectionObserver' in window) {
      let wavePhase = 0;
      let waveActive = false;
      let waveRaf = null;
      const tickWave = () => {
        wavePhase += 0.025;
        renderWave(wavePhase);
        if (waveActive) waveRaf = requestAnimationFrame(tickWave);
      };
      new IntersectionObserver(
        entries => {
          waveActive = entries[0].isIntersecting;
          if (waveActive) {
            iconWave.classList.add('in');
            if (!waveRaf) waveRaf = requestAnimationFrame(tickWave);
          } else if (waveRaf) {
            cancelAnimationFrame(waveRaf);
            waveRaf = null;
          }
        },
        { threshold: 0.05 }
      ).observe(iconWave);
    } else {
      iconWave.classList.add('in');
    }
  }

  const adaptiveVideos = [...document.querySelectorAll('video[data-src-high]')];
  const qualityToggleBtn = document.getElementById('videoQualityToggle');
  const qualityLabelEl = document.getElementById('videoQualityLabel');
  const TIER_ORDER = ['low', 'med', 'high'];
  const QUALITY_MODES = ['auto', 'high', 'med', 'low'];

  const createAdaptiveVideoController = () => {
    const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection || null;
    let mode = 'auto';
    let measuredMbps = null;
    let maxAutoTierIdx = 2; // 2=high, 1=med, 0=low
    let ceilingExpiresAt = 0;

    try {
      const savedMode = sessionStorage.getItem('system_video_quality');
      if (QUALITY_MODES.includes(savedMode)) mode = savedMode;
    } catch {}

    const recordTransferSample = entry => {
      if (!entry || entry.transferSize < 100000) return;
      const bodyMs =
        entry.responseStart > 0 && entry.responseEnd > entry.responseStart
          ? entry.responseEnd - entry.responseStart
          : entry.duration;
      if (bodyMs < 5) return;
      const mbps = (entry.transferSize * 8) / (bodyMs * 1000);
      if (!Number.isFinite(mbps) || mbps <= 0.05 || mbps > 2000) return;
      measuredMbps = measuredMbps === null ? mbps : measuredMbps * 0.65 + mbps * 0.35;
    };

    try {
      if (typeof performance !== 'undefined' && performance.getEntriesByType) {
        performance.getEntriesByType('resource').forEach(recordTransferSample);
      }
      if ('PerformanceObserver' in window) {
        const resObs = new PerformanceObserver(list => {
          let sawClip = false;
          list.getEntries().forEach(entry => {
            recordTransferSample(entry);
            if (entry.name && entry.name.includes('/clips/')) sawClip = true;
          });
          if (sawClip && mode === 'auto') {
            refreshQualityBadge();
          }
        });
        resObs.observe({ type: 'resource', buffered: false });
      }
    } catch {}

    const estimateBandwidthMbps = () => {
      if (conn && conn.saveData) return 0.6;
      const eff = conn && conn.effectiveType ? conn.effectiveType : '';
      if (eff === 'slow-2g' || eff === '2g') return 0.4;
      if (eff === '3g') return 1.4;

      const connDownlink = conn && typeof conn.downlink === 'number' && conn.downlink > 0 ? conn.downlink : null;
      if (measuredMbps !== null && connDownlink !== null) {
        return Math.min(measuredMbps, connDownlink * 1.25);
      }
      if (measuredMbps !== null) return measuredMbps;
      if (connDownlink !== null) return connDownlink;
      return 6.5;
    };

    const getAutoTierForVideo = video => {
      if (Date.now() > ceilingExpiresAt) maxAutoTierIdx = 2;

      const bw = estimateBandwidthMbps();
      let idx = bw >= 5.5 ? 2 : bw >= 2.0 ? 1 : 0;

      // Never stream 1080p into a small viewport/element whose physical pixels are below HD
      const rect = video ? video.getBoundingClientRect() : null;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const renderedPx = rect && rect.width > 0 ? rect.width * dpr : window.innerWidth * dpr;
      if (renderedPx < 540) idx = Math.min(idx, 0);
      else if (renderedPx < 960) idx = Math.min(idx, 1);

      return TIER_ORDER[Math.min(idx, maxAutoTierIdx)];
    };

    const resolveTier = video => (mode === 'auto' ? getAutoTierForVideo(video) : mode);

    const getTierLabel = (tier, video) => {
      const maxRes = (video && video.dataset.maxRes) || '1080p';
      if (tier === 'high') return maxRes;
      if (tier === 'med') return maxRes === '720p' ? '540p' : '720p';
      return '480p';
    };

    const getSrcForTier = (video, tier) => {
      if (!video) return '';
      if (tier === 'high') return video.dataset.srcHigh || '';
      if (tier === 'med') return video.dataset.srcMed || video.dataset.srcHigh || '';
      return video.dataset.srcLow || video.dataset.srcMed || video.dataset.srcHigh || '';
    };

    const refreshQualityBadge = activeVideo => {
      if (!qualityLabelEl) return;
      const refVideo =
        activeVideo ||
        document.querySelector('.carousel-slide.active video') ||
        adaptiveVideos[0] ||
        null;
      const activeTier = (refVideo && refVideo.dataset.activeTier) || resolveTier(refVideo);
      const resText = getTierLabel(activeTier, refVideo);
      qualityLabelEl.textContent = mode === 'auto' ? `Auto · ${resText}` : resText;
      if (qualityToggleBtn) {
        qualityToggleBtn.setAttribute(
          'aria-label',
          `Video quality: ${qualityLabelEl.textContent}. Click to switch.`
        );
      }
    };

    const applyTier = (video, tier, opts = {}) => {
      if (!video) return;
      const nextSrc = getSrcForTier(video, tier);
      if (!nextSrc) return;
      if (video.dataset.activeTier === tier && video.getAttribute('src') === nextSrc) return;

      const wasPlaying = !video.paused && !video.ended;
      const resumeAt = opts.preserveTime && Number.isFinite(video.currentTime) ? video.currentTime : 0;

      video.dataset.activeTier = tier;
      video.preload = 'metadata';
      video.setAttribute('src', nextSrc);
      video.load();

      if (resumeAt > 0.15) {
        const restoreTime = () => {
          try {
            if (Number.isFinite(video.duration) && resumeAt < video.duration - 0.1) {
              video.currentTime = resumeAt;
            }
          } catch {}
        };
        video.addEventListener('loadedmetadata', restoreTime, { once: true });
      }

      if (wasPlaying || opts.autoplay) {
        video.play().catch(() => {});
      }

      refreshQualityBadge();
    };

    const ensureLoaded = (video, opts = {}) => {
      if (!video) return;
      if (!video.getAttribute('src')) {
        applyTier(video, resolveTier(video), opts);
      } else if (opts.autoplay && video.paused && !video.dataset.manualPause) {
        video.play().catch(() => {});
      }
    };

    const stepDownVideo = video => {
      const curIdx = TIER_ORDER.indexOf(video.dataset.activeTier || 'high');
      if (curIdx <= 0) return;
      const nextIdx = curIdx - 1;
      maxAutoTierIdx = Math.min(maxAutoTierIdx, nextIdx);
      ceilingExpiresAt = Date.now() + 60000;
      applyTier(video, TIER_ORDER[nextIdx], { preserveTime: true, autoplay: !video.paused });
    };

    const bindStallMonitor = video => {
      let stallTimer = null;
      let recentStalls = [];

      const clearStall = () => {
        if (stallTimer) {
          clearTimeout(stallTimer);
          stallTimer = null;
        }
      };

      const onPotentialStall = () => {
        if (mode !== 'auto' || video.paused || video.readyState >= 3) return;
        clearStall();
        const now = Date.now();
        recentStalls = recentStalls.filter(ts => now - ts < 10000);
        recentStalls.push(now);

        const delay = recentStalls.length >= 2 ? 450 : 1200;
        stallTimer = setTimeout(() => {
          if (mode === 'auto' && !video.paused && video.readyState < 3) {
            stepDownVideo(video);
          }
        }, delay);
      };

      video.addEventListener('waiting', onPotentialStall);
      video.addEventListener('stalled', onPotentialStall);
      video.addEventListener('playing', clearStall);
      video.addEventListener('canplay', clearStall);
      video.addEventListener('pause', clearStall);
      video.addEventListener('error', () => {
        if (video.dataset.activeTier && video.dataset.activeTier !== 'low') {
          stepDownVideo(video);
        }
      });
    };

    adaptiveVideos.forEach(bindStallMonitor);

    const applyModeToLoadedVideos = () => {
      adaptiveVideos.forEach(video => {
        if (!video.getAttribute('src')) return;
        const targetTier = resolveTier(video);
        if (video.dataset.activeTier !== targetTier) {
          applyTier(video, targetTier, { preserveTime: true });
        }
      });
      refreshQualityBadge();
    };

    if (conn && typeof conn.addEventListener === 'function') {
      conn.addEventListener('change', () => {
        if (mode === 'auto') {
          maxAutoTierIdx = 2;
          ceilingExpiresAt = 0;
          applyModeToLoadedVideos();
        }
      });
    }

    if (qualityToggleBtn) {
      qualityToggleBtn.addEventListener('click', () => {
        const nextIdx = (QUALITY_MODES.indexOf(mode) + 1) % QUALITY_MODES.length;
        mode = QUALITY_MODES[nextIdx];
        if (mode === 'auto') {
          maxAutoTierIdx = 2;
          ceilingExpiresAt = 0;
        }
        try {
          sessionStorage.setItem('system_video_quality', mode);
        } catch {}
        applyModeToLoadedVideos();
      });
    }

    refreshQualityBadge();

    return {
      ensureLoaded,
      refreshQualityBadge
    };
  };

  const adaptiveMedia = createAdaptiveVideoController();

  const featureVideos = [...document.querySelectorAll('.feature-video')];
  if (featureVideos.length) {
    featureVideos.forEach(video => {
      const shell = video.closest('.feature-media');
      if (shell) {
        shell.addEventListener('click', () => {
          adaptiveMedia.ensureLoaded(video);
          if (video.paused) {
            delete video.dataset.manualPause;
            video.play().catch(() => {});
          } else {
            video.dataset.manualPause = '1';
            video.pause();
          }
        });
      }
    });

    if ('IntersectionObserver' in window) {
      const fvPreloadObs = new IntersectionObserver(
        (entries, self) => {
          entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            adaptiveMedia.ensureLoaded(entry.target);
            self.unobserve(entry.target);
          });
        },
        { rootMargin: '500px 0px', threshold: 0.01 }
      );
      featureVideos.forEach(v => fvPreloadObs.observe(v));

      const fvObs = new IntersectionObserver(
        entries => {
          entries.forEach(entry => {
            const v = entry.target;
            if (entry.isIntersecting) {
              adaptiveMedia.ensureLoaded(v);
              if (!v.dataset.manualPause) v.play().catch(() => {});
            } else {
              v.pause();
            }
          });
        },
        { threshold: 0.25 }
      );
      featureVideos.forEach(v => fvObs.observe(v));
    } else {
      featureVideos.forEach(v => adaptiveMedia.ensureLoaded(v));
    }
  }

  const carousel = document.getElementById('mediaCarousel');
  if (carousel) {
    const slides = [...carousel.querySelectorAll('.carousel-slide')];
    const titleEl = document.getElementById('carouselTitle');
    const metaTitleEl = document.getElementById('carouselMetaTitle');
    const counterEl = document.getElementById('carouselCounter');
    const playToggle = document.getElementById('carouselPlayToggle');
    const carouselCursor = document.getElementById('carouselCursor');
    const prevBtn = document.getElementById('carouselPrev');
    const nextBtn = document.getElementById('carouselNext');

    let currentIdx = 0;
    let carouselInView = false;
    let cancelTitleType = null;

    const preloadAdjacentSlide = idx => {
      const nextSlide = slides[(idx + 1) % slides.length];
      const nextVideo = nextSlide && nextSlide.querySelector('video');
      if (nextVideo) adaptiveMedia.ensureLoaded(nextVideo);
    };

    if (carouselCursor && finePointer) {
      carousel.addEventListener('mousemove', e => {
        const rect = carousel.getBoundingClientRect();
        carouselCursor.style.left = `${e.clientX - rect.left}px`;
        carouselCursor.style.top = `${e.clientY - rect.top}px`;
        carouselCursor.classList.add('is-visible');
        carousel.classList.add('has-cursor');
      });
      carousel.addEventListener('mouseleave', () => {
        carouselCursor.classList.remove('is-visible');
        carousel.classList.remove('has-cursor');
      });
    }

    const toggleActivePlay = () => {
      const activeVideo = slides[currentIdx].querySelector('video');
      if (!activeVideo) return;
      adaptiveMedia.ensureLoaded(activeVideo);
      if (activeVideo.paused) {
        activeVideo.play().catch(() => {});
        carousel.classList.remove('is-paused');
      } else {
        activeVideo.pause();
        carousel.classList.add('is-paused');
      }
    };

    const showSlide = idx => {
      const prevVideo = slides[currentIdx].querySelector('video');
      if (prevVideo) prevVideo.pause();
      slides[currentIdx].classList.remove('active');

      currentIdx = (idx + slides.length) % slides.length;
      const activeSlide = slides[currentIdx];
      activeSlide.classList.add('active');

      const title = activeSlide.dataset.title || '';
      if (cancelTitleType) cancelTitleType();
      if (titleEl) cancelTitleType = typeText(titleEl, title, 34);
      if (metaTitleEl) metaTitleEl.textContent = title;
      if (counterEl) counterEl.textContent = `${String(currentIdx + 1).padStart(2, '0')} / ${String(slides.length).padStart(2, '0')}`;

      const activeVideo = activeSlide.querySelector('video');
      carousel.classList.remove('is-paused');
      if (activeVideo) {
        adaptiveMedia.ensureLoaded(activeVideo);
        adaptiveMedia.refreshQualityBadge(activeVideo);
        try {
          activeVideo.currentTime = 0;
        } catch {}
        if (carouselInView) activeVideo.play().catch(() => {});
        activeVideo.addEventListener('canplay', () => preloadAdjacentSlide(currentIdx), { once: true });
      }
    };

    if (prevBtn) prevBtn.addEventListener('click', () => showSlide(currentIdx - 1));
    if (nextBtn) nextBtn.addEventListener('click', () => showSlide(currentIdx + 1));

    carousel.addEventListener('click', e => {
      if (e.target === playToggle || (playToggle && playToggle.contains(e.target))) return;
      toggleActivePlay();
    });
    if (playToggle) {
      playToggle.addEventListener('click', e => {
        e.stopPropagation();
        toggleActivePlay();
      });
    }

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(
        (entries, self) => {
          if (!entries[0].isIntersecting) return;
          const activeVideo = slides[currentIdx].querySelector('video');
          if (activeVideo) {
            adaptiveMedia.ensureLoaded(activeVideo);
            adaptiveMedia.refreshQualityBadge(activeVideo);
          }
          self.unobserve(carousel);
        },
        { rootMargin: '500px 0px', threshold: 0.01 }
      ).observe(carousel);

      new IntersectionObserver(
        entries => {
          carouselInView = entries[0].isIntersecting;
          const activeVideo = slides[currentIdx].querySelector('video');
          if (!activeVideo) return;
          if (carouselInView && !carousel.classList.contains('is-paused')) {
            adaptiveMedia.ensureLoaded(activeVideo);
            adaptiveMedia.refreshQualityBadge(activeVideo);
            activeVideo.play().catch(() => {});
            activeVideo.addEventListener('canplay', () => preloadAdjacentSlide(currentIdx), { once: true });
          } else {
            activeVideo.pause();
          }
        },
        { threshold: 0.2 }
      ).observe(carousel);
    } else {
      const activeVideo = slides[currentIdx].querySelector('video');
      if (activeVideo) adaptiveMedia.ensureLoaded(activeVideo);
    }
  }

  const belt = document.getElementById('belt');
  if (belt) {
    const fillBelt = () => {
      const seed = belt.firstElementChild;
      const run = seed.getBoundingClientRect().width;
      if (!run) return;
      const across = Math.max(document.documentElement.clientWidth, window.innerWidth || 0) || 1920;
      const want = Math.max(1, Math.ceil(across / run)) * 2;
      while (belt.children.length > want) belt.lastElementChild.remove();
      while (belt.children.length < want) {
        const copy = seed.cloneNode(true);
        copy.setAttribute('aria-hidden', 'true');
        copy.querySelectorAll('img').forEach(img => img.setAttribute('alt', ''));
        belt.appendChild(copy);
      }
    };
    fillBelt();
    let beltResizeTimer = null;
    window.addEventListener('resize', () => {
      clearTimeout(beltResizeTimer);
      beltResizeTimer = setTimeout(fillBelt, 180);
    });
  }

  const bypassStrip = document.getElementById('bypassStrip');
  const bypassPrev = document.getElementById('bypassPrev');
  const bypassNext = document.getElementById('bypassNext');
  if (bypassStrip && bypassPrev && bypassNext) {
    bypassPrev.addEventListener('click', () => bypassStrip.scrollBy({ left: -240, behavior: 'smooth' }));
    bypassNext.addEventListener('click', () => bypassStrip.scrollBy({ left: 240, behavior: 'smooth' }));
  }

  const downloadCounters = [...document.querySelectorAll('.download-count')];
  const versionEls = [...document.querySelectorAll('.release-version')];

  const updateCounters = total => {
    const formatted = total.toLocaleString();
    downloadCounters.forEach(el => {
      el.textContent = formatted;
    });
  };

  const formatReleaseTag = raw => {
    const tag = String(raw || '').trim();
    if (!tag) return '';
    return /^\d/.test(tag) ? `b${tag}` : tag;
  };

  const applyReleaseVersion = version => {
    if (!version) return;
    versionEls.forEach(el => {
      el.textContent = version;
    });
    document.title = `System Client ${version} | Minecraft 1.21.11 Fabric Client`;
  };

  try {
    const savedDownloads = sessionStorage.getItem('system_repo_downloads');
    if (savedDownloads && !isNaN(parseInt(savedDownloads, 10))) {
      updateCounters(parseInt(savedDownloads, 10));
    }
    const savedVersion = sessionStorage.getItem('system_repo_latest_version');
    if (savedVersion) {
      applyReleaseVersion(savedVersion);
    }
  } catch {}

  const fetchLatestTagFallback = () =>
    fetch('https://api.github.com/repos/daduncs/system/releases/latest', { cache: 'no-store' })
      .then(r => (r.ok ? r.json() : Promise.reject()))
      .then(rel => {
        const version = formatReleaseTag(rel && (rel.tag_name || rel.name));
        if (!version) return;
        applyReleaseVersion(version);
        try {
          sessionStorage.setItem('system_repo_latest_version', version);
        } catch {}
      })
      .catch(() => {});

  fetch('https://api.github.com/repos/daduncs/system/releases', { cache: 'no-store' })
    .then(r => (r.ok ? r.json() : Promise.reject()))
    .then(releases => {
      if (!Array.isArray(releases)) return fetchLatestTagFallback();

      const total = releases.reduce(
        (sum, rel) => sum + (rel.assets || []).reduce((n, asset) => n + (asset.download_count || 0), 0),
        0
      );
      if (typeof total === 'number' && !isNaN(total) && total > 0) {
        updateCounters(total);
        try {
          sessionStorage.setItem('system_repo_downloads', String(total));
        } catch {}
      }

      const latest = releases.find(r => !r.draft && !r.prerelease) || releases[0];
      const version = formatReleaseTag(latest && (latest.tag_name || latest.name));
      if (version) {
        applyReleaseVersion(version);
        try {
          sessionStorage.setItem('system_repo_latest_version', version);
        } catch {}
      } else {
        return fetchLatestTagFallback();
      }
    })
    .catch(() => fetchLatestTagFallback());
});
