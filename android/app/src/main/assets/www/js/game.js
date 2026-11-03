// Main game orchestrator — state machine, render loop, game modes
const Game = (() => {
  const STATE = {
    LOADING: 'loading',
    MAIN_MENU: 'main_menu',
    MAP_SELECT: 'map_select',
    SETTINGS: 'settings',
    COUNTDOWN: 'countdown',
    PLAYING: 'playing',
    PAUSED: 'paused',
    RESULTS: 'results',
  };

  let state = STATE.LOADING;
  let renderer, scene, camera;
  let playerMesh, playerPhys;
  let trackData = null;
  let aiAgents = [];
  let checkpointMeshes = [];
  let mode = 'race'; // 'race' | 'free'
  let currentTrackId = 'city';
  let animFrameId = null;
  let lastTime = 0;

  // Race state
  let raceStarted = false;
  let raceTimer = 0;
  let lapTimer = 0;
  let currentLap = 1;
  let totalLaps = 3;
  let currentCheckpoint = 0;
  let bestLap = Infinity;
  let lapTimes = [];
  let playerFinished = false;

  // Camera
  const cameraViews = ['chase', 'cockpit', 'hood'];
  let cameraViewIdx = 0;
  const chaseOffset = new THREE.Vector3(0, 6, -14);
  const chaseTarget = new THREE.Vector3(0, 1, 4);

  // Best lap storage key
  const bestLapKey = () => `bestLap_${currentTrackId}`;

  // ── Init Three.js ──────────────────────────────────────────────────────────

  function initRenderer() {
    const canvas = document.getElementById('game-canvas');
    const quality = Settings.get('graphicsQuality');

    // Check WebGL support before attempting to create the renderer
    const testCtx = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    if (!testCtx) {
      document.body.innerHTML = '<div style="color:#fff;font-size:24px;text-align:center;padding:40px;background:#000;height:100vh;display:flex;align-items:center;justify-content:center;">WebGL is not supported on this device.</div>';
      throw new Error('WebGL not supported');
    }

    try {
      renderer = new THREE.WebGLRenderer({
        canvas,
        antialias: quality === 'high',
        powerPreference: 'default',
        failIfMajorPerformanceCaveat: false,
      });
    } catch (e) {
      document.body.innerHTML = '<div style="color:#fff;font-size:20px;text-align:center;padding:40px;background:#000;height:100vh;display:flex;align-items:center;justify-content:center;">Failed to initialize 3D renderer. Please restart the app.</div>';
      throw e;
    }

    renderer.setPixelRatio(Math.min(window.devicePixelRatio, quality === 'high' ? 2 : 1.5));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.shadowMap.enabled = quality !== 'low';
    renderer.shadowMap.type = quality === 'high' ? THREE.PCFSoftShadowMap : THREE.PCFShadowMap;
    renderer.outputColorSpace = THREE.SRGBColorSpace;

    camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.1, 1200);

    window.addEventListener('resize', () => {
      renderer.setSize(window.innerWidth, window.innerHeight);
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
    });
  }

  // ── Load/build a scene ─────────────────────────────────────────────────────

  async function loadScene(trackId, gameMode) {
    UI.showLoading(0, 'Initializing…');
    state = STATE.LOADING;
    currentTrackId = trackId;
    mode = gameMode;

    // Dispose old scene
    if (scene) disposeScene();
    scene = new THREE.Scene();

    await tick(5, 'Building track…');
    const quality = Settings.get('graphicsQuality');
    trackData = Tracks.build(trackId, scene, quality);
    totalLaps = trackData.lapCount;

    await tick(40, 'Spawning cars…');
    // Player car
    playerMesh = Car.buildMesh(scene, 0x00f0ff, true);
    playerPhys = Car.createPhysics(trackData.startPosition, trackData.startHeading);

    await tick(60, 'Setting up AI…');
    if (mode === 'race') {
      aiAgents = AI.createAllAgents(scene, trackData.waypoints);
    }

    await tick(75, 'Building checkpoints…');
    buildCheckpoints();

    await tick(90, 'Setting up controls…');
    const scheme = Settings.get('controlScheme');
    const invert = Settings.get('invertSteering');
    Controls.init(scheme, invert);
    UI.showTouchControls(scheme);

    await tick(95, 'Setting up audio…');
    Audio.init(
      Settings.get('masterVolume'),
      Settings.get('engineVolume'),
      Settings.get('musicVolume'),
      Settings.get('muted'),
    );

    await tick(100, 'Ready!');

    // Set camera view from settings
    const camPref = Settings.get('cameraView');
    cameraViewIdx = cameraViews.indexOf(camPref);
    if (cameraViewIdx < 0) cameraViewIdx = 0;

    await sleep(300);
    UI.hideLoading();

    // Init minimap
    UI.initMinimap(trackData.waypoints);

    // Show HUD
    UI.showHUD(mode, trackData.name, totalLaps);

    // Reset race state
    resetRaceState();

    if (mode === 'race') {
      state = STATE.COUNTDOWN;
      UI.showCountdown(3, () => {
        state = STATE.PLAYING;
        raceStarted = true;
        Audio.startEngine();
        Audio.startMusic(trackId);
        startLoop();
      });
    } else {
      state = STATE.PLAYING;
      Audio.startEngine();
      Audio.startMusic(trackId);
      startLoop();
    }
  }

  function resetRaceState() {
    raceStarted = false;
    raceTimer = 0;
    lapTimer = 0;
    currentLap = 1;
    currentCheckpoint = 0;
    lapTimes = [];
    playerFinished = false;
    const saved = localStorage.getItem(bestLapKey());
    bestLap = saved ? parseFloat(saved) : Infinity;
    highlightNextCheckpoint(0);
  }

  function buildCheckpoints() {
    if (!trackData) return;
    checkpointMeshes = trackData.checkpoints.map((cp, idx) =>
      Tracks.buildCheckpointVisuals(scene, cp, idx, idx === 0)
    );
  }

  function highlightNextCheckpoint(idx) {
    if (!trackData) return;
    trackData.checkpoints.forEach((cp, i) => {
      const meshes = checkpointMeshes[i];
      if (!meshes) return;
      const isNext = i === idx;
      const col = isNext ? 0x00ff00 : 0x334455;
      meshes.forEach(m => {
        if (m.material) {
          m.material.color.setHex(col);
          m.material.emissive = new THREE.Color(col).multiplyScalar(isNext ? 0.5 : 0.05);
        }
      });
    });
  }

  // ── Main render loop ───────────────────────────────────────────────────────

  function startLoop() {
    if (animFrameId) cancelAnimationFrame(animFrameId);
    lastTime = performance.now();
    loop();
  }

  function loop() {
    animFrameId = requestAnimationFrame(loop);
    const now = performance.now();
    const dt = Math.min((now - lastTime) / 1000, 0.05);
    lastTime = now;

    if (state !== STATE.PLAYING && state !== STATE.COUNTDOWN) return;

    Audio.resume();
    Controls.update();
    const ctrl = Controls.getState();

    // Handle pause
    if (Controls.consumePause()) {
      pauseGame();
      return;
    }

    // Handle camera switch
    if (Controls.consumeCamera()) {
      cameraViewIdx = (cameraViewIdx + 1) % cameraViews.length;
      Settings.set('cameraView', cameraViews[cameraViewIdx]);
    }

    if (state === STATE.PLAYING && raceStarted) {
      // Update timers
      raceTimer += dt;
      lapTimer += dt;

      // Update player
      const terrainY = trackData ? trackData.getTerrainY(playerPhys.position.x, playerPhys.position.z) : 0;
      Car.updatePhysics(playerPhys, ctrl, dt, terrainY);
      Car.applyPhysicsToMesh(playerMesh, playerPhys);

      // Update audio
      Audio.updateEngine(playerPhys.speed, ctrl.throttle);

      // Tyre squeal on drift
      if (playerPhys.isDrifting && Math.random() < 0.05) Audio.playTire();

      // Checkpoint detection
      if (mode === 'race' && !playerFinished) checkPlayerCheckpoint();

      // Update AI
      if (mode === 'race') {
        AI.updateAll(aiAgents, playerPhys, dt, trackData ? trackData.getTerrainY.bind(trackData) : null);
        AI.checkAllWaypoints(aiAgents, trackData.checkpoints.map(c => c.position));
      }
    }

    // Update camera
    updateCamera(dt);

    // Update HUD
    const pos = computeRacePosition();
    UI.updateHUD({
      speed: playerPhys.speed,
      lap: currentLap,
      totalLaps,
      lapTime: lapTimer,
      bestLap,
      position: pos,
      drifting: playerPhys.isDrifting,
    });

    // Minimap
    UI.updateMinimap(playerPhys.position, mode === 'race' ? aiAgents : [], trackData ? trackData.waypoints : []);

    renderer.render(scene, camera);
  }

  function updateCamera(dt) {
    if (!playerMesh) return;
    const view = cameraViews[cameraViewIdx];

    if (view === 'cockpit' && playerMesh.cockpitAnchor) {
      const worldPos = playerMesh.cockpitAnchor.getWorldPosition(new THREE.Vector3());
      const worldDir = new THREE.Vector3(0, 0, 1).applyQuaternion(playerMesh.quaternion);
      camera.position.copy(worldPos);
      camera.lookAt(worldPos.clone().add(worldDir.multiplyScalar(10)));
    } else if (view === 'hood' && playerMesh.hoodAnchor) {
      const worldPos = playerMesh.hoodAnchor.getWorldPosition(new THREE.Vector3());
      const worldDir = new THREE.Vector3(0, 0, 1).applyQuaternion(playerMesh.quaternion);
      camera.position.copy(worldPos);
      camera.lookAt(worldPos.clone().add(worldDir.multiplyScalar(15)));
    } else {
      // Chase camera
      const forward = new THREE.Vector3(
        Math.sin(playerPhys.heading),
        0,
        Math.cos(playerPhys.heading),
      );
      const targetPos = playerPhys.position.clone()
        .add(forward.clone().multiplyScalar(chaseOffset.z))
        .add(new THREE.Vector3(0, chaseOffset.y, 0));
      camera.position.lerp(targetPos, 0.12);

      const lookTarget = playerPhys.position.clone()
        .add(forward.clone().multiplyScalar(chaseTarget.z))
        .add(new THREE.Vector3(0, chaseTarget.y, 0));
      camera.lookAt(lookTarget);
    }
  }

  function checkPlayerCheckpoint() {
    if (!trackData) return;
    const cps = trackData.checkpoints;
    if (!cps.length) return;
    const cp = cps[currentCheckpoint];
    const dist = playerPhys.position.distanceTo(cp.position);
    if (dist < cp.width * 0.8) {
      const nextCP = (currentCheckpoint + 1) % cps.length;
      if (nextCP === 0) {
        // Completed a lap
        lapTimes.push(lapTimer);
        if (lapTimer < bestLap) {
          bestLap = lapTimer;
          localStorage.setItem(bestLapKey(), bestLap);
        }
        UI.flashLap(currentLap, lapTimer);
        Audio.playCheckpoint();
        lapTimer = 0;
        currentLap++;
        if (currentLap > totalLaps) {
          finishRace();
          return;
        }
      } else {
        UI.flashCheckpoint('CHECKPOINT');
        Audio.playCheckpoint();
      }
      currentCheckpoint = nextCP;
      highlightNextCheckpoint(currentCheckpoint);
    }
  }

  function finishRace() {
    playerFinished = true;
    raceStarted = false;
    state = STATE.RESULTS;
    Audio.playFinish();

    setTimeout(() => {
      Audio.stopEngine();
      Audio.stopMusic();
      const results = buildResults();
      UI.showRaceResults(results, {
        restart: () => loadScene(currentTrackId, 'race'),
        menu: () => returnToMenu(),
      });
    }, 2000);
  }

  function buildResults() {
    const playerResult = {
      isPlayer: true,
      name: 'YOU',
      totalTime: raceTimer,
      bestLap: bestLap,
    };
    const aiResults = aiAgents.map(a => ({
      isPlayer: false,
      name: a.name,
      totalTime: raceTimer * (0.85 + Math.random() * 0.3),
      bestLap: bestLap * (0.9 + Math.random() * 0.3),
    }));
    return [playerResult, ...aiResults].sort((a, b) => a.totalTime - b.totalTime);
  }

  function computeRacePosition() {
    if (mode !== 'race') return 1;
    const playerScore = currentLap * 1000 + currentCheckpoint;
    let pos = 1;
    aiAgents.forEach(a => {
      const aiScore = a.lapCount * 1000 + a.checkpointIdx;
      if (aiScore > playerScore) pos++;
    });
    return pos;
  }

  // ── State transitions ──────────────────────────────────────────────────────

  function pauseGame() {
    if (state !== STATE.PLAYING) return;
    state = STATE.PAUSED;
    Audio.stopEngine();
    UI.showPause({
      resume: resumeGame,
      restart: () => loadScene(currentTrackId, mode),
      quit: returnToMenu,
    });
  }

  function resumeGame() {
    state = STATE.PLAYING;
    raceStarted = true;
    Audio.startEngine();
    UI.hidePause();
  }

  function returnToMenu() {
    Audio.stopEngine();
    Audio.stopMusic();
    Controls.dispose();
    UI.hideTouchControls();
    UI.hideHUD();
    UI.hidePause();
    if (animFrameId) cancelAnimationFrame(animFrameId);
    disposeScene();
    showMainMenu();
  }

  function disposeScene() {
    if (!scene) return;
    AI.disposeAll(aiAgents, scene);
    aiAgents = [];
    checkpointMeshes = [];
    // Dispose all scene objects
    scene.traverse(obj => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) {
        if (Array.isArray(obj.material)) obj.material.forEach(m => m.dispose());
        else obj.material.dispose();
      }
    });
    scene.clear();
    scene = null;
    playerMesh = null;
    trackData = null;
  }

  // ── Entry points ───────────────────────────────────────────────────────────

  function showMainMenu() {
    state = STATE.MAIN_MENU;
    UI.showMainMenu({
      freeMode: () => {
        mode = 'free';
        loadScene(currentTrackId, 'free');
      },
      raceMode: () => {
        mode = 'race';
        loadScene(currentTrackId, 'race');
      },
      mapSelect: () => {
        state = STATE.MAP_SELECT;
        UI.showMapSelect(Tracks.TRACK_LIST, currentTrackId, {
          select: id => { currentTrackId = id; },
          back: () => showMainMenu(),
        });
      },
      settings: () => {
        state = STATE.SETTINGS;
        UI.showSettings({ back: () => showMainMenu() });
      },
      exit: () => {
        UI.showExitConfirm({
          yes: () => {
            if (window.NeonRacer && window.NeonRacer.exit) window.NeonRacer.exit();
            else window.close();
          },
          no: () => showMainMenu(),
        });
      },
    });
  }

  // ── Startup ────────────────────────────────────────────────────────────────

  async function start() {
    UI.showLoading(0, 'Starting engine…');
    Settings.load();

    await sleep(100);
    UI.showLoading(20, 'Initializing renderer…');
    initRenderer();

    await sleep(100);
    UI.showLoading(60, 'Loading assets…');
    await sleep(200);
    UI.showLoading(90, 'Ready!');
    await sleep(300);
    UI.hideLoading();
    showMainMenu();
  }

  // ── Utils ──────────────────────────────────────────────────────────────────

  function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

  async function tick(pct, label) {
    UI.showLoading(pct, label);
    await sleep(30);
  }

  // Handle Android back button
  window.addEventListener('neonracer_back', () => {
    if (state === STATE.PLAYING) pauseGame();
    else if (state === STATE.PAUSED) resumeGame();
    else if (state === STATE.MAIN_MENU) {
      UI.showExitConfirm({
        yes: () => { if (window.NeonRacer) window.NeonRacer.exit(); },
        no: () => {},
      });
    }
  });

  return { start };
})();
