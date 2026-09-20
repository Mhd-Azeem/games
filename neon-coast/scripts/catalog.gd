extends RefCounted
## Data-only game catalog; positions describe road centerlines in world meters.

const VEHICLES = [
    {"id": "comet", "name": "COMET / Street coupe", "price": 0, "stars": 0, "speed": 46.0, "accel": 17.0, "grip": 7.5, "color": Color("27cbbd")},
    {"id": "vandal", "name": "VANDAL / Muscle", "price": 2200, "stars": 3, "speed": 55.0, "accel": 20.0, "grip": 5.8, "color": Color("e9a04c")},
    {"id": "spectre", "name": "SPECTRE / GT", "price": 4800, "stars": 7, "speed": 64.0, "accel": 24.0, "grip": 8.6, "color": Color("c65583")}
]
const EVENTS = [
    {"name": "01 / BLOCK PARTY", "district": "Old Quarter", "stars": 0, "reward": 950, "laps": 1, "pace": 17.0, "route": [Vector3(-160,0,0),Vector3(-160,0,-160),Vector3(0,0,-160),Vector3(0,0,0)]},
    {"name": "02 / PALM RUN", "district": "Sunset Beach", "stars": 1, "reward": 1250, "laps": 1, "pace": 20.0, "route": [Vector3(-240,0,160),Vector3(-240,0,-160),Vector3(-80,0,-160),Vector3(-80,0,160)]},
    {"name": "03 / DOWNTOWN HEAT", "district": "Downtown", "stars": 3, "reward": 1700, "laps": 2, "pace": 22.0, "route": [Vector3(0,0,80),Vector3(0,0,-240),Vector3(240,0,-240),Vector3(240,0,0),Vector3(80,0,0),Vector3(80,0,80)]},
    {"name": "04 / DOCKSIDE", "district": "Port Meridian", "stars": 5, "reward": 2100, "laps": 2, "pace": 24.0, "route": [Vector3(0,0,240),Vector3(0,0,80),Vector3(240,0,80),Vector3(240,0,240)]},
    {"name": "05 / COAST CROWN", "district": "City Championship", "stars": 8, "reward": 3400, "laps": 2, "pace": 27.0, "route": [Vector3(-240,0,240),Vector3(-240,0,-240),Vector3(240,0,-240),Vector3(240,0,240)]}
]

static func upgrade_cost(level: int) -> int:
    return 650 + level * 600

static func grid_route(x: float, z: float, width: float = 80.0) -> Array:
    # Clockwise rectangles keep ambient traffic in the right-hand lane.
    return [Vector3(x+3,0,z+3),Vector3(x+width-3,0,z+3),Vector3(x+width-3,0,z+width-3),Vector3(x+3,0,z+width-3)]
