// Settings module — all values persist to localStorage
const Settings = (() => {
  const KEY = 'neonRacerSettings';

  const DEFAULTS = {
    graphicsQuality: 'medium',   // 'low' | 'medium' | 'high'
    masterVolume: 0.8,
    engineVolume: 0.7,
    musicVolume: 0.4,
    muted: false,
    controlScheme: 'buttons',    // 'buttons' | 'wheel' | 'tilt'
    cameraView: 'chase',         // 'chase' | 'cockpit' | 'hood'
    invertSteering: false,
    showTraffic: false,
    timeOfDay: 'night',          // 'day' | 'night'
  };

  let current = { ...DEFAULTS };

  function load() {
    try {
      const saved = localStorage.getItem(KEY);
      if (saved) current = { ...DEFAULTS, ...JSON.parse(saved) };
      else current = { ...DEFAULTS };
    } catch (e) {
      current = { ...DEFAULTS };
    }
  }

  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(current)); } catch (e) {}
  }

  function reset() { current = { ...DEFAULTS }; save(); }
  function get(key) { return current[key]; }
  function set(key, value) { current[key] = value; save(); }
  function getAll() { return { ...current }; }

  return { load, save, reset, get, set, getAll, DEFAULTS };
})();
