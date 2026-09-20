extends Node3D
const Catalog = preload("res://scripts/catalog.gd")
const Career = preload("res://scripts/career.gd")
const City = preload("res://scripts/city.gd")
const Vehicle = preload("res://scripts/vehicle.gd")
const Driver = preload("res://scripts/ai_driver.gd")
const HUD = preload("res://scripts/hud.gd")
const EngineAudio = preload("res://scripts/engine_audio.gd")
var career = Career.new()
var city: Node3D
var player: CharacterBody3D
var traffic: Array = []
var traffic_ai: Array = []
var racers: Array = []
var hud: CanvasLayer
var camera: Camera3D
var sunlight: DirectionalLight3D
var engine_audio: AudioStreamPlayer
var marker: Node3D
var marker_ring: MeshInstance3D
var active_event: int = -1
var route: Array = []
var next_gate: int = 1
var passed: int = 0
var race_time: float = 0
var countdown_time: float = 0
var camera_mode: int = 0
var recovering: float = 0
var finish_result: Dictionary = {}
var test_mode: bool = false

func _ready() -> void:
    test_mode = "--test-mode" in OS.get_cmdline_user_args()
    configure_input()
    if not test_mode: career.load_game()
    make_environment()
    city=City.new(); add_child(city)
    player=Vehicle.new(); player.is_player=true; add_child(player)
    player.configure(career.selected,career.level(career.selected)); player.respawn(Vector3(-160,0,25),Vector3.FORWARD)
    camera=Camera3D.new(); add_child(camera); camera.current=true; camera.fov=68; camera.far=700
    camera.position=player.position+Vector3(0,5,10); camera.look_at(player.position+Vector3(0,1,0))
    for i in mini(city.traffic_routes.size(),14):
        var car=Vehicle.new(); add_child(car); car.configure(i%3,0,Color.from_hsv(fmod(i*0.173,1),0.25,0.65))
        var points: Array=city.traffic_routes[i]
        car.respawn(points[0],(points[1]-points[0]).normalized())
        traffic.append(car); traffic_ai.append(Driver.new(car,points,12.0+(i%4)*1.5))
    make_marker()
    hud=HUD.new(); add_child(hud); hud.action_requested.connect(on_action)
    hud.map.player=player; hud.map.traffic=traffic
    engine_audio=EngineAudio.new(); engine_audio.car=player; add_child(engine_audio)
    apply_settings()
    hud.show_home(career); get_tree().paused=true

func configure_input() -> void:
    var keys: Dictionary={"accelerate":[KEY_W,KEY_UP],"brake":[KEY_S,KEY_DOWN],"left":[KEY_A,KEY_LEFT],"right":[KEY_D,KEY_RIGHT],"handbrake":[KEY_SPACE],"boost":[KEY_SHIFT],"camera":[KEY_C],"reset":[KEY_R],"pause":[KEY_ESCAPE],"career":[KEY_E]}
    for action in keys:
        if not InputMap.has_action(action): InputMap.add_action(action)
        for key in keys[action]:
            var event:=InputEventKey.new(); event.physical_keycode=key; InputMap.action_add_event(action,event)
    for entry in [["left",JOY_AXIS_LEFT_X,-1.0],["right",JOY_AXIS_LEFT_X,1.0],["accelerate",JOY_AXIS_TRIGGER_RIGHT,1.0],["brake",JOY_AXIS_TRIGGER_LEFT,1.0]]:
        var motion:=InputEventJoypadMotion.new(); motion.axis=entry[1]; motion.axis_value=entry[2]; InputMap.action_add_event(entry[0],motion)
    for entry in [["handbrake",JOY_BUTTON_A],["boost",JOY_BUTTON_B],["camera",JOY_BUTTON_Y],["reset",JOY_BUTTON_X],["pause",JOY_BUTTON_START]]:
        var button:=InputEventJoypadButton.new(); button.button_index=entry[1]; InputMap.action_add_event(entry[0],button)

func make_environment() -> void:
    var environment:=WorldEnvironment.new(); var env:=Environment.new()
    var sky:=Sky.new(); var material:=ProceduralSkyMaterial.new()
    material.sky_top_color=Color("477b99"); material.sky_horizon_color=Color("e7b98e")
    material.ground_bottom_color=Color("626968"); material.ground_horizon_color=Color("e7b98e")
    sky.sky_material=material; env.sky=sky; env.background_mode=Environment.BG_SKY
    env.ambient_light_source=Environment.AMBIENT_SOURCE_COLOR; env.ambient_light_color=Color("adc4cb"); env.ambient_light_energy=0.38
    env.tonemap_mode=Environment.TONE_MAPPER_FILMIC
    environment.environment=env; add_child(environment)
    sunlight=DirectionalLight3D.new(); sunlight.rotation_degrees=Vector3(-36,-32,0)
    sunlight.light_color=Color("ffe0b5"); sunlight.light_energy=0.85; sunlight.shadow_enabled=true
    sunlight.directional_shadow_max_distance=110; add_child(sunlight)

