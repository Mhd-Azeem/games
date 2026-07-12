// UI module — all menus, HUD, overlays, touch controls
const UI = (() => {
  let touchSteerLeft = false;
  let touchSteerRight = false;
  let wheelActive = false;
  let wheelCenterX = 0;
  let wheelDragX = 0;
  let minimapCanvas = null;
  let minimapCtx = null;
  let currentTrackWaypoints = [];
  let hudVisible = false;

  // ── Helpers ──────────────────────────────────────────────────────────────────

  function el(id) { return document.getElementById(id); }

  function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.add('hidden'));
    const s = el(id);
    if (s) s.classList.remove('hidden');
  }

  function hideAllScreens() {
    document.querySelectorAll('.screen').forEach(s => s.classList.add('hidden'));
  }

  // ── Loading screen ────────────────────────────────────────────────────────────

  function showLoading(pct, label) {
    showScreen('screen-loading');
    const bar = el('loading-bar-inner');
    const txt = el('loading-text');
    if (bar) bar.style.width = pct + '%';
    if (txt) txt.textContent = label || 'Loading…';
  }

  function hideLoading() {
    const s = el('screen-loading');
    if (s) s.classList.add('hidden');
  }

  // ── Main Menu ────────────────────────────────────────────────────────────────

  function showMainMenu(callbacks) {
    showScreen('screen-main-menu');
    hudVisible = false;
    el('btn-free').onclick = () => callbacks.freeMode && callbacks.freeMode();
    el('btn-race').onclick = () => callbacks.raceMode && callbacks.raceMode();
    el('btn-map-select').onclick = () => callbacks.mapSelect && callbacks.mapSelect();
    el('btn-settings').onclick = () => callbacks.settings && callbacks.settings();
    el('btn-exit').onclick = () => callbacks.exit && callbacks.exit();
  }

  // ── Map Select ───────────────────────────────────────────────────────────────

  function showMapSelect(trackList, currentTrack, callbacks) {
    showScreen('screen-map-select');
    const grid = el('map-grid');
    grid.innerHTML = '';
    trackList.forEach(t => {
      const card = document.createElement('div');
      card.className = 'map-card' + (t.id === currentTrack ? ' selected' : '');
      card.innerHTML = `
        <div class="map-preview" style="border-color:${t.color}">
          <div class="map-icon" style="color:${t.color}">🏁</div>
          <div class="map-name">${t.name}</div>
        </div>
        <div class="map-desc">${t.desc}</div>`;
      card.onclick = () => {
        document.querySelectorAll('.map-card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        if (callbacks.select) callbacks.select(t.id);
      };
      grid.appendChild(card);
    });
    el('btn-map-back').onclick = () => callbacks.back && callbacks.back();
  }

  // ── Settings Screen ──────────────────────────────────────────────────────────

  function showSettings(callbacks) {
    showScreen('screen-settings');
    const s = Settings.getAll();

    // Graphics quality
    document.querySelectorAll('.btn-quality').forEach(b => {
      b.classList.toggle('active', b.dataset.value === s.graphicsQuality);
      b.onclick = () => {
        Settings.set('graphicsQuality', b.dataset.value);
        document.querySelectorAll('.btn-quality').forEach(x => x.classList.remove('active'));
        b.classList.add('active');
      };
    });

    // Volume sliders
    const mkSlider = (id, key) => {
      const sl = el(id);
      if (!sl) return;
      sl.value = Settings.get(key) * 100;
      sl.oninput = () => {
        Settings.set(key, sl.value / 100);
        Audio.setVolumes(
          Settings.get('masterVolume'),
          Settings.get('engineVolume'),
          Settings.get('musicVolume'),
        );
      };
    };
    mkSlider('sl-master', 'masterVolume');
    mkSlider('sl-engine', 'engineVolume');
    mkSlider('sl-music', 'musicVolume');

    // Mute toggle
    const muteBtn = el('btn-mute');
    if (muteBtn) {
      muteBtn.textContent = s.muted ? '🔇 Unmute' : '🔊 Mute';
      muteBtn.onclick = () => {
        const m = !Settings.get('muted');
        Settings.set('muted', m);
        Audio.setMuted(m);
        muteBtn.textContent = m ? '🔇 Unmute' : '🔊 Mute';
      };
    }

    // Control scheme
    document.querySelectorAll('.btn-ctrl').forEach(b => {
      b.classList.toggle('active', b.dataset.value === s.controlScheme);
      b.onclick = () => {
        Settings.set('controlScheme', b.dataset.value);
        document.querySelectorAll('.btn-ctrl').forEach(x => x.classList.remove('active'));
        b.classList.add('active');
      };
    });

    // Camera view
    document.querySelectorAll('.btn-cam').forEach(b => {
      b.classList.toggle('active', b.dataset.value === s.cameraView);
      b.onclick = () => {
        Settings.set('cameraView', b.dataset.value);
        document.querySelectorAll('.btn-cam').forEach(x => x.classList.remove('active'));
        b.classList.add('active');
      };
    });

    // Invert steering
    const invBtn = el('btn-invert');
    if (invBtn) {
      invBtn.classList.toggle('active', s.invertSteering);
      invBtn.onclick = () => {
        const v = !Settings.get('invertSteering');
        Settings.set('invertSteering', v);
        invBtn.classList.toggle('active', v);
      };
    }

    // Reset
    const resetBtn = el('btn-reset-settings');
    if (resetBtn) {
      resetBtn.onclick = () => {
        Settings.reset();
        showSettings(callbacks);
      };
    }

    el('btn-settings-back').onclick = () => callbacks.back && callbacks.back();
  }

  // ── HUD ──────────────────────────────────────────────────────────────────────

  function showHUD(mode, trackName, lapTotal) {
    hudVisible = true;
    el('hud').classList.remove('hidden');
    el('hud-track').textContent = trackName || '';
    el('hud-mode').textContent = mode === 'race' ? 'RACE' : 'FREE';
    el('hud-lap-label').style.display = mode === 'race' ? '' : 'none';
    el('hud-timer-label').style.display = mode === 'race' ? '' : 'none';
  }

  function hideHUD() {
    hudVisible = false;
    el('hud').classList.add('hidden');
  }

  function updateHUD(data) {
    if (!hudVisible) return;
    // Speedometer
    const spd = Math.abs(Math.round(data.speed || 0));
    el('hud-speed').textContent = spd;
    const needle = el('speedo-needle');
    if (needle) {
      const angle = -135 + (spd / 220) * 270;
      needle.style.transform = `rotate(${angle}deg)`;
    }
    // Lap
    if (data.lap !== undefined) {
      el('hud-lap').textContent = `${data.lap}/${data.totalLaps}`;
    }
    // Timer
    if (data.lapTime !== undefined) {
      el('hud-timer').textContent = formatTime(data.lapTime);
    }
    // Best lap
    if (data.bestLap !== undefined && data.bestLap < Infinity) {
      el('hud-best').textContent = formatTime(data.bestLap);
    }
    // Position
    if (data.position !== undefined) {
      const pos = data.position;
      el('hud-pos').textContent = pos + getOrdinal(pos);
    }
    // Gear indicator (fake)
    const gear = data.speed < 0 ? 'R' : data.speed < 40 ? '1' : data.speed < 80 ? '2' : data.speed < 120 ? '3' : data.speed < 160 ? '4' : '5';
    const gearEl = el('hud-gear');
    if (gearEl) gearEl.textContent = gear;

    // Drift indicator
    const driftEl = el('hud-drift');
    if (driftEl) driftEl.style.opacity = data.drifting ? '1' : '0';
  }

  function updateMinimap(playerPos, aiAgents, trackWaypoints) {
    if (!minimapCtx || !trackWaypoints || trackWaypoints.length < 2) return;
    const ctx = minimapCtx;
    const w = minimapCanvas.width;
    const h = minimapCanvas.height;

    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = 'rgba(0,0,0,0.6)';
    ctx.fillRect(0, 0, w, h);

    if (!trackWaypoints.length) return;

    // Compute track bounds
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    trackWaypoints.forEach(wp => {
      minX = Math.min(minX, wp.x); maxX = Math.max(maxX, wp.x);
      minZ = Math.min(minZ, wp.z); maxZ = Math.max(maxZ, wp.z);
    });
    const pad = 10;
    const scaleX = (w - pad * 2) / (maxX - minX + 1);
    const scaleZ = (h - pad * 2) / (maxZ - minZ + 1);
    const scale = Math.min(scaleX, scaleZ);

    const toCanvas = (x, z) => ({
      x: pad + (x - minX) * scale,
      y: pad + (z - minZ) * scale,
    });

    // Draw track
    ctx.beginPath();
    ctx.strokeStyle = 'rgba(255,255,255,0.4)';
    ctx.lineWidth = 3;
    trackWaypoints.forEach((wp, i) => {
      const p = toCanvas(wp.x, wp.z);
      if (i === 0) ctx.moveTo(p.x, p.y);
      else ctx.lineTo(p.x, p.y);
    });
    ctx.closePath();
    ctx.stroke();

    // Draw AI blips
    if (aiAgents) {
      aiAgents.forEach(a => {
        const p = toCanvas(a.phys.position.x, a.phys.position.z);
        ctx.beginPath();
        ctx.arc(p.x, p.y, 3, 0, Math.PI * 2);
        ctx.fillStyle = '#' + new THREE.Color(a.color).getHexString();
        ctx.fill();
      });
    }

    // Draw player blip
    if (playerPos) {
      const p = toCanvas(playerPos.x, playerPos.z);
      ctx.beginPath();
      ctx.arc(p.x, p.y, 5, 0, Math.PI * 2);
      ctx.fillStyle = '#00f0ff';
      ctx.fill();
      ctx.beginPath();
      ctx.arc(p.x, p.y, 5, 0, Math.PI * 2);
      ctx.strokeStyle = 'white';
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

  // ── Touch Controls ────────────────────────────────────────────────────────────

  function showTouchControls(scheme) {
    const tc = el('touch-controls');
    if (!tc) return;
    tc.innerHTML = '';
    tc.classList.remove('hidden');

    if (scheme === 'buttons') {
      buildButtonControls(tc);
    } else if (scheme === 'wheel') {
      buildWheelControls(tc);
    } else {
      // tilt: only show throttle/brake
      buildTiltControls(tc);
    }

    // Pause button always shown
    const pauseBtn = document.createElement('button');
    pauseBtn.id = 'btn-pause-game';
    pauseBtn.className = 'touch-btn pause-btn';
    pauseBtn.textContent = '⏸';
    pauseBtn.ontouchstart = (e) => {
      e.preventDefault();
      Controls.getState().pausePressed = true;
    };
    pauseBtn.onclick = () => { Controls.getState().pausePressed = true; };
    tc.appendChild(pauseBtn);

    // Camera toggle
    const camBtn = document.createElement('button');
    camBtn.id = 'btn-cam-toggle';
    camBtn.className = 'touch-btn cam-btn';
    camBtn.textContent = '📷';
    camBtn.ontouchstart = (e) => {
      e.preventDefault();
      Controls.getState().cameraPressed = true;
    };
    camBtn.onclick = () => { Controls.getState().cameraPressed = true; };
    tc.appendChild(camBtn);
  }

  function buildButtonControls(tc) {
    // Left side: steer left/right
    const leftCluster = document.createElement('div');
    leftCluster.className = 'steer-cluster';

    const btnL = mkTouchBtn('◄', 'steer-left-btn');
    const btnR = mkTouchBtn('►', 'steer-right-btn');

    let leftHeld = false, rightHeld = false;

    const trackTouch = (btn, onStart, onEnd) => {
      btn.addEventListener('touchstart', e => { e.preventDefault(); onStart(); }, { passive: false });
      btn.addEventListener('touchend', e => { e.preventDefault(); onEnd(); }, { passive: false });
      btn.addEventListener('touchcancel', e => { e.preventDefault(); onEnd(); }, { passive: false });
      btn.addEventListener('mousedown', onStart);
      btn.addEventListener('mouseup', onEnd);
      btn.addEventListener('mouseleave', onEnd);
    };

    trackTouch(btnL,
      () => { leftHeld = true; updateSteerBtns(); },
      () => { leftHeld = false; updateSteerBtns(); },
    );
    trackTouch(btnR,
      () => { rightHeld = true; updateSteerBtns(); },
      () => { rightHeld = false; updateSteerBtns(); },
    );

    function updateSteerBtns() {
      if (leftHeld && !rightHeld) Controls.setTouchSteering(-1);
      else if (rightHeld && !leftHeld) Controls.setTouchSteering(1);
      else Controls.setTouchSteering(0);
    }

    leftCluster.appendChild(btnL);
    leftCluster.appendChild(btnR);
    tc.appendChild(leftCluster);

    // Right side: throttle, brake, handbrake
    const rightCluster = document.createElement('div');
    rightCluster.className = 'pedal-cluster';

    const btnThrottle = mkTouchBtn('▲', 'throttle-btn');
    const btnBrake = mkTouchBtn('▼', 'brake-btn');
    const btnHB = mkTouchBtn('E-BRAKE', 'handbrake-btn');

    const pedal = (btn, key) => {
      btn.addEventListener('touchstart', e => { e.preventDefault(); Controls.setButtonState(key, true); }, { passive: false });
      btn.addEventListener('touchend', e => { e.preventDefault(); Controls.setButtonState(key, false); }, { passive: false });
      btn.addEventListener('touchcancel', e => { e.preventDefault(); Controls.setButtonState(key, false); }, { passive: false });
      btn.addEventListener('mousedown', () => Controls.setButtonState(key, true));
      btn.addEventListener('mouseup', () => Controls.setButtonState(key, false));
      btn.addEventListener('mouseleave', () => Controls.setButtonState(key, false));
    };
    pedal(btnThrottle, 'throttle');
    pedal(btnBrake, 'brake');
    pedal(btnHB, 'handbrake');

    rightCluster.appendChild(btnThrottle);
    rightCluster.appendChild(btnBrake);
    rightCluster.appendChild(btnHB);
    tc.appendChild(rightCluster);
  }

  function buildWheelControls(tc) {
    const wheel = document.createElement('div');
    wheel.id = 'steering-wheel';
    wheel.innerHTML = `<div class="wheel-inner"></div>`;
    tc.appendChild(wheel);

    let wheelRot = 0;
    let dragging = false;
    let lastX = 0;

    wheel.addEventListener('touchstart', e => {
      e.preventDefault();
      dragging = true;
      lastX = e.touches[0].clientX;
    }, { passive: false });
    window.addEventListener('touchmove', e => {
      if (!dragging) return;
      e.preventDefault();
      const dx = e.touches[0].clientX - lastX;
      lastX = e.touches[0].clientX;
      wheelRot = Math.max(-150, Math.min(150, wheelRot + dx * 0.8));
      wheel.style.transform = `rotate(${wheelRot}deg)`;
      Controls.setTouchSteering(wheelRot / 150);
    }, { passive: false });
    window.addEventListener('touchend', e => {
      if (!dragging) return;
      dragging = false;
      // Return to center
      const returnInterval = setInterval(() => {
        if (dragging) { clearInterval(returnInterval); return; }
        wheelRot *= 0.82;
        Controls.setTouchSteering(wheelRot / 150);
        wheel.style.transform = `rotate(${wheelRot}deg)`;
        if (Math.abs(wheelRot) < 1) { wheelRot = 0; clearInterval(returnInterval); }
      }, 16);
    });

    // Right side pedals
    const rightCluster = document.createElement('div');
    rightCluster.className = 'pedal-cluster';
    const btnThrottle = mkTouchBtn('▲', 'throttle-btn');
    const btnBrake = mkTouchBtn('▼', 'brake-btn');
    const pedal = (btn, key) => {
      btn.addEventListener('touchstart', e => { e.preventDefault(); Controls.setButtonState(key, true); }, { passive: false });
      btn.addEventListener('touchend', e => { e.preventDefault(); Controls.setButtonState(key, false); }, { passive: false });
      btn.addEventListener('touchcancel', e => { e.preventDefault(); Controls.setButtonState(key, false); }, { passive: false });
      btn.addEventListener('mousedown', () => Controls.setButtonState(key, true));
      btn.addEventListener('mouseup', () => Controls.setButtonState(key, false));
      btn.addEventListener('mouseleave', () => Controls.setButtonState(key, false));
    };
    pedal(btnThrottle, 'throttle');
    pedal(btnBrake, 'brake');
    rightCluster.appendChild(btnThrottle);
    rightCluster.appendChild(btnBrake);
    tc.appendChild(rightCluster);
  }

  function buildTiltControls(tc) {
    const tiltInfo = document.createElement('div');
    tiltInfo.className = 'tilt-info';
    tiltInfo.textContent = 'Tilt device to steer';
    tc.appendChild(tiltInfo);

    const rightCluster = document.createElement('div');
    rightCluster.className = 'pedal-cluster';
    const btnThrottle = mkTouchBtn('▲', 'throttle-btn');
    const btnBrake = mkTouchBtn('▼', 'brake-btn');
    const pedal = (btn, key) => {
      btn.addEventListener('touchstart', e => { e.preventDefault(); Controls.setButtonState(key, true); }, { passive: false });
      btn.addEventListener('touchend', e => { e.preventDefault(); Controls.setButtonState(key, false); }, { passive: false });
      btn.addEventListener('touchcancel', e => { e.preventDefault(); Controls.setButtonState(key, false); }, { passive: false });
    };
    pedal(btnThrottle, 'throttle');
    pedal(btnBrake, 'brake');
    rightCluster.appendChild(btnThrottle);
    rightCluster.appendChild(btnBrake);
    tc.appendChild(rightCluster);
  }

  function mkTouchBtn(label, className) {
    const b = document.createElement('button');
    b.className = 'touch-btn ' + className;
    b.textContent = label;
    return b;
  }

  function hideTouchControls() {
    const tc = el('touch-controls');
    if (tc) { tc.innerHTML = ''; tc.classList.add('hidden'); }
  }

  // ── Countdown ────────────────────────────────────────────────────────────────

  function showCountdown(n, cb) {
    const overlay = el('countdown-overlay');
    overlay.classList.remove('hidden');
    const txt = el('countdown-text');
    let count = n;

    function tick() {
      if (count > 0) {
        txt.textContent = count;
        txt.classList.remove('pulse');
        void txt.offsetWidth;
        txt.classList.add('pulse');
        Audio.playCountdown(count > 1 ? 1 : 0);
        count--;
        setTimeout(tick, 1000);
      } else {
        txt.textContent = 'GO!';
        txt.classList.remove('pulse');
        void txt.offsetWidth;
        txt.classList.add('pulse');
        Audio.playCountdown(0);
        setTimeout(() => {
          overlay.classList.add('hidden');
          if (cb) cb();
        }, 800);
      }
    }
    tick();
  }

  // ── Pause Menu ───────────────────────────────────────────────────────────────

  function showPause(callbacks) {
    el('screen-pause').classList.remove('hidden');
    el('btn-resume').onclick = () => {
      el('screen-pause').classList.add('hidden');
      if (callbacks.resume) callbacks.resume();
    };
    el('btn-restart').onclick = () => {
      el('screen-pause').classList.add('hidden');
      if (callbacks.restart) callbacks.restart();
    };
    el('btn-pause-settings').onclick = () => {
      el('screen-pause').classList.add('hidden');
      showSettings({ back: () => { el('screen-pause').classList.remove('hidden'); } });
    };
    el('btn-quit').onclick = () => {
      el('screen-pause').classList.add('hidden');
      if (callbacks.quit) callbacks.quit();
    };
  }

  function hidePause() {
    const s = el('screen-pause');
    if (s) s.classList.add('hidden');
  }

  // ── Race Results ─────────────────────────────────────────────────────────────

  function showRaceResults(results, callbacks) {
    showScreen('screen-results');
    const tbody = el('results-tbody');
    if (tbody) {
      tbody.innerHTML = '';
      results.forEach((r, i) => {
        const tr = document.createElement('tr');
        tr.className = i === 0 ? 'result-first' : '';
        tr.innerHTML = `
          <td>${i + 1}</td>
          <td style="color:${r.isPlayer ? '#00f0ff' : 'white'}">${r.isPlayer ? 'YOU' : r.name}</td>
          <td>${formatTime(r.totalTime)}</td>
          <td>${formatTime(r.bestLap)}</td>`;
        tbody.appendChild(tr);
      });
    }
    el('btn-results-restart').onclick = () => callbacks.restart && callbacks.restart();
    el('btn-results-menu').onclick = () => callbacks.menu && callbacks.menu();
  }

  // ── Checkpoint flash ──────────────────────────────────────────────────────────

  function flashCheckpoint(text) {
    const el2 = el('checkpoint-flash');
    if (!el2) return;
    el2.textContent = text || 'CHECKPOINT';
    el2.classList.remove('flash-anim');
    void el2.offsetWidth;
    el2.classList.add('flash-anim');
  }

  function flashLap(lapNum, time) {
    const el2 = el('lap-flash');
    if (!el2) return;
    el2.innerHTML = `LAP ${lapNum}<br><span class="lap-time">${formatTime(time)}</span>`;
    el2.classList.remove('flash-anim');
    void el2.offsetWidth;
    el2.classList.add('flash-anim');
  }

  // ── Exit confirm ──────────────────────────────────────────────────────────────

  function showExitConfirm(callbacks) {
    el('screen-exit').classList.remove('hidden');
    el('btn-exit-yes').onclick = () => {
      el('screen-exit').classList.add('hidden');
      if (callbacks.yes) callbacks.yes();
    };
    el('btn-exit-no').onclick = () => {
      el('screen-exit').classList.add('hidden');
      if (callbacks.no) callbacks.no();
    };
  }

  // ── Minimap init ──────────────────────────────────────────────────────────────

  function initMinimap(waypoints) {
    currentTrackWaypoints = waypoints || [];
    minimapCanvas = el('minimap-canvas');
    if (!minimapCanvas) return;
    minimapCtx = minimapCanvas.getContext('2d');
  }

  // ── Utils ─────────────────────────────────────────────────────────────────────

  function formatTime(secs) {
    if (!isFinite(secs)) return '--:--.---';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    const ms = Math.floor((secs % 1) * 1000);
    return `${m}:${String(s).padStart(2, '0')}.${String(ms).padStart(3, '0')}`;
  }

  function getOrdinal(n) {
    const s = ['th', 'st', 'nd', 'rd'];
    const v = n % 100;
    return s[(v - 20) % 10] || s[v] || s[0];
  }

  return {
    showLoading, hideLoading,
    showMainMenu,
    showMapSelect,
    showSettings,
    showHUD, hideHUD, updateHUD, updateMinimap, initMinimap,
    showTouchControls, hideTouchControls,
    showCountdown,
    showPause, hidePause,
    showRaceResults,
    flashCheckpoint, flashLap,
    showExitConfirm,
    showScreen, hideAllScreens,
    formatTime,
  };
})();
