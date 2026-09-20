extends CanvasLayer
signal action_requested(action: String, index: int)
const Catalog = preload("res://scripts/catalog.gd")
const Minimap = preload("res://scripts/minimap.gd")
const Touch = preload("res://scripts/touch_controls.gd")
var root: Control
var overlay: Control
var menu_box: VBoxContainer
var dashboard: Control
var stats: Label
var speed_label: Label
var race_label: Label
var hint: Label
var countdown: Label
var nitro: ProgressBar
var map: Control
var touch: Control
var menu_page: String = "home"
var mobile: bool = OS.has_feature("mobile")

func _ready() -> void:
    process_mode=Node.PROCESS_MODE_ALWAYS
    root=Control.new(); add_child(root); root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); root.mouse_filter=Control.MOUSE_FILTER_IGNORE
    var theme := Theme.new(); theme.default_font_size=18
    theme.set_color("font_color","Label",Color("e7eeeb"))
    theme.set_color("font_color","Button",Color("e7eeeb"))
    for type in ["normal","hover","pressed","focus","disabled"]:
        var style := StyleBoxFlat.new(); style.set_corner_radius_all(8); style.content_margin_left=18; style.content_margin_right=18; style.content_margin_top=10; style.content_margin_bottom=10
        style.bg_color=Color("24464c") if type in ["hover","focus","pressed"] else Color("172e3c")
        if type=="disabled": style.bg_color=Color("18242c")
        style.border_color=Color("4bbaac") if type=="focus" else Color("305160"); style.set_border_width_all(1)
        theme.set_stylebox(type,"Button",style)
    root.theme=theme
    dashboard=Control.new(); root.add_child(dashboard); dashboard.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); dashboard.mouse_filter=Control.MOUSE_FILTER_IGNORE
    var left := VBoxContainer.new(); dashboard.add_child(left); left.position=Vector2(26,20)
    var title := label(left,"NEON / COAST",24); title.modulate=Color("6ee3cb")
    stats=label(left,"",17)
    race_label=label(left,"",18)
    map=Minimap.new(); dashboard.add_child(map); map.position=Vector2(26,140); map.custom_minimum_size=Vector2(210,210); map.mouse_filter=Control.MOUSE_FILTER_IGNORE
    var controls := HBoxContainer.new(); dashboard.add_child(controls); controls.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT); controls.offset_left=-330; controls.offset_top=22; controls.offset_right=-20; controls.offset_bottom=68
    button(controls,"CAM","camera"); button(controls,"RESET","reset"); button(controls,"MENU","pause")
    var lower := VBoxContainer.new(); dashboard.add_child(lower); lower.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT); lower.offset_left=-260; lower.offset_top=-176 if not mobile else -370; lower.offset_right=-34; lower.offset_bottom=-36 if not mobile else -230; lower.custom_minimum_size=Vector2(226,140)
    speed_label=label(lower,"000",58)
    label(lower,"KM/H     //     NITRO",14).modulate=Color("82aaa9")
    nitro=ProgressBar.new(); lower.add_child(nitro); nitro.custom_minimum_size=Vector2(210,7); nitro.show_percentage=false
    hint=Label.new(); dashboard.add_child(hint); hint.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM); hint.offset_left=-460; hint.offset_top=-45; hint.offset_right=460; hint.offset_bottom=-15; hint.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER; hint.add_theme_font_size_override("font_size",14)
    countdown=Label.new(); dashboard.add_child(countdown); countdown.set_anchors_and_offsets_preset(Control.PRESET_CENTER); countdown.offset_left=-200; countdown.offset_top=-120; countdown.offset_right=200; countdown.offset_bottom=40; countdown.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER; countdown.add_theme_font_size_override("font_size",70); countdown.modulate=Color("ffce90")
    touch=Touch.new(); root.add_child(touch); touch.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    overlay=Control.new(); root.add_child(overlay); overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    var shade := ColorRect.new(); overlay.add_child(shade); shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); shade.color=Color(0.015,0.035,0.055,0.84)
    var panel := PanelContainer.new(); overlay.add_child(panel); panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER); panel.offset_left=-450; panel.offset_top=-310; panel.offset_right=450; panel.offset_bottom=310
    var backdrop := StyleBoxFlat.new(); backdrop.bg_color=Color(0.035,0.08,0.12,0.96); backdrop.set_corner_radius_all(20); backdrop.set_border_width_all(1); backdrop.border_color=Color("34555e"); backdrop.content_margin_left=30; backdrop.content_margin_right=30; backdrop.content_margin_top=24; backdrop.content_margin_bottom=24
    panel.add_theme_stylebox_override("panel",backdrop)
    var scroll := ScrollContainer.new(); panel.add_child(scroll); scroll.horizontal_scroll_mode=ScrollContainer.SCROLL_MODE_DISABLED
    menu_box=VBoxContainer.new(); scroll.add_child(menu_box); menu_box.size_flags_horizontal=Control.SIZE_EXPAND_FILL; menu_box.add_theme_constant_override("separation",12)

func label(parent: Node, text: String, font_size: int = 18) -> Label:
    var node := Label.new(); parent.add_child(node); node.text=text; node.add_theme_font_size_override("font_size",font_size)
    return node

func button(parent: Node, text: String, action: String, index: int = -1, disabled: bool = false) -> Button:
    var node := Button.new(); parent.add_child(node); node.text=text; node.disabled=disabled; node.custom_minimum_size.y=44
    node.pressed.connect(func(): action_requested.emit(action,index))
    return node

