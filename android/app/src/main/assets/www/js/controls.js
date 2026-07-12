// Controls module — touch buttons, steering wheel, tilt, and keyboard
const Controls = (() => {
  const state = {
    throttle: 0,
    brake: 0,
    steering: 0,  // -1 (left) to +1 (right)
    handbrake: false,
    pausePressed: false,
    cameraPressed: false,
  };

  let scheme = 'buttons';
  let invertFactor = 1;
  let tiltBaseline = 0;
  let tiltCalibrated = false;

  // Active touches keyed by identifier
  const activeTouches = {};

  // Button element references
  const btns = {};

  function init(controlScheme, invertSteering) {
    scheme = controlScheme;
    invertFactor = invertSteering ? -1 : 1;
    state.throttle = 0;
    state.brake = 0;
    state.steering = 0;
    state.handbrake = false;

    if (scheme === 'tilt') {
      setupTilt();
    }
    setupKeyboard();
    // Touch overlay is built and managed by UI module; we just track state here
  }

  function setupTilt() {
    tiltCalibrated = false;
    window.addEventListener('deviceorientation', onTilt, true);
    // calibrate after 1 second
    setTimeout(() => { tiltCalibrated = true; }, 1000);
  }

  function onTilt(e) {
    if (!tiltCalibrated) {
      tiltBaseline = e.gamma || 0;
      return;
    }
    const gamma = (e.gamma || 0) - tiltBaseline;
    state.steering = Math.max(-1, Math.min(1, gamma / 30)) * invertFactor;
  }

  const keyMap = {
    ArrowUp: 'throttle', w: 'throttle', W: 'throttle',
    ArrowDown: 'brake', s: 'brake', S: 'brake',
    ArrowLeft: 'left', a: 'left', A: 'left',
    ArrowRight: 'right', d: 'right', D: 'right',
    ' ': 'handbrake',
    Escape: 'pause', p: 'pause', P: 'pause',
    c: 'camera', C: 'camera',
  };

  const keyState = {};

  function setupKeyboard() {
    window.addEventListener('keydown', e => {
      const action = keyMap[e.key];
      if (!action) return;
      if (!keyState[action]) {
        keyState[action] = true;
        if (action === 'pause') state.pausePressed = true;
        if (action === 'camera') state.cameraPressed = true;
      }
      e.preventDefault();
    });
    window.addEventListener('keyup', e => {
      const action = keyMap[e.key];
      if (action) keyState[action] = false;
    });
  }

  function update() {
    // Keyboard state
    if (scheme !== 'tilt') {
      let targetSteering = 0;
      if (keyState.left) targetSteering -= 1;
      if (keyState.right) targetSteering += 1;
      targetSteering *= invertFactor;
      // Smooth keyboard steering
      const kbStr = targetSteering;
      state.steering = lerp(state.steering, kbStr, 0.15);
    }

    if (keyState.throttle) state.throttle = Math.min(1, state.throttle + 0.08);
    else state.throttle = Math.max(0, state.throttle - 0.06);

    if (keyState.brake) state.brake = Math.min(1, state.brake + 0.1);
    else state.brake = Math.max(0, state.brake - 0.08);

    if (keyState.handbrake !== undefined) state.handbrake = !!keyState.handbrake;
  }

  function lerp(a, b, t) { return a + (b - a) * t; }

  // Called by UI when touch buttons change state
  function setButtonState(button, active) {
    switch (button) {
      case 'throttle': state.throttle = active ? 1 : 0; break;
      case 'brake': state.brake = active ? 1 : 0; break;
      case 'left': {
        // handled in update via touchSteering
        break;
      }
      case 'right': break;
      case 'handbrake': state.handbrake = active; break;
    }
  }

  // For touch steering (buttons/wheel): set raw steering value
  function setTouchSteering(value) {
    state.steering = Math.max(-1, Math.min(1, value * invertFactor));
  }

  function consumePause() {
    const v = state.pausePressed;
    state.pausePressed = false;
    return v;
  }

  function consumeCamera() {
    const v = state.cameraPressed;
    state.cameraPressed = false;
    return v;
  }

  function getState() { return state; }

  function dispose() {
    window.removeEventListener('deviceorientation', onTilt, true);
  }

  return { init, update, setButtonState, setTouchSteering, getState, consumePause, consumeCamera, dispose };
})();
