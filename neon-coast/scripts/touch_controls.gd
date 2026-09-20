extends Control
## Independent touch IDs permit simultaneous steering, throttle, and boost.
var fingers: Dictionary = {}
var zones: Dictionary = {}
var active: bool = false

func _ready() -> void:
    mouse_filter = Control.MOUSE_FILTER_IGNORE

func layout() -> void:
    var v: Vector2 = get_viewport_rect().size
    zones = {"left":Rect2(24,v.y-120,86,86),"right":Rect2(122,v.y-120,86,86),"brake":Rect2(v.x-222,v.y-120,88,86),"accelerate":Rect2(v.x-122,v.y-160,98,126),"handbrake":Rect2(v.x-222,v.y-218,88,80),"boost":Rect2(v.x-122,v.y-258,98,86)}

func _input(event: InputEvent) -> void:
    if not active: return
    layout()
    if event is InputEventScreenTouch:
        if event.pressed:
            for action in zones:
                if zones[action].has_point(event.position):
                    fingers[event.index]=action; Input.action_press(action); get_viewport().set_input_as_handled(); break
        elif fingers.has(event.index):
            var action: String = fingers[event.index]; fingers.erase(event.index)
            if action not in fingers.values(): Input.action_release(action)
            get_viewport().set_input_as_handled()
        queue_redraw()

func clear() -> void:
    for action in fingers.values(): Input.action_release(action)
    fingers.clear(); queue_redraw()

func _draw() -> void:
    if not active: return
    layout()
    var labels: Dictionary = {"left":"<","right":">","brake":"BRAKE","accelerate":"GAS","handbrake":"DRIFT","boost":"NITRO"}
    for action in zones:
        var rect: Rect2 = zones[action]
        var style := StyleBoxFlat.new(); style.set_corner_radius_all(16); style.set_border_width_all(2)
        style.bg_color = Color(0.08,0.26,0.3,0.84) if action in fingers.values() else Color(0.025,0.065,0.1,0.65)
        style.border_color = Color(0.4,0.85,0.78,0.75)
        draw_style_box(style,rect)
        var text: String = labels[action]; var font := ThemeDB.fallback_font
        var text_size: Vector2 = font.get_string_size(text,HORIZONTAL_ALIGNMENT_LEFT,-1,18)
        draw_string(font,rect.get_center()-Vector2(text_size.x/2,-6),text,HORIZONTAL_ALIGNMENT_LEFT,-1,18,Color("d7f3e9"))
