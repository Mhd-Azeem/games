extends Control
var player: Node3D
var traffic: Array = []
var racers: Array = []
var route: Array = []
var next_gate: int = 1

func point(pos: Vector3) -> Vector2:
    return Vector2(105,105)+Vector2(pos.x,pos.z)*0.34

func _draw() -> void:
    draw_style_box(background(),Rect2(Vector2.ZERO,Vector2(210,210)))
    draw_rect(Rect2(2,2,14,206),Color("256676"))
    for i in range(-3,4):
        var p: float = 105+i*80*0.34
        draw_line(Vector2(p,10),Vector2(p,200),Color("50616a"),3)
        draw_line(Vector2(10,p),Vector2(200,p),Color("50616a"),3)
    if route.size()>1:
        for i in route.size(): draw_line(point(route[i]),point(route[(i+1)%route.size()]),Color("f6bb73"),2,true)
        draw_circle(point(route[next_gate%route.size()]),5,Color("fff4cc"))
    draw_rect(Rect2(point(Vector3(-160,0,27))-Vector2(3,3),Vector2(6,6)),Color("53e0c5"))
    for car in traffic:
        if is_instance_valid(car): draw_circle(point(car.position),1.7,Color("8e9fa8"))
    for driver in racers:
        if is_instance_valid(driver.car) and driver.finish_time<0: draw_circle(point(driver.car.position),3,Color("ff7277"))
    if is_instance_valid(player):
        var center: Vector2 = point(player.position)
        var forward: Vector3 = -player.global_transform.basis.z
        var f := Vector2(forward.x,forward.z)
        var side := Vector2(-f.y,f.x)
        draw_colored_polygon(PackedVector2Array([center+f*7,center-f*4+side*4,center-f*4-side*4]),Color("5bffe0"))

func background() -> StyleBoxFlat:
    var style := StyleBoxFlat.new(); style.bg_color=Color(0.035,0.075,0.11,0.93)
    style.border_color=Color("345563"); style.set_border_width_all(1); style.set_corner_radius_all(14)
    return style