func make_marker() -> void:
    marker=Node3D.new(); add_child(marker)
    marker_ring=MeshInstance3D.new(); marker.add_child(marker_ring)
    var torus:=TorusMesh.new(); torus.inner_radius=5.4; torus.outer_radius=5.8; torus.rings=32; torus.ring_segments=8
    marker_ring.mesh=torus; marker_ring.rotation.x=PI/2; marker_ring.position.y=4.5
    var mat:=StandardMaterial3D.new(); mat.albedo_color=Color("ffd08c"); mat.shading_mode=BaseMaterial3D.SHADING_MODE_UNSHADED
    marker_ring.material_override=mat; marker.hide()

func _unhandled_input(event: InputEvent) -> void:
    if event.is_action_pressed("pause"):
        on_action("pause",-1)
    elif event.is_action_pressed("camera"): camera_mode=(camera_mode+1)%3
    elif event.is_action_pressed("reset"): recover_player()
    elif event.is_action_pressed("career"):
        get_tree().paused=true; hud.show_career(career)

func _notification(what: int) -> void:
    if what==NOTIFICATION_APPLICATION_FOCUS_OUT and is_instance_valid(hud) and not test_mode:
        get_tree().paused=true; hud.show_home(career,active_event>=0)
        for action in ["accelerate","brake","left","right","boost","handbrake"]: Input.action_release(action)

func on_action(action: String, index: int) -> void:
    match action:
        "resume":
            hud.hide_menu(); get_tree().paused=false
        "free":
            clear_race(); hud.hide_menu(); get_tree().paused=false
        "pause","home":
            get_tree().paused=true; hud.show_home(career,active_event>=0)
        "career":
            get_tree().paused=true; hud.show_career(career)
        "garage":
            get_tree().paused=true; hud.show_garage(career)
        "race":
            start_race(index)
        "buy":
            if career.buy(index): player.configure(career.selected,career.level(career.selected))
            hud.show_garage(career)
        "upgrade":
            if career.upgrade(): player.configure(career.selected,career.level(career.selected))
            hud.show_garage(career)
        "sound":
            career.sound=not career.sound; career.save_game(); apply_settings(); hud.show_home(career,active_event>=0)
        "quality":
            career.low_quality=not career.low_quality; career.save_game(); apply_settings(); hud.show_home(career,active_event>=0)
        "touch":
            hud.mobile=not hud.mobile; hud.show_home(career,active_event>=0)
        "camera": camera_mode=(camera_mode+1)%3
        "reset": recover_player()
        "quit": get_tree().quit()

func apply_settings() -> void:
    sunlight.shadow_enabled=not career.low_quality
    get_viewport().msaa_3d=Viewport.MSAA_DISABLED if career.low_quality else Viewport.MSAA_2X
    engine_audio.volume_db=-23 if career.sound else -80

func clear_race() -> void:
    for driver in racers:
        driver.car.queue_free()
    racers.clear(); active_event=-1; route.clear(); marker.hide(); hud.map.route=[]; hud.map.racers=[]
    player.enabled=true; countdown_time=0; hud.countdown.text=""

func start_race(index: int) -> void:
    if not career.unlocked(index): return
    clear_race(); active_event=index
    var event: Dictionary=Catalog.EVENTS[index]
    route=event.route.duplicate(); next_gate=1; passed=0; race_time=0; countdown_time=3.2; finish_result={}
    var forward: Vector3=(route[1]-route[0]).normalized(); var right: Vector3=forward.cross(Vector3.UP)
    player.respawn(route[0]+right*3,forward); player.nitro=1; player.enabled=false
    for i in range(3):
        var car=Vehicle.new(); add_child(car); car.configure(mini(2,index/2),0,Color.from_hsv(0.02+i*0.25,0.65,0.9))
        var stagger: Vector3=right*(-3 if i%2==0 else 3)-forward*(5+floori(i/2.0)*6)
        car.respawn(route[0]+stagger,forward); car.enabled=false
        var driver=Driver.new(car,route,event.pace+i*1.1,true); driver.total_gates=route.size()*event.laps
        racers.append(driver)
    # Clear traffic off the starting grid without removing city traffic.
    for driver in traffic_ai:
        if driver.car.position.distance_to(route[0])<24:
            driver.car.respawn(driver.route[2],(driver.route[3]-driver.route[2]).normalized()); driver.target=3
    update_marker(); hud.map.route=route; hud.map.racers=racers; hud.hide_menu(); get_tree().paused=false
    camera.position=player.position-forward*10+Vector3.UP*5

func update_marker() -> void:
    marker.show(); marker.position=route[next_gate]
    var previous: Vector3=route[(next_gate-1+route.size())%route.size()]
    var direction: Vector3=(route[next_gate]-previous).normalized()
    marker.rotation.y=atan2(direction.x,direction.z)
    hud.map.next_gate=next_gate

func player_score() -> float:
    if route.is_empty(): return 0
    var previous: Vector3=route[(next_gate-1+route.size())%route.size()]
    var segment: Vector3=route[next_gate]-previous
    return passed+clampf((player.position-previous).dot(segment)/maxf(segment.length_squared(),1),0,0.999)

