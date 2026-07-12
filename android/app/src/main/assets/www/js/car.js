// Car physics + 3D model builder
const Car = (() => {

  // Physics constants
  const MAX_SPEED_KMH = 180;
  const MAX_REVERSE_KMH = 40;
  const ACCELERATION = 55;        // km/h per second at full throttle
  const BRAKE_FORCE = 110;
  const FRICTION = 0.985;
  const ROLLING_RESISTANCE = 0.003;
  const MAX_STEER_ANGLE = 0.045;  // radians per frame contribution
  const STEER_RETURN = 0.12;
  const DRIFT_FACTOR = 0.92;
  const LATERAL_GRIP = 0.88;
  const SUSPENSION_HEIGHT = 0.18;
  const SUSPENSION_STIFFNESS = 8;
  const WHEEL_RADIUS = 0.32;

  function buildMesh(scene, color = 0x00f0ff, isPlayer = true) {
    const group = new THREE.Group();

    // Main body
    const bodyGeo = new THREE.BoxGeometry(1.8, 0.55, 4.2);
    const bodyMat = new THREE.MeshPhongMaterial({
      color,
      emissive: new THREE.Color(color).multiplyScalar(0.15),
      shininess: 90,
      specular: 0xffffff,
    });
    const bodyMesh = new THREE.Mesh(bodyGeo, bodyMat);
    bodyMesh.position.y = 0.55;
    bodyMesh.castShadow = true;
    bodyMesh.receiveShadow = true;
    group.add(bodyMesh);

    // Cabin
    const cabinGeo = new THREE.BoxGeometry(1.6, 0.5, 2.2);
    const cabinMat = new THREE.MeshPhongMaterial({
      color: 0x0a0a1a,
      transparent: true,
      opacity: 0.7,
      shininess: 150,
    });
    const cabinMesh = new THREE.Mesh(cabinGeo, cabinMat);
    cabinMesh.position.set(0, 1.05, -0.2);
    cabinMesh.castShadow = true;
    group.add(cabinMesh);

    // Hood slope
    const hoodGeo = new THREE.BoxGeometry(1.75, 0.18, 0.9);
    const hoodMesh = new THREE.Mesh(hoodGeo, bodyMat);
    hoodMesh.position.set(0, 0.85, 1.2);
    hoodMesh.rotation.x = 0.15;
    hoodMesh.castShadow = true;
    group.add(hoodMesh);

    // Rear spoiler
    const spoilerGeo = new THREE.BoxGeometry(1.7, 0.08, 0.35);
    const spoilerMesh = new THREE.Mesh(spoilerGeo, bodyMat);
    spoilerMesh.position.set(0, 1.28, -1.85);
    group.add(spoilerMesh);

    // Spoiler mounts
    for (const x of [-0.6, 0.6]) {
      const mountGeo = new THREE.BoxGeometry(0.06, 0.3, 0.06);
      const mountMesh = new THREE.Mesh(mountGeo, bodyMat);
      mountMesh.position.set(x, 1.14, -1.85);
      group.add(mountMesh);
    }

    // Headlights
    const headlightMat = new THREE.MeshPhongMaterial({
      color: 0xffffee,
      emissive: 0xffffcc,
      emissiveIntensity: 1.2,
    });
    for (const x of [-0.65, 0.65]) {
      const hlGeo = new THREE.BoxGeometry(0.35, 0.14, 0.08);
      const hlMesh = new THREE.Mesh(hlGeo, headlightMat);
      hlMesh.position.set(x, 0.62, 2.1);
      group.add(hlMesh);
    }

    // Tail lights
    const taillightMat = new THREE.MeshPhongMaterial({
      color: 0xff2200,
      emissive: 0xff1100,
      emissiveIntensity: 1.5,
    });
    for (const x of [-0.65, 0.65]) {
      const tlGeo = new THREE.BoxGeometry(0.35, 0.14, 0.08);
      const tlMesh = new THREE.Mesh(tlGeo, taillightMat);
      tlMesh.position.set(x, 0.62, -2.1);
      group.add(tlMesh);
    }

    // Neon underglow
    const glowMat = new THREE.MeshPhongMaterial({
      color,
      emissive: new THREE.Color(color),
      emissiveIntensity: 0.8,
      transparent: true,
      opacity: 0.5,
    });
    const glowGeo = new THREE.BoxGeometry(2.0, 0.04, 4.4);
    const glowMesh = new THREE.Mesh(glowGeo, glowMat);
    glowMesh.position.y = 0.1;
    group.add(glowMesh);

    // Wheels (4)
    const wheelPositions = [
      [-0.95, WHEEL_RADIUS, 1.4],   // FL
      [0.95, WHEEL_RADIUS, 1.4],    // FR
      [-0.95, WHEEL_RADIUS, -1.4],  // RL
      [0.95, WHEEL_RADIUS, -1.4],   // RR
    ];
    const wheelGeo = new THREE.CylinderGeometry(WHEEL_RADIUS, WHEEL_RADIUS, 0.3, 16);
    const wheelMat = new THREE.MeshPhongMaterial({ color: 0x111111, shininess: 30 });
    const rimMat = new THREE.MeshPhongMaterial({ color: 0x888888, shininess: 120 });
    const rimGeo = new THREE.CylinderGeometry(WHEEL_RADIUS * 0.6, WHEEL_RADIUS * 0.6, 0.32, 8);

    const wheels = [];
    wheelPositions.forEach((pos, i) => {
      const wheelGroup = new THREE.Group();
      const wheel = new THREE.Mesh(wheelGeo, wheelMat);
      wheel.rotation.z = Math.PI / 2;
      wheel.castShadow = true;
      wheelGroup.add(wheel);
      const rim = new THREE.Mesh(rimGeo, rimMat);
      rim.rotation.z = Math.PI / 2;
      wheelGroup.add(rim);
      wheelGroup.position.set(...pos);
      group.add(wheelGroup);
      wheels.push(wheelGroup);
    });

    // Headlight point lights
    const headlightLight = new THREE.PointLight(0xffffcc, 1.5, 18);
    headlightLight.position.set(0, 0.8, 2.5);
    group.add(headlightLight);

    group.wheels = wheels;
    group.headlightLight = headlightLight;

    // Cockpit camera anchor
    const cockpitAnchor = new THREE.Object3D();
    cockpitAnchor.position.set(0, 1.3, 0.8);
    group.add(cockpitAnchor);
    group.cockpitAnchor = cockpitAnchor;

    // Hood camera anchor
    const hoodAnchor = new THREE.Object3D();
    hoodAnchor.position.set(0, 1.1, 1.6);
    group.add(hoodAnchor);
    group.hoodAnchor = hoodAnchor;

    scene.add(group);
    return group;
  }

  function createPhysics(startPos, startAngle) {
    return {
      position: startPos.clone(),
      heading: startAngle || 0,
      speed: 0,           // km/h (positive = forward)
      steeringAngle: 0,   // current visual steer angle
      lateralVelocity: 0, // for drift
      suspensionOffset: 0,
      suspensionVel: 0,
      wheelRot: 0,        // wheel rotation for spin animation
      isDrifting: false,
      onGround: true,
    };
  }

  function updatePhysics(phys, ctrl, dt, terrainY) {
    const mps = phys.speed / 3.6;
    const absSpeed = Math.abs(phys.speed);
    const speedFactor = absSpeed / MAX_SPEED_KMH;

    // Throttle / brake
    if (ctrl.throttle > 0 && phys.speed > -5) {
      phys.speed += ACCELERATION * ctrl.throttle * dt;
    }
    if (ctrl.brake > 0) {
      if (phys.speed > 0) phys.speed -= BRAKE_FORCE * ctrl.brake * dt;
      else phys.speed -= ACCELERATION * 0.5 * ctrl.brake * dt; // reverse
    }

    // Speed limits
    phys.speed = Math.max(-MAX_REVERSE_KMH, Math.min(MAX_SPEED_KMH, phys.speed));

    // Friction + rolling resistance
    const fric = ctrl.handbrake ? 0.88 : FRICTION;
    phys.speed *= Math.pow(fric, dt * 60);
    phys.speed *= (1 - ROLLING_RESISTANCE * dt * 60);
    if (Math.abs(phys.speed) < 0.1) phys.speed = 0;

    // Steering
    const steerInput = ctrl.steering;
    const maxAngle = MAX_STEER_ANGLE;
    const targetSteer = steerInput * maxAngle * (1 - speedFactor * 0.4);
    phys.steeringAngle += (targetSteer - phys.steeringAngle) * STEER_RETURN * dt * 60;

    // Turn heading based on speed and steering
    if (absSpeed > 0.5) {
      const turnRate = (phys.steeringAngle / maxAngle) * 1.8 * (phys.speed > 0 ? 1 : -1);
      const gripFactor = ctrl.handbrake ? DRIFT_FACTOR : 1.0;
      phys.heading += turnRate * dt * (absSpeed / 50) * gripFactor;
    }

    // Lateral drift
    if (ctrl.handbrake && absSpeed > 20) {
      phys.isDrifting = true;
      phys.lateralVelocity *= LATERAL_GRIP;
      phys.lateralVelocity += steerInput * 8 * dt;
    } else {
      phys.isDrifting = false;
      phys.lateralVelocity *= 0.85;
    }

    // Move forward
    const fwdMps = phys.speed / 3.6;
    phys.position.x += Math.sin(phys.heading) * fwdMps * dt;
    phys.position.z += Math.cos(phys.heading) * fwdMps * dt;

    // Lateral movement
    phys.position.x += Math.cos(phys.heading) * phys.lateralVelocity * dt;
    phys.position.z -= Math.sin(phys.heading) * phys.lateralVelocity * dt;

    // Terrain height / suspension
    const targetY = terrainY !== undefined ? terrainY : 0;
    const suspTarget = targetY + SUSPENSION_HEIGHT;
    const suspDelta = suspTarget - phys.position.y;
    phys.suspensionVel += suspDelta * SUSPENSION_STIFFNESS * dt;
    phys.suspensionVel *= 0.75;
    phys.position.y += phys.suspensionVel * dt * 10;
    phys.position.y = Math.max(targetY, phys.position.y);

    // Wheel rotation
    phys.wheelRot += fwdMps * dt * 2;
  }

  function applyPhysicsToMesh(mesh, phys) {
    if (!mesh) return;
    mesh.position.copy(phys.position);
    mesh.rotation.y = phys.heading;

    // Suspension tilt based on lateral vel
    mesh.rotation.z = -phys.lateralVelocity * 0.02;
    mesh.rotation.x = -phys.suspensionVel * 0.05;

    // Front wheels steer
    if (mesh.wheels) {
      mesh.wheels[0].rotation.y = phys.steeringAngle * 15;
      mesh.wheels[1].rotation.y = phys.steeringAngle * 15;
      // Spin wheels
      mesh.wheels.forEach(w => {
        w.children[0].rotation.x = phys.wheelRot;
      });
    }
  }

  return { buildMesh, createPhysics, updatePhysics, applyPhysicsToMesh };
})();
