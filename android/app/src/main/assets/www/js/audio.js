// Audio engine — procedural sounds via Web Audio API
const Audio = (() => {
  let ctx = null;
  let masterGain = null;
  let engineGain = null;
  let musicGain = null;
  let engineOsc = null;
  let engineDistortion = null;
  let musicOsc = null;
  let musicNodes = [];
  let initialized = false;
  let muted = false;

  function ensureContext() {
    if (ctx) return true;
    try {
      ctx = new (window.AudioContext || window.webkitAudioContext)();
      masterGain = ctx.createGain();
      masterGain.connect(ctx.destination);
      engineGain = ctx.createGain();
      engineGain.connect(masterGain);
      musicGain = ctx.createGain();
      musicGain.connect(masterGain);
      return true;
    } catch (e) {
      console.warn('Web Audio not available', e);
      return false;
    }
  }

  function init(masterVol, engineVol, musicVol, isMuted) {
    if (!ensureContext()) return;
    muted = isMuted;
    masterGain.gain.value = isMuted ? 0 : masterVol;
    engineGain.gain.value = engineVol;
    musicGain.gain.value = musicVol;
    initialized = true;
  }

  function startEngine() {
    if (!initialized || !ctx) return;

    // Base engine oscillator
    engineOsc = ctx.createOscillator();
    engineOsc.type = 'sawtooth';
    engineOsc.frequency.value = 55;

    // Waveshaper for distortion / engine rumble
    engineDistortion = ctx.createWaveShaper();
    engineDistortion.curve = makeDistortionCurve(80);
    engineDistortion.oversample = '2x';

    // Low-pass filter
    const filter = ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 800;
    filter.Q.value = 2;

    // Gain shaping
    const oscGain = ctx.createGain();
    oscGain.gain.value = 0.35;

    engineOsc.connect(engineDistortion);
    engineDistortion.connect(filter);
    filter.connect(oscGain);
    oscGain.connect(engineGain);
    engineOsc.start();

    // Secondary harmonic
    const osc2 = ctx.createOscillator();
    osc2.type = 'square';
    osc2.frequency.value = 110;
    const g2 = ctx.createGain();
    g2.gain.value = 0.05;
    osc2.connect(g2);
    g2.connect(engineGain);
    osc2.start();
    engineOsc._osc2 = osc2;
    engineOsc._g2 = g2;
  }

  function updateEngine(speedKmh, throttle) {
    if (!engineOsc || !ctx) return;
    const t = ctx.currentTime;
    const idleFreq = 55;
    const maxFreq = 280;
    const freq = idleFreq + (maxFreq - idleFreq) * Math.abs(speedKmh / 180);
    engineOsc.frequency.setTargetAtTime(freq, t, 0.05);
    if (engineOsc._osc2) {
      engineOsc._osc2.frequency.setTargetAtTime(freq * 2, t, 0.05);
    }
  }

  function stopEngine() {
    if (engineOsc) {
      try {
        engineOsc.stop();
        if (engineOsc._osc2) engineOsc._osc2.stop();
      } catch (e) {}
      engineOsc = null;
    }
  }

  function playTire() {
    if (!initialized || !ctx) return;
    const buf = ctx.createBuffer(1, ctx.sampleRate * 0.3, ctx.sampleRate);
    const data = buf.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = (Math.random() * 2 - 1) * 0.4;
    const src = ctx.createBufferSource();
    src.buffer = buf;
    const gain = ctx.createGain();
    gain.gain.setValueAtTime(0.6, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
    src.connect(gain);
    gain.connect(masterGain);
    src.start();
  }

  function playCheckpoint() {
    if (!initialized || !ctx) return;
    const dur = 0.15;
    const freqs = [523, 659, 784, 1047];
    freqs.forEach((f, i) => {
      const osc = ctx.createOscillator();
      const g = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = f;
      g.gain.setValueAtTime(0.3, ctx.currentTime + i * dur);
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * dur + dur);
      osc.connect(g);
      g.connect(masterGain);
      osc.start(ctx.currentTime + i * dur);
      osc.stop(ctx.currentTime + i * dur + dur);
    });
  }

  function playCountdown(beat) {
    if (!initialized || !ctx) return;
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = beat === 0 ? 880 : 440;
    g.gain.setValueAtTime(0.5, ctx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.2);
    osc.connect(g);
    g.connect(masterGain);
    osc.start();
    osc.stop(ctx.currentTime + 0.25);
  }

  function playFinish() {
    if (!initialized || !ctx) return;
    const freqs = [523, 659, 784, 1047, 1319];
    freqs.forEach((f, i) => {
      const osc = ctx.createOscillator();
      const g = ctx.createGain();
      osc.type = i % 2 === 0 ? 'square' : 'triangle';
      osc.frequency.value = f;
      g.gain.setValueAtTime(0.25, ctx.currentTime + i * 0.1);
      g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.1 + 0.5);
      osc.connect(g);
      g.connect(masterGain);
      osc.start(ctx.currentTime + i * 0.1);
      osc.stop(ctx.currentTime + i * 0.1 + 0.6);
    });
  }

  function startMusic(trackId) {
    if (!initialized || !ctx) return;
    stopMusic();
    // Simple arpeggiated chiptune
    const scales = {
      city: [55, 73, 110, 146, 165, 220, 293],
      desert: [60, 75, 90, 120, 150, 180, 240],
      coastal: [65, 87, 110, 131, 175, 220, 262],
    };
    const notes = scales[trackId] || scales.city;
    let noteIdx = 0;
    const tempo = 0.2;
    let startTime = ctx.currentTime;

    function scheduleNotes(count) {
      for (let i = 0; i < count; i++) {
        const osc = ctx.createOscillator();
        const g = ctx.createGain();
        const freq = notes[noteIdx % notes.length];
        noteIdx++;
        osc.type = 'square';
        osc.frequency.value = freq;
        g.gain.setValueAtTime(0.0, startTime);
        g.gain.linearRampToValueAtTime(0.12, startTime + 0.01);
        g.gain.setValueAtTime(0.12, startTime + tempo - 0.03);
        g.gain.linearRampToValueAtTime(0.0, startTime + tempo);
        osc.connect(g);
        g.connect(musicGain);
        osc.start(startTime);
        osc.stop(startTime + tempo);
        musicNodes.push(osc);
        startTime += tempo;
      }
    }

    scheduleNotes(32);
    // Loop using setInterval
    const interval = setInterval(() => {
      if (!initialized) { clearInterval(interval); return; }
      scheduleNotes(16);
    }, tempo * 14 * 1000);
    musicNodes._interval = interval;
  }

  function stopMusic() {
    if (musicNodes._interval) clearInterval(musicNodes._interval);
    musicNodes.forEach(n => { try { n.stop(); } catch (e) {} });
    musicNodes = [];
  }

  function setMuted(val) {
    muted = val;
    if (masterGain) masterGain.gain.value = val ? 0 : (Settings.get('masterVolume') || 0.8);
  }

  function setVolumes(master, engine, music) {
    if (masterGain) masterGain.gain.value = muted ? 0 : master;
    if (engineGain) engineGain.gain.value = engine;
    if (musicGain) musicGain.gain.value = music;
  }

  function resume() {
    if (ctx && ctx.state === 'suspended') ctx.resume();
  }

  function makeDistortionCurve(amount) {
    const n = 256;
    const curve = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const x = (i * 2) / n - 1;
      curve[i] = ((Math.PI + amount) * x) / (Math.PI + amount * Math.abs(x));
    }
    return curve;
  }

  return {
    init, startEngine, updateEngine, stopEngine,
    playTire, playCheckpoint, playCountdown, playFinish,
    startMusic, stopMusic, setMuted, setVolumes, resume,
  };
})();
