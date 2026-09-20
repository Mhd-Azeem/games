extends CharacterBody3D
## Shared arcade vehicle controller used by the player, traffic, and racers.
const Models = preload("res://scripts/models.gd")
const Catalog = preload("res://scripts/catalog.gd")
const City = preload("res://scripts/city.gd")
var throttle: float = 0
var steering: float = 0
var handbrake: bool = false
var boost: bool = false
var nitro: float = 1
var max_speed: float = 46
var acceleration: float = 17
var grip: float = 7.5
var speed: float = 0
var travel: float = 0
var steering_visual: float = 0
var model: Node3D
var wheels: Array = []
var enabled: bool = true
var is_player: bool = false
var car_index: int = 0
var boost_active: bool = false

func _ready() -> void:
    collision_layer = 2; collision_mask = 3
    floor_snap_length = 0.4; floor_max_angle = deg_to_rad(50)
    var collider := CollisionShape3D.new(); var shape := BoxShape3D.new()
    shape.size = Vector3(1.9,1.5,4.1); collider.shape = shape; collider.position.y = 0.75; add_child(collider)
    configure(car_index)

func configure(index: int, upgrade: int = 0, tint: Color = Color.TRANSPARENT) -> void:
    car_index = clampi(index,0,2)
    var data: Dictionary = Catalog.VEHICLES[car_index]
    max_speed = data.speed + upgrade*3.5; acceleration = data.accel+upgrade*1.8; grip = data.grip+upgrade*0.45
    if is_instance_valid(model):
        remove_child(model); model.queue_free()
    var visual: Dictionary = Models.car(self,data.color if tint==Color.TRANSPARENT else tint,car_index)
    model = visual.root; wheels = visual.wheels

func respawn(pos: Vector3, forward: Vector3) -> void:
    global_position = pos+Vector3.UP*0.25
    rotation = Vector3(0,atan2(-forward.x,-forward.z),0)
    velocity = Vector3.ZERO; speed = 0; throttle = 0; steering = 0; handbrake = false; boost = false

func _physics_process(delta: float) -> void:
    if not enabled:
        velocity = Vector3.ZERO; speed = 0; boost_active = false
        return
    var forward: Vector3 = -global_transform.basis.z
    var lateral: Vector3 = global_transform.basis.x
    speed = velocity.dot(forward)
    var side_speed: float = velocity.dot(lateral)
    var road: bool = City.on_road(global_position)
    var current_grip: float = grip*(0.24 if handbrake else 1.0)*(1.0 if road else 0.6)
    var engine: float = throttle*acceleration
    if throttle*speed < -0.5: engine *= 2.3
    boost_active = boost and throttle>0 and nitro>0.03 and is_player
    if boost_active:
        engine += 19; nitro = maxf(0,nitro-delta*0.23)
    else: nitro = minf(1,nitro+delta*0.09)
    speed = move_toward(speed,0,delta*(1.6+absf(speed)*0.027+(16.0 if handbrake else 0.0)))
    speed += engine*delta
    var cap: float = max_speed*(1.24 if boost_active else 1.0)*(1.0 if road else 0.53)
    if absf(speed)>cap: speed = move_toward(speed,signf(speed)*cap,delta*30)
    speed = clampf(speed,-12,max_speed*1.24)
    var steering_rate: float = 1.6/(1+absf(speed)*0.035)
    rotation.y -= steering*steering_rate*clampf(speed/9,-1,1)*delta*(1.45 if handbrake else 1.0)
    forward = -global_transform.basis.z; lateral = global_transform.basis.x
    side_speed = velocity.dot(lateral)
    side_speed = move_toward(side_speed,0,delta*current_grip*maxf(2,absf(side_speed)))
    velocity = forward*speed+lateral*side_speed+Vector3.UP*(velocity.y-20*delta)
    if is_on_floor(): velocity.y = -0.5
    move_and_slide()
    travel += absf(speed)*delta
    steering_visual = lerpf(steering_visual,steering,minf(1,delta*9))
    model.rotation.z = lerpf(model.rotation.z,-steering_visual*speed*0.0018,minf(1,delta*6))
    for i in wheels.size():
        wheels[i].rotation.y = -steering_visual*0.4 if i<2 else 0
        # Spin the tire/rim assembly around its local axle, leaving steering independent.
        for part in wheels[i].get_children(): part.rotation.x -= speed*delta/0.43
    if global_position.y < -8:
        respawn(Vector3(-160,0,0),Vector3.FORWARD)
