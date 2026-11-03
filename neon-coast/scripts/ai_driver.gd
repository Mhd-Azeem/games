extends RefCounted
## Waypoint pursuit, corner speed control, obstacle sensing, and stuck recovery.
var car: CharacterBody3D
var route: Array = []
var target: int = 1
var passed: int = 0
var pace: float = 18
var racer: bool = false
var finish_time: float = -1
var stuck: float = 0
var total_gates: int = 999999

func _init(vehicle: CharacterBody3D, points: Array, target_speed: float, competitive: bool = false) -> void:
    car = vehicle; route = points.duplicate(); pace = target_speed; racer = competitive

func step(delta: float, race_time: float = 0) -> void:
    if route.size()<2 or finish_time>=0:
        car.throttle = 0; car.handbrake = true
        return
    var delta_pos: Vector3 = route[target]-car.global_position; delta_pos.y=0
    var distance: float = delta_pos.length()
    if distance<11:
        passed += 1; target = (target+1)%route.size()
        if racer and passed>=total_gates:
            finish_time = race_time
            car.enabled=false; car.visible=false; car.collision_layer=0; car.collision_mask=0
            return
        delta_pos = route[target]-car.global_position; delta_pos.y=0; distance=delta_pos.length()
    var local: Vector3 = car.global_transform.basis.inverse()*delta_pos.normalized()
    var angle: float = atan2(local.x,-local.z)
    car.steering = clampf(angle*1.6,-1,1)
    var desired: float = pace*clampf(1-absf(angle)*0.52,0.27,1)
    # Slow before a turn, then accelerate along the next straight.
    if distance<32: desired = minf(desired,12+distance*0.15)
    var origin: Vector3 = car.global_position+Vector3.UP
    var query := PhysicsRayQueryParameters3D.create(origin,origin-car.global_transform.basis.z*(6+absf(car.speed)*0.45),3,[car.get_rid()])
    var hit: Dictionary = car.get_world_3d().direct_space_state.intersect_ray(query)
    if not hit.is_empty(): desired = 0
    car.throttle = clampf((desired-car.speed)*0.35,-1,1)
    car.handbrake = false
    if absf(car.speed)<1.3: stuck += delta
    else: stuck = 0
    # Reverse briefly to recover from bumper contact; respawn only after 9 seconds.
    if stuck>2.5:
        car.throttle = -0.7; car.steering = -signf(angle)
    if stuck>9:
        var previous: Vector3 = route[(target-1+route.size())%route.size()]
        car.respawn(previous,(route[target]-previous).normalized()); stuck = 0

func score() -> float:
    var previous: Vector3 = route[(target-1+route.size())%route.size()]
    var segment: Vector3 = route[target]-previous
    var along: float = (car.global_position-previous).dot(segment)/maxf(segment.length_squared(),1)
    return passed+clampf(along,0,0.999)
