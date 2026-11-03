// AI opponent — waypoint-following cars with simple rubber-banding
const AI = (() => {

  const AI_MAX_SPEED = 130;
  const AI_ACCEL = 40;
  const AI_BRAKE = 80;
  const AI_TURN_SPEED = 2.2;
  const AI_LOOKAHEAD = 20;  // how far ahead to steer towards (m)
  const AI_STEER_GAIN = 0.8;
  const AI_RUBBER_BAND = 0.15; // speed bonus/penalty when far from player

  function createAgent(scene, waypoints, startIndex, color, name) {
    const mesh = Car.buildMesh(scene, color, false);
    const waypointIdx = startIndex % waypoints.length;
    const startPos = waypoints[waypointIdx].clone();
    startPos.y = 0;

    // Offset slightly so they don't all stack
    startPos.x += (Math.random() - 0.5) * 6;
    startPos.z += (Math.random() - 0.5) * 6;

    const phys = Car.createPhysics(startPos, 0);

    const agent = {
      mesh,
      phys,
      waypoints,
      waypointIdx,
      name,
      color,
      lapCount: 0,
      checkpointIdx: 0,
      totalCheckpointsPassed: 0,
      racePosition: 0,
      finished: false,
      finishTime: null,
      // Difficulty modifiers (0.7 = easier, 1.0 = full, 1.2 = hard)
      speedMult: 0.85 + Math.random() * 0.25,
    };

    return agent;
  }

  function updateAgent(agent, playerPhys, dt, terrainY) {
    if (agent.finished) return;

    const phys = agent.phys;
    const waypoints = agent.waypoints;

    // Find the target waypoint
    const targetWP = waypoints[agent.waypointIdx];
    const toTarget = targetWP.clone().sub(phys.position);
    toTarget.y = 0;
    const distToTarget = toTarget.length();

    // Advance waypoint if close enough
    if (distToTarget < AI_LOOKAHEAD) {
      agent.waypointIdx = (agent.waypointIdx + 1) % waypoints.length;
    }

    // Compute heading angle to target
    const targetAngle = Math.atan2(toTarget.x, toTarget.z);
    let angleDiff = targetAngle - phys.heading;
    // Normalize to [-PI, PI]
    while (angleDiff > Math.PI) angleDiff -= Math.PI * 2;
    while (angleDiff < -Math.PI) angleDiff += Math.PI * 2;

    // Rubber banding: if far behind player, go faster; if far ahead, go slower
    const distToPlayer = phys.position.distanceTo(playerPhys.position);
    let speedBonus = 0;
    if (distToPlayer > 50) speedBonus = AI_RUBBER_BAND;
    if (distToPlayer < 15) speedBonus = -AI_RUBBER_BAND;

    const maxSpeed = AI_MAX_SPEED * agent.speedMult * (1 + speedBonus);
    const absSpeed = Math.abs(phys.speed);

    // Fake ctrl object
    const ctrl = {
      throttle: 0,
      brake: 0,
      steering: Math.max(-1, Math.min(1, angleDiff * AI_STEER_GAIN * 2)),
      handbrake: false,
    };

    // Slow down for sharp turns
    const sharpTurn = Math.abs(angleDiff) > 0.5;
    const targetSpeed = sharpTurn ? maxSpeed * 0.55 : maxSpeed;

    if (absSpeed < targetSpeed) ctrl.throttle = 1;
    else ctrl.brake = Math.min(1, (absSpeed - targetSpeed) / 20);

    Car.updatePhysics(phys, ctrl, dt, terrainY ? terrainY(phys.position.x, phys.position.z) : 0);
    Car.applyPhysicsToMesh(agent.mesh, phys);
  }

  function checkWaypoint(agent, checkpointPositions) {
    if (!checkpointPositions || agent.finished) return false;
    const cp = checkpointPositions[agent.checkpointIdx];
    if (!cp) return false;
    const dist = agent.phys.position.distanceTo(cp);
    if (dist < 20) {
      agent.checkpointIdx = (agent.checkpointIdx + 1) % checkpointPositions.length;
      agent.totalCheckpointsPassed++;
      if (agent.checkpointIdx === 0) agent.lapCount++;
      return true;
    }
    return false;
  }

  const AI_CONFIGS = [
    { color: 0xff00ff, name: 'CYBER-1' },
    { color: 0xffcc00, name: 'BLAZE-X' },
    { color: 0xff3300, name: 'INFERNO' },
  ];

  function createAllAgents(scene, waypoints) {
    return AI_CONFIGS.map((cfg, i) =>
      createAgent(scene, waypoints, Math.max(0, i * 2), cfg.color, cfg.name)
    );
  }

  function updateAll(agents, playerPhys, dt, terrainY) {
    agents.forEach(a => updateAgent(a, playerPhys, dt, terrainY));
  }

  function checkAllWaypoints(agents, checkpointPositions) {
    agents.forEach(a => checkWaypoint(a, checkpointPositions));
  }

  function disposeAll(agents, scene) {
    agents.forEach(a => {
      if (a.mesh) scene.remove(a.mesh);
    });
  }

  return { createAllAgents, updateAll, checkAllWaypoints, disposeAll };
})();
