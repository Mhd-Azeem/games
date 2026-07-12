// Track definitions and scene builder
const Tracks = (() => {

  // ---- Track 1: Neon City ----
  function buildNeonCity(scene, quality) {
    const checkpoints = [];
    const waypoints = [];

    // City loop — 8 waypoints forming a city block circuit
    const cityPoints = [
      [0, 0],
      [120, 0],
      [200, 40],
      [200, 160],
      [120, 200],
      [0, 200],
      [-80, 160],
      [-80, 40],
    ];

    // Build road segments between points (closed loop)
    const roadPieces = [];
    const roadW = 14;
    const totalSegments = cityPoints.length;

    for (let i = 0; i < totalSegments; i++) {
      const a = cityPoints[i];
      const b = cityPoints[(i + 1) % totalSegments];
      roadPieces.push({ from: a, to: b });
    }

    roadPieces.forEach((seg, idx) => {
      const from = new THREE.Vector3(seg.from[0], 0, seg.from[1]);
      const to = new THREE.Vector3(seg.to[0], 0, seg.to[1]);
      buildRoadSegment(scene, from, to, roadW, 0x111133, 0x00f0ff);

      // Checkpoint at midpoint
      const mid = from.clone().add(to).multiplyScalar(0.5);
      const dir = to.clone().sub(from).normalize();
      checkpoints.push({ position: mid, normal: dir, width: roadW + 4 });
      waypoints.push(mid.clone());
    });

    // Buildings along the sides
    const buildingColors = [0x0d0d2e, 0x0a1a3a, 0x1a0a2e, 0x0a2a1a];
    const neonColors = [0x00f0ff, 0xff00ff, 0x00ff88, 0xff6600];

    for (let i = 0; i < (quality === 'low' ? 30 : 60); i++) {
      const segIdx = Math.floor(Math.random() * roadPieces.length);
      const seg = roadPieces[segIdx];
      const from = new THREE.Vector3(seg.from[0], 0, seg.from[1]);
      const to = new THREE.Vector3(seg.to[0], 0, seg.to[1]);
      const dir = to.clone().sub(from).normalize();
      const perp = new THREE.Vector3(-dir.z, 0, dir.x);
      const t = Math.random();
      const center = from.clone().lerp(to, t);
      const side = (Math.random() > 0.5 ? 1 : -1) * (roadW / 2 + 5 + Math.random() * 10);
      const pos = center.clone().add(perp.clone().multiplyScalar(side));

      const h = 8 + Math.random() * 30;
      const w = 6 + Math.random() * 10;
      const d = 6 + Math.random() * 10;
      const bGeo = new THREE.BoxGeometry(w, h, d);
      const bMat = new THREE.MeshPhongMaterial({
        color: buildingColors[Math.floor(Math.random() * buildingColors.length)],
        emissive: 0x050510,
        shininess: 10,
      });
      const bMesh = new THREE.Mesh(bGeo, bMat);
      bMesh.position.set(pos.x, h / 2, pos.z);
      bMesh.castShadow = quality !== 'low';
      bMesh.receiveShadow = quality !== 'low';
      scene.add(bMesh);

      // Neon strip on building
      if (quality !== 'low' && Math.random() > 0.5) {
        const nGeo = new THREE.BoxGeometry(w + 0.2, 0.3, 0.1);
        const nCol = neonColors[Math.floor(Math.random() * neonColors.length)];
        const nMat = new THREE.MeshPhongMaterial({
          color: nCol, emissive: new THREE.Color(nCol), emissiveIntensity: 2,
        });
        const nMesh = new THREE.Mesh(nGeo, nMat);
        nMesh.position.set(pos.x, h * 0.6, pos.z + d / 2 + 0.05);
        scene.add(nMesh);
      }
    }

    // Ground plane
    const groundGeo = new THREE.PlaneGeometry(600, 600);
    const groundMat = new THREE.MeshPhongMaterial({ color: 0x0a0a1a, shininess: 5 });
    const ground = new THREE.Mesh(groundGeo, groundMat);
    ground.rotation.x = -Math.PI / 2;
    ground.position.set(60, -0.02, 100);
    ground.receiveShadow = true;
    scene.add(ground);

    // Streetlights
    const lightInterval = 30;
    roadPieces.forEach(seg => {
      const from = new THREE.Vector3(seg.from[0], 0, seg.from[1]);
      const to = new THREE.Vector3(seg.to[0], 0, seg.to[1]);
      const dir = to.clone().sub(from).normalize();
      const perp = new THREE.Vector3(-dir.z, 0, dir.x);
      const len = from.distanceTo(to);
      const steps = Math.floor(len / lightInterval);
      for (let s = 0; s <= steps; s++) {
        const t = s / Math.max(steps, 1);
        const pos = from.clone().lerp(to, t);
        addStreetlight(scene, pos.clone().add(perp.clone().multiplyScalar(roadW / 2 + 1.5)), quality);
      }
    });

    // Fog & sky
    scene.background = new THREE.Color(0x050515);
    scene.fog = new THREE.Fog(0x050515, quality === 'low' ? 80 : 150, quality === 'high' ? 400 : 250);

    // Ambient + city glow
    const ambient = new THREE.AmbientLight(0x111133, 0.8);
    scene.add(ambient);
    const cityGlow = new THREE.DirectionalLight(0x0033ff, 0.3);
    cityGlow.position.set(0, 50, 0);
    scene.add(cityGlow);

    return {
      name: 'Neon City',
      checkpoints,
      waypoints,
      startPosition: new THREE.Vector3(60, 0, 0),
      startHeading: 0,
      lapCount: 3,
      getTerrainY: () => 0,
    };
  }

  // ---- Track 2: Desert Storm ----
  function buildDesertStorm(scene, quality) {
    const checkpoints = [];
    const waypoints = [];

    // Figure-8 / oval on hilly terrain
    const desertPoints = [
      [0, 0],
      [100, -30],
      [200, 0],
      [260, 80],
      [200, 160],
      [100, 190],
      [0, 160],
      [-60, 80],
    ];

    const roadW = 16;
    const segments = desertPoints.length;

    for (let i = 0; i < segments; i++) {
      const a = desertPoints[i];
      const b = desertPoints[(i + 1) % segments];
      const from = new THREE.Vector3(a[0], 0, a[1]);
      const to = new THREE.Vector3(b[0], 0, b[1]);
      buildRoadSegment(scene, from, to, roadW, 0x8b6914, 0xff6600);
      const mid = from.clone().add(to).multiplyScalar(0.5);
      const dir = to.clone().sub(from).normalize();
      checkpoints.push({ position: mid, normal: dir, width: roadW + 4 });
      waypoints.push(mid.clone());
    }

    // Sandy terrain with hills
    const terrainSize = 600;
    const terrainSegs = quality === 'high' ? 80 : 40;
    const terrainGeo = new THREE.PlaneGeometry(terrainSize, terrainSize, terrainSegs, terrainSegs);
    const positions = terrainGeo.attributes.position;
    for (let i = 0; i < positions.count; i++) {
      const x = positions.getX(i);
      const z = positions.getY(i);
      const height = Math.sin(x * 0.02) * 3 + Math.cos(z * 0.015) * 2
        + Math.sin(x * 0.05 + z * 0.03) * 1.5;
      positions.setZ(i, height);
    }
    terrainGeo.computeVertexNormals();
    const terrainMat = new THREE.MeshPhongMaterial({ color: 0xc8a055, shininess: 2 });
    const terrain = new THREE.Mesh(terrainGeo, terrainMat);
    terrain.rotation.x = -Math.PI / 2;
    terrain.position.set(100, -0.5, 80);
    terrain.receiveShadow = true;
    scene.add(terrain);

    // Rocky formations
    for (let i = 0; i < (quality === 'low' ? 20 : 40); i++) {
      const x = (Math.random() - 0.5) * 400 + 100;
      const z = (Math.random() - 0.5) * 400 + 80;
      const h = 3 + Math.random() * 12;
      const r = 2 + Math.random() * 6;
      const rGeo = new THREE.ConeGeometry(r, h, 6);
      const rMat = new THREE.MeshPhongMaterial({
        color: 0x8b5a2b, shininess: 5,
        flatShading: true,
      });
      const rMesh = new THREE.Mesh(rGeo, rMat);
      rMesh.position.set(x, h / 2, z);
      rMesh.rotation.y = Math.random() * Math.PI;
      rMesh.castShadow = quality !== 'low';
      scene.add(rMesh);
    }

    // Cactus-like pillars
    for (let i = 0; i < 30; i++) {
      const x = (Math.random() - 0.5) * 350 + 100;
      const z = (Math.random() - 0.5) * 350 + 80;
      const h = 4 + Math.random() * 5;
      const cGeo = new THREE.CylinderGeometry(0.3, 0.5, h, 8);
      const cMat = new THREE.MeshPhongMaterial({ color: 0x2d6a2d, shininess: 5 });
      const cMesh = new THREE.Mesh(cGeo, cMat);
      cMesh.position.set(x, h / 2, z);
      scene.add(cMesh);
    }

    // Sky & lighting
    scene.background = new THREE.Color(0xe8952a);
    scene.fog = new THREE.FogExp2(0xe8952a, quality === 'low' ? 0.006 : 0.003);
    const sun = new THREE.DirectionalLight(0xffcc66, 1.4);
    sun.position.set(200, 100, -100);
    sun.castShadow = quality === 'high';
    if (quality === 'high') {
      sun.shadow.mapSize.width = 2048;
      sun.shadow.mapSize.height = 2048;
      sun.shadow.camera.near = 1;
      sun.shadow.camera.far = 600;
      sun.shadow.camera.left = -300;
      sun.shadow.camera.right = 300;
      sun.shadow.camera.top = 300;
      sun.shadow.camera.bottom = -300;
    }
    scene.add(sun);
    scene.add(new THREE.AmbientLight(0xff9944, 0.6));

    return {
      name: 'Desert Storm',
      checkpoints,
      waypoints,
      startPosition: new THREE.Vector3(0, 0, 0),
      startHeading: 0,
      lapCount: 3,
      getTerrainY: (x, z) => {
        const lx = x - 100, lz = z - 80;
        return Math.sin(lx * 0.02) * 3 + Math.cos(lz * 0.015) * 2
          + Math.sin(lx * 0.05 + lz * 0.03) * 1.5 - 0.5;
      },
    };
  }

  // ---- Track 3: Coastal Circuit ----
  function buildCoastalCircuit(scene, quality) {
    const checkpoints = [];
    const waypoints = [];

    // Coastal oval with chicanes
    const coastPoints = [
      [0, 0],
      [80, -20],
      [160, 0],
      [220, 60],
      [240, 130],
      [220, 190],
      [180, 230],
      [100, 250],
      [20, 240],
      [-40, 200],
      [-60, 130],
      [-40, 60],
    ];

    const roadW = 15;
    const segments = coastPoints.length;

    for (let i = 0; i < segments; i++) {
      const a = coastPoints[i];
      const b = coastPoints[(i + 1) % segments];
      const from = new THREE.Vector3(a[0], 0, a[1]);
      const to = new THREE.Vector3(b[0], 0, b[1]);
      buildRoadSegment(scene, from, to, roadW, 0x222244, 0x00ff88);
      const mid = from.clone().add(to).multiplyScalar(0.5);
      const dir = to.clone().sub(from).normalize();
      checkpoints.push({ position: mid, normal: dir, width: roadW + 4 });
      waypoints.push(mid.clone());
    }

    // Ocean (large flat blue plane, lower)
    const oceanGeo = new THREE.PlaneGeometry(800, 400);
    const oceanMat = new THREE.MeshPhongMaterial({
      color: 0x0044aa,
      shininess: 100,
      specular: 0x66bbff,
      transparent: true,
      opacity: 0.85,
    });
    const ocean = new THREE.Mesh(oceanGeo, oceanMat);
    ocean.rotation.x = -Math.PI / 2;
    ocean.position.set(400, -1.5, 115);
    scene.add(ocean);

    // Ground
    const gGeo = new THREE.PlaneGeometry(700, 700);
    const gMat = new THREE.MeshPhongMaterial({ color: 0x2d5a27, shininess: 3 });
    const ground = new THREE.Mesh(gGeo, gMat);
    ground.rotation.x = -Math.PI / 2;
    ground.position.set(90, -0.05, 115);
    ground.receiveShadow = true;
    scene.add(ground);

    // Palm trees
    for (let i = 0; i < (quality === 'low' ? 20 : 50); i++) {
      const x = (Math.random() - 0.5) * 500 + 90;
      const z = (Math.random() - 0.5) * 500 + 115;
      addPalmTree(scene, x, z, quality);
    }

    // Cliffside barriers (coastal wall)
    for (let i = 0; i < 20; i++) {
      const x = 180 + i * 15;
      const bGeo = new THREE.BoxGeometry(1.5, 3, 10);
      const bMat = new THREE.MeshPhongMaterial({ color: 0xddccaa });
      const b = new THREE.Mesh(bGeo, bMat);
      b.position.set(x, 1, 260);
      scene.add(b);
    }

    // Sky & lighting
    scene.background = new THREE.Color(0x1a3a6a);
    scene.fog = new THREE.Fog(0x1a3a6a, quality === 'low' ? 100 : 200, quality === 'high' ? 500 : 300);
    const sun = new THREE.DirectionalLight(0xffffff, 1.2);
    sun.position.set(-100, 120, 50);
    sun.castShadow = quality === 'high';
    if (quality === 'high') {
      sun.shadow.mapSize.width = 2048;
      sun.shadow.mapSize.height = 2048;
      sun.shadow.camera.far = 600;
      sun.shadow.camera.left = -300;
      sun.shadow.camera.right = 300;
      sun.shadow.camera.top = 300;
      sun.shadow.camera.bottom = -300;
    }
    scene.add(sun);
    scene.add(new THREE.AmbientLight(0x8899cc, 0.7));

    // Sun reflection on water
    const sunReflect = new THREE.PointLight(0x4488ff, 2, 200);
    sunReflect.position.set(350, 5, 100);
    scene.add(sunReflect);

    return {
      name: 'Coastal Circuit',
      checkpoints,
      waypoints,
      startPosition: new THREE.Vector3(0, 0, 0),
      startHeading: 0,
      lapCount: 3,
      getTerrainY: () => 0,
    };
  }

  // ---- Shared helpers ----

  function buildRoadSegment(scene, from, to, width, roadColor, lineColor) {
    const dir = to.clone().sub(from);
    const len = dir.length();
    const mid = from.clone().add(to).multiplyScalar(0.5);
    const angle = Math.atan2(dir.x, dir.z);

    // Road surface
    const roadGeo = new THREE.BoxGeometry(width, 0.2, len);
    const roadMat = new THREE.MeshPhongMaterial({ color: roadColor, shininess: 5 });
    const road = new THREE.Mesh(roadGeo, roadMat);
    road.position.copy(mid);
    road.position.y = 0;
    road.rotation.y = angle;
    road.receiveShadow = true;
    scene.add(road);

    // Center line
    const lineGeo = new THREE.BoxGeometry(0.3, 0.22, len);
    const lineMat = new THREE.MeshPhongMaterial({
      color: lineColor,
      emissive: new THREE.Color(lineColor).multiplyScalar(0.6),
    });
    const line = new THREE.Mesh(lineGeo, lineMat);
    line.position.copy(mid);
    line.position.y = 0.01;
    line.rotation.y = angle;
    scene.add(line);

    // Edge lines
    const perp = new THREE.Vector3(-dir.z, 0, dir.x).normalize();
    for (const side of [-1, 1]) {
      const eGeo = new THREE.BoxGeometry(0.2, 0.22, len);
      const eMat = new THREE.MeshPhongMaterial({ color: 0xffffff, emissive: 0x888888 });
      const eLine = new THREE.Mesh(eGeo, eMat);
      eLine.position.copy(mid.clone().add(perp.clone().multiplyScalar(side * (width / 2 - 0.5))));
      eLine.position.y = 0.01;
      eLine.rotation.y = angle;
      scene.add(eLine);
    }

    // Barriers/walls
    for (const side of [-1, 1]) {
      const barrierGeo = new THREE.BoxGeometry(0.5, 1.0, len);
      const barrierMat = new THREE.MeshPhongMaterial({
        color: lineColor,
        emissive: new THREE.Color(lineColor).multiplyScalar(0.3),
      });
      const barrier = new THREE.Mesh(barrierGeo, barrierMat);
      barrier.position.copy(mid.clone().add(perp.clone().multiplyScalar(side * (width / 2 + 0.25))));
      barrier.position.y = 0.5;
      barrier.rotation.y = angle;
      barrier.castShadow = true;
      scene.add(barrier);
    }
  }

  function addStreetlight(scene, pos, quality) {
    // Pole
    const poleGeo = new THREE.CylinderGeometry(0.1, 0.12, 8, 6);
    const poleMat = new THREE.MeshPhongMaterial({ color: 0x333344 });
    const pole = new THREE.Mesh(poleGeo, poleMat);
    pole.position.set(pos.x, 4, pos.z);
    scene.add(pole);

    // Lamp head
    const lampGeo = new THREE.BoxGeometry(0.8, 0.3, 0.4);
    const lampMat = new THREE.MeshPhongMaterial({
      color: 0xffffcc, emissive: 0xffffaa, emissiveIntensity: 1.5,
    });
    const lamp = new THREE.Mesh(lampGeo, lampMat);
    lamp.position.set(pos.x, 8.2, pos.z);
    scene.add(lamp);

    if (quality !== 'low') {
      const light = new THREE.PointLight(0xffffaa, 1.2, 25);
      light.position.set(pos.x, 7.8, pos.z);
      scene.add(light);
    }
  }

  function addPalmTree(scene, x, z, quality) {
    const h = 5 + Math.random() * 4;
    const trunkGeo = new THREE.CylinderGeometry(0.2, 0.35, h, 6);
    const trunkMat = new THREE.MeshPhongMaterial({ color: 0x8b6914 });
    const trunk = new THREE.Mesh(trunkGeo, trunkMat);
    trunk.position.set(x, h / 2, z);
    trunk.rotation.z = (Math.random() - 0.5) * 0.3;
    scene.add(trunk);

    // Leaf clusters
    if (quality !== 'low') {
      for (let i = 0; i < 5; i++) {
        const leafGeo = new THREE.ConeGeometry(1.2, 0.4, 8);
        const leafMat = new THREE.MeshPhongMaterial({ color: 0x228b22 });
        const leaf = new THREE.Mesh(leafGeo, leafMat);
        const ang = (i / 5) * Math.PI * 2;
        leaf.position.set(
          x + Math.cos(ang) * 1.5,
          h + 0.5,
          z + Math.sin(ang) * 1.5,
        );
        leaf.rotation.z = Math.cos(ang) * 0.8;
        leaf.rotation.x = Math.sin(ang) * 0.8;
        scene.add(leaf);
      }
    }

    const topGeo = new THREE.SphereGeometry(1.8, 8, 6);
    const topMat = new THREE.MeshPhongMaterial({ color: 0x33aa33 });
    const top = new THREE.Mesh(topGeo, topMat);
    top.position.set(x, h + 0.8, z);
    scene.add(top);
  }

  function buildCheckpointVisuals(scene, checkpoint, idx, isNext) {
    const arches = [];
    const col = isNext ? 0x00ff00 : 0x444466;
    const height = 5;
    const halfW = checkpoint.width / 2;
    const dir = checkpoint.normal.clone();
    const perp = new THREE.Vector3(-dir.z, 0, dir.x);

    for (const side of [-1, 1]) {
      const archGeo = new THREE.BoxGeometry(0.4, height, 0.4);
      const archMat = new THREE.MeshPhongMaterial({
        color: col,
        emissive: new THREE.Color(col).multiplyScalar(0.5),
        transparent: true,
        opacity: 0.8,
      });
      const arch = new THREE.Mesh(archGeo, archMat);
      arch.position.copy(checkpoint.position.clone().add(perp.clone().multiplyScalar(side * halfW)));
      arch.position.y = height / 2;
      scene.add(arch);
      arches.push(arch);
    }

    // Top bar
    const barGeo = new THREE.BoxGeometry(checkpoint.width + 0.4, 0.4, 0.4);
    const barMat = new THREE.MeshPhongMaterial({
      color: col, emissive: new THREE.Color(col).multiplyScalar(0.5),
      transparent: true, opacity: 0.8,
    });
    const bar = new THREE.Mesh(barGeo, barMat);
    bar.position.copy(checkpoint.position.clone());
    bar.position.y = height;
    bar.rotation.y = Math.atan2(dir.x, dir.z);
    scene.add(bar);
    arches.push(bar);

    return arches;
  }

  const TRACK_LIST = [
    { id: 'city', name: 'Neon City', color: '#00f0ff', desc: 'Urban neon street circuit' },
    { id: 'desert', name: 'Desert Storm', color: '#ff6600', desc: 'Scorched sands of the dunes' },
    { id: 'coastal', name: 'Coastal Circuit', color: '#00ff88', desc: 'Wind along the sea cliffs' },
  ];

  function build(trackId, scene, quality) {
    switch (trackId) {
      case 'city': return buildNeonCity(scene, quality);
      case 'desert': return buildDesertStorm(scene, quality);
      case 'coastal': return buildCoastalCircuit(scene, quality);
      default: return buildNeonCity(scene, quality);
    }
  }

  return { build, buildCheckpointVisuals, TRACK_LIST };
})();
