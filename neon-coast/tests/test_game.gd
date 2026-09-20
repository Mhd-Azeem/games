extends SceneTree
const Career=preload("res://scripts/career.gd")
const Catalog=preload("res://scripts/catalog.gd")
var failures: int=0

func _initialize() -> void:
    call_deferred("run")

func check(condition: bool, message: String) -> void:
    if not condition:
        failures+=1; printerr("TEST FAIL: ",message)
    else: print("PASS: ",message)

func frames(count: int) -> void:
    for i in count: await physics_frame

func run() -> void:
    var path: String="res://tests/.career-test.json"
    for suffix in ["",".bak",".tmp"]:
        if FileAccess.file_exists(path+suffix): DirAccess.remove_absolute(path+suffix)
    var career=Career.new(path)
    check(career.unlocked(0) and not career.unlocked(1),"career locks later events")
    check(not career.buy(2) and not career.upgrade(),"unaffordable purchases are rejected")
    var prize: Dictionary=career.award(0,1)
    check(prize.stars==3 and career.credits==950 and career.unlocked(2),"podium awards credits and unlocks events")
    career.award(0,3)
    check(career.stars()==3,"replays cannot duplicate earned stars")
    career.credits=5000
    check(career.buy(1) and career.selected==1,"eligible car purchase and selection")
    check(career.upgrade() and career.level(1)==1,"performance upgrade purchase")
    var restored=Career.new(path); restored.load_game()
    check(restored.selected==1 and restored.level(1)==1 and restored.stars()==3,"save round trip preserves career")
    var file:=FileAccess.open(path,FileAccess.WRITE); file.store_string("corrupted"); file.close()
    restored=Career.new(path); restored.load_game()
    check(restored.selected==1,"corrupted primary save falls back to backup")
    check(career.award(99,1).is_empty(),"invalid event cannot award rewards")
    var main=load("res://scenes/main.tscn").instantiate()
    root.add_child(main); current_scene=main
    main.career=Career.new(path)
    await process_frame
    var menu_rect: Rect2=main.hud.overlay.get_child(1).get_global_rect()
    check(menu_rect.position.x>=0 and menu_rect.position.y>=0 and menu_rect.end.x<=1281 and menu_rect.end.y<=721,"menu fits the viewport")
    check(main.city.modules.size()==36 and main.traffic.size()>8,"modular city and traffic spawn")
    main.on_action("resume",-1)
    Input.action_press("accelerate"); await frames(100)
    check(main.player.speed>14 and main.player.position.z<15,"player accelerates and drives in the world")
    Input.action_release("accelerate"); Input.action_press("brake"); await frames(35)
    check(main.player.speed<10,"braking reduces road speed")
    Input.action_release("brake"); main.player.respawn(Vector3(0,0,0),Vector3.FORWARD)
    Input.action_press("accelerate"); Input.action_press("boost"); await frames(45)
    check(main.player.nitro<0.95,"nitro consumes a bounded reserve")
    Input.action_release("boost"); Input.action_press("right"); await frames(30)
    check(absf(main.player.rotation.y)>0.1,"steering rotates the moving vehicle")
    Input.action_release("accelerate"); Input.action_release("right")
    main.player.respawn(Vector3(272,0,0),Vector3.RIGHT)
    Input.action_press("accelerate"); await frames(150); Input.action_release("accelerate")
    check(main.player.position.x<282,"world barrier prevents escape")
    var traffic_progress: int=0
    await frames(450)
    for driver in main.traffic_ai: traffic_progress+=driver.passed
    check(traffic_progress>4,"waypoint traffic traverses road intersections")
    main.start_race(0)
    check(main.racers.size()==3 and not main.player.enabled,"race grid and countdown initialize")
    await frames(205)
    check(main.player.enabled,"countdown releases race cars")
    main.player.position=main.route[2]; main.check_checkpoint()
    check(main.passed==0,"out-of-order checkpoint is rejected")
    # Park player out of AI paths while opponents complete a whole race.
    main.player.position=Vector3(-270,0,270)
    await frames(4200)
    var finished: int=0
    for driver in main.racers:
        print("AI progress: ",driver.passed," target ",driver.target," at ",driver.car.position)
        if driver.finish_time>=0: finished+=1
    check(finished>=2,"AI racers can finish a complete event autonomously")
    for i in range(4):
        main.player.position=main.route[main.next_gate]; main.check_checkpoint()
    check(main.active_event==-1 and not main.finish_result.is_empty(),"ordered checkpoints complete race and produce results")
    check(main.career.credits>0,"race completion updates career")
    main.on_action("free",-1); main.on_action("pause",-1)
    check(paused and main.hud.overlay.visible,"pause menu stops simulation")
    main.on_action("resume",-1)
    check(not paused,"resume continues simulation")
    main.hud.touch.active=true; main.hud.touch.layout()
    for entry in [[0,"accelerate"],[1,"left"],[2,"boost"]]:
        var event:=InputEventScreenTouch.new(); event.index=entry[0]; event.pressed=true; event.position=main.hud.touch.zones[entry[1]].get_center(); main.hud.touch._input(event)
    check(Input.is_action_pressed("accelerate") and Input.is_action_pressed("left") and Input.is_action_pressed("boost"),"multi-touch supports three simultaneous controls")
    main.hud.touch.clear()
    check(not Input.is_action_pressed("accelerate") and not Input.is_action_pressed("left"),"opening menu clears held touches")
    main.queue_free(); await process_frame
    for suffix in ["",".bak",".tmp"]:
        if FileAccess.file_exists(path+suffix): DirAccess.remove_absolute(path+suffix)
    print("TEST RESULT: ",failures," failure(s)")
    quit(1 if failures else 0)
