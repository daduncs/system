document.addEventListener('DOMContentLoaded', () => {

  const nav = document.getElementById('nav');
  const burger = document.getElementById('burger');
  const drawer = document.getElementById('drawer');
  const onScroll = () => nav.classList.toggle('stuck', window.scrollY > 24);

  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  const shut = () => {
    burger.classList.remove('open');
    drawer.classList.remove('open');
    burger.setAttribute('aria-expanded', 'false');
  };

  burger.addEventListener('click', () => {
    const open = drawer.classList.toggle('open');
    burger.classList.toggle('open', open);
    burger.setAttribute('aria-expanded', String(open));
  });

  drawer.querySelectorAll('a').forEach(a => a.addEventListener('click', shut));
  window.addEventListener('keydown', e => e.key === 'Escape' && shut());
  window.addEventListener('resize', () => window.innerWidth > 860 && shut());

  const reveals = document.querySelectorAll('.reveal');

  if ('IntersectionObserver' in window) {
    const watcher = new IntersectionObserver((entries, self) => {
      entries.forEach((entry, i) => {
        if (!entry.isIntersecting) return;

        setTimeout(() => entry.target.classList.add('in'), i * 60);
        self.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' });

    reveals.forEach(el => watcher.observe(el));
  } else {
    reveals.forEach(el => el.classList.add('in'));
  }

  const fill = track => {
    const seed = track.firstElementChild;
    const run = seed.getBoundingClientRect().width;

    if (!run) return;

    const across = Math.max(document.documentElement.clientWidth, window.innerWidth || 0) || 1920;
    const want = Math.max(1, Math.ceil(across / run)) * 2;

    while (track.children.length > want) track.lastElementChild.remove();

    while (track.children.length < want) {
      const copy = seed.cloneNode(true);

      copy.setAttribute('aria-hidden', 'true');
      copy.querySelectorAll('img').forEach(img => img.setAttribute('alt', ''));
      copy.querySelectorAll('video').forEach(v => v.setAttribute('preload', 'metadata'));

      track.appendChild(copy);
    }
  };

  const belt = document.getElementById('belt');
  const reelTrack = document.getElementById('reelTrack');

  let reels = [];

  const bind = () => {
    reels = [...document.querySelectorAll('.reel')];

    reels.forEach(reel => {
      if (reel.dataset.bound) return;

      reel.dataset.bound = '1';

      reel.addEventListener('click', () => {
        if (reel.paused) {
          delete reel.dataset.manualPause;
          reel.play().catch(() => {});
        } else {
          reel.dataset.manualPause = '1';
          reel.pause();
        }
      });

      reel.addEventListener('ended', () => {
        reel.currentTime = 0;
        reel.play().catch(() => {});
      });
    });

    document.querySelectorAll('.embed').forEach(key => {
      if (key.dataset.bound) return;

      key.dataset.bound = '1';

      key.addEventListener('click', () => {
        const frame = document.createElement('iframe');

        frame.src = key.dataset.embed;
        frame.title = 'Trailer';
        frame.allow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture';
        frame.allowFullscreen = true;

        key.replaceWith(frame);
      });
    });
  };

  const lay = () => {
    fill(belt);
    fill(reelTrack);
    bind();
  };

  lay();

  let sized = null;

  window.addEventListener('resize', () => {
    clearTimeout(sized);
    sized = setTimeout(lay, 180);
  });

  const reelBelt = document.querySelector('.reel-belt');

  let watching = false;
  let ticker = null;

  const syncPlayback = () => {
    const strip = reelBelt.getBoundingClientRect();
    const viewWidth = window.innerWidth || document.documentElement.clientWidth;
    const viewHeight = window.innerHeight || document.documentElement.clientHeight;

    const beltVisible = strip.bottom > 0 && strip.top < viewHeight;
    if (!beltVisible) {
      reels.forEach(reel => {
        if (!reel.paused) reel.pause();
      });
      return;
    }

    reels.forEach(reel => {
      const box = reel.getBoundingClientRect();
      const onScreen = box.right > 0 && box.left < viewWidth;

      if (onScreen) {
        if (reel.paused && !reel.dataset.manualPause) {
          reel.play().catch(() => {});
        }
      } else {
        delete reel.dataset.manualPause;
        if (!reel.paused) {
          reel.pause();
          reel.currentTime = 0;
        }
      }
    });
  };

  const watch = on => {
    if (on === watching) return;

    watching = on;

    if (on) {
      syncPlayback();
      ticker = setInterval(syncPlayback, 120);
    } else {
      clearInterval(ticker);
      reels.forEach(reel => {
        if (!reel.paused) reel.pause();
      });
    }
  };

  if ('IntersectionObserver' in window) {
    new IntersectionObserver(e => watch(e[0].isIntersecting), { threshold: 0.05 }).observe(reelBelt);
  } else {
    watch(true);
  }

  window.addEventListener('scroll', syncPlayback, { passive: true });

  const count = document.getElementById('downloadCount');
  const saved = sessionStorage.getItem('system_repo_downloads');

  if (saved) {
    count.textContent = parseInt(saved, 10).toLocaleString();
  } else {
    fetch('https://api.github.com/repos/daduncs/system/releases')
      .then(r => r.ok ? r.json() : Promise.reject())
      .then(releases => {
        if (!Array.isArray(releases)) return;

        const total = releases.reduce((sum, rel) => sum + (rel.assets || [])
          .reduce((n, asset) => n + (asset.download_count || 0), 0), 0);

        if (!total) return;

        count.textContent = total.toLocaleString();
        sessionStorage.setItem('system_repo_downloads', String(total));
      })
      .catch(() => {});
  }

  try {
    localStorage.removeItem('system_theme_accent');
  } catch (e) {}

});