func place() -> int:
    var result: int=1
    for driver in racers:
        if driver.finish_time>=0 or driver.score()>player_score(): result+=1
    return result

func check_checkpoint() -> void:
    if active_event<0: return
    var offset: Vector3=player.position-route[next_gate]; offset.y=0
    if offset.length()>13: return
    passed+=1; next_gate=(next_gate+1)%route.size()
    if passed>=route.size()*Catalog.EVENTS[active_event].laps:
        finish_race(false)
    else: update_marker()

func finish_race(timed_out: bool) -> void:
    var event_name: String=Catalog.EVENTS[active_event].name
    var rank: int=4 if timed_out else place()
    # Successful completion is the only place that awards money/stars.
    finish_result=career.award(active_event,rank) if not timed_out else {"credits":0,"stars":0,"place":4}
    var elapsed: float=race_time
    clear_race(); get_tree().paused=true; hud.show_result(event_name,finish_result,elapsed,timed_out)

func recover_player() -> void:
    if recovering>0: return
    if active_event>=0:
        var previous: Vector3=route[(next_gate-1+route.size())%route.size()]
        player.respawn(previous,(route[next_gate]-previous).normalized()); race_time+=3
    else:
        var pos: Vector3=player.position
        if absf(pos.x-roundf(pos.x/80)*80)<absf(pos.z-roundf(pos.z/80)*80):
            pos.x=clampf(roundf(pos.x/80)*80,-240,240); pos.z=clampf(pos.z,-250,250)
        else:
            pos.z=clampf(roundf(pos.z/80)*80,-240,240); pos.x=clampf(pos.x,-250,250)
        pos.y=0; player.respawn(pos,Vector3.FORWARD)
    recovering=2

func _physics_process(delta: float) -> void:
    if not is_instance_valid(hud): return
    recovering=maxf(0,recovering-delta)
    player.throttle=Input.get_action_strength("accelerate")-Input.get_action_strength("brake")
    player.steering=Input.get_axis("left","right"); player.handbrake=Input.is_action_pressed("handbrake"); player.boost=Input.is_action_pressed("boost")
    for driver in traffic_ai: driver.step(delta)
    if active_event>=0:
        if countdown_time>0:
            countdown_time=maxf(0,countdown_time-delta); hud.countdown.text=str(ceili(countdown_time))
            if countdown_time<=0:
                player.enabled=true
                for driver in racers: driver.car.enabled=true
        else:
            hud.countdown.text="GO" if race_time<0.65 else ""
            race_time+=delta
            for driver in racers: driver.step(delta,race_time)
            check_checkpoint()
            if active_event>=0 and race_time>480: finish_race(true)
    update_hud()

func update_hud() -> void:
    hud.speed_label.text="%03d" % int(absf(player.speed)*3.6)
    hud.nitro.value=player.nitro*100
    hud.stats.text="%s\n$%s  /  %s STARS" % [City.district(player.position),career.credits,career.stars()]
    if active_event>=0:
        var lap: int=mini(Catalog.EVENTS[active_event].laps,passed/route.size()+1)
        hud.race_label.text="POSITION %s / 4    LAP %s / %s\n%02d:%05.2f  •  CHECKPOINT %s / %s" % [place(),lap,Catalog.EVENTS[active_event].laps,int(race_time/60),fmod(race_time,60),passed+1,route.size()*Catalog.EVENTS[active_event].laps]
        hud.hint.text="FOLLOW THE GOLD RINGS  /  R: recover (+3 seconds)"
    else:
        hud.race_label.text="FREE ROAM / MERIDIAN"
        hud.hint.text="E: career    C: camera    Shift: nitro    Space: drift    Esc: menu" if not hud.mobile else "MENU: career, garage & settings"
    hud.map.queue_redraw()

func _process(delta: float) -> void:
    if not is_instance_valid(player) or not is_instance_valid(camera): return
    var forward: Vector3=-player.global_transform.basis.z
    var look: Vector3=player.position+Vector3.UP*1.3+forward*6
    var target: Vector3=player.position-forward*(9+absf(player.speed)*0.045)+Vector3.UP*4
    if camera_mode==1:
        target=player.position+Vector3.UP*1.62+forward*0.55; look=target+forward*40
    elif camera_mode==2:
        target=player.position-forward*4+Vector3.UP*20; look=player.position+forward*7
    if camera_mode==0:
        var query:=PhysicsRayQueryParameters3D.create(player.position+Vector3.UP*1.5,target,1)
        var hit: Dictionary=get_world_3d().direct_space_state.intersect_ray(query)
        if not hit.is_empty(): target=hit.position+hit.normal*0.4
    camera.position=target if camera_mode==1 else camera.position.lerp(target,1-exp(-delta*7))
    camera.look_at(look)
    camera.fov=lerpf(camera.fov,77.0 if player.boost_active else 68.0,delta*3)
    if marker.visible: marker_ring.rotate_y(delta*0.3)