func begin_menu(title: String, subtitle: String) -> void:
    overlay.show(); dashboard.hide(); touch.active=false; touch.clear(); touch.queue_redraw()
    for child in menu_box.get_children(): menu_box.remove_child(child); child.queue_free()
    label(menu_box,"N E O N   /   C O A S T",15).modulate=Color("60dac5")
    label(menu_box,title,36)
    label(menu_box,subtitle,17).modulate=Color("99b4bd")
    menu_box.add_child(HSeparator.new())

func show_home(career, in_race: bool = false) -> void:
    menu_page="home"
    begin_menu("OWN THE STREETS.","An open city. Five events. One coast champion.")
    label(menu_box,"$%s     /     %s STARS     /     %s" % [career.credits,career.stars(),Catalog.VEHICLES[career.selected].name],18)
    button(menu_box,"RESUME RACE" if in_race else "FREE ROAM  /  Explore Meridian","resume")
    button(menu_box,"CAREER  /  Choose an event","career")
    button(menu_box,"GARAGE  /  Cars & upgrades","garage")
    var row := HBoxContainer.new(); menu_box.add_child(row)
    button(row,"SOUND: ON" if career.sound else "SOUND: OFF","sound")
    button(row,"GRAPHICS: LOW" if career.low_quality else "GRAPHICS: HIGH","quality")
    button(row,"TOUCH: ON" if mobile else "TOUCH: OFF","touch")
    if not OS.has_feature("mobile"): button(row,"QUIT","quit")
    label(menu_box,"WASD / arrows: drive   •   Space: drift   •   Shift: nitro\nC: camera   •   R: recover   •   Esc: menu   •   E: career\nGamepad: left stick, triggers, A drift, B nitro, Start menu",15)
    label(menu_box,"Progress saves automatically. Start with Block Party to earn your first stars.",14).modulate=Color("99b4bd")
    focus_first()

func show_career(career) -> void:
    menu_page="career"; begin_menu("CAREER / CITY SERIES","$%s  •  %s / 15 stars  •  Podium finishes unlock the next event." % [career.credits,career.stars()])
    for i in Catalog.EVENTS.size():
        var event: Dictionary=Catalog.EVENTS[i]; var earned: int=int(career.results.get(str(i),0))
        var text: String="%s    |    %s laps    |    $%s    |    %s/3 stars" % [event.name,event.laps,event.reward,earned]
        if not career.unlocked(i): text+="    [needs %s stars]" % event.stars
        button(menu_box,text,"race",i,not career.unlocked(i))
    label(menu_box,"1st = 3 stars  /  2nd = 2  /  3rd = 1. Replay events for credits.\nFollow the gold checkpoint rings in order. Leaving a race gives no reward.",16)
    if career.stars()>=8 and int(career.results.get("4",0))>0:
        label(menu_box,"COAST CHAMPION  /  Championship completed!",22).modulate=Color("f4c886")
    button(menu_box,"BACK","home"); focus_first()

func show_garage(career) -> void:
    menu_page="garage"; begin_menu("GARAGE / BUILD YOUR RIDE","$%s   •   %s stars   •   Upgrades improve speed, acceleration, and grip." % [career.credits,career.stars()])
    for i in Catalog.VEHICLES.size():
        var car: Dictionary=Catalog.VEHICLES[i]
        var text: String="%s     %s km/h     " % [car.name,int(car.speed*3.6)]
        text+="[SELECTED]" if career.selected==i else ("SELECT" if i in career.owned else "$%s / %s stars" % [car.price,car.stars])
        button(menu_box,text,"buy",i,i not in career.owned and (career.credits<car.price or career.stars()<car.stars))
    var level: int=career.level(career.selected)
    button(menu_box,"PERFORMANCE KIT  %s/3    /    %s" % [level,"MAXED" if level>=3 else "$%s" % Catalog.upgrade_cost(level)],"upgrade",-1,level>=3 or career.credits<Catalog.upgrade_cost(level))
    label(menu_box,"Your selected car and upgrades apply when you leave the garage.\nCredits are earned in career races; no purchases or online account needed.",16)
    button(menu_box,"BACK","home"); focus_first()

func show_result(event_name: String, result: Dictionary, elapsed: float, timed_out: bool) -> void:
    menu_page="results"; begin_menu("TIME LIMIT" if timed_out else "FINISH / P%s" % result.place,event_name)
    label(menu_box,"%02d:%05.2f     +$%s     +%s new stars" % [int(elapsed/60),fmod(elapsed,60),result.credits,result.stars],30)
    label(menu_box,"Keep racing to earn credits and improve your best star rating.",18)
    button(menu_box,"NEXT EVENT / CAREER","career"); button(menu_box,"GARAGE","garage"); button(menu_box,"FREE ROAM","free"); focus_first()

func focus_first() -> void:
    for node in menu_box.get_children():
        if node is Button and not node.disabled:
            node.grab_focus(); return

func hide_menu() -> void:
    overlay.hide(); dashboard.show(); touch.active=mobile; touch.queue_redraw()

func _unhandled_input(event: InputEvent) -> void:
    if overlay.visible and event.is_action_pressed("pause"):
        action_requested.emit("resume" if menu_page=="home" else "home",-1)
        get_viewport().set_input_as_handled()
