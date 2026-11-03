extends RefCounted
## Versioned, validated local save. Atomic replacement retains one backup.
const Catalog = preload("res://scripts/catalog.gd")
var path: String
var credits: int = 0
var results: Dictionary = {}
var owned: Array = [0]
var selected: int = 0
var upgrades: Dictionary = {}
var sound: bool = true
var low_quality: bool = OS.has_feature("mobile")

func _init(save_path: String = "user://career.json") -> void:
    path = save_path

func stars() -> int:
    var total: int = 0
    for value in results.values():
        total += int(value)
    return total

func level(car: int) -> int:
    return int(upgrades.get(str(car), 0))

func unlocked(event: int) -> bool:
    return event >= 0 and event < Catalog.EVENTS.size() and stars() >= Catalog.EVENTS[event].stars

func award(event: int, place: int) -> Dictionary:
    if not unlocked(event) or place < 1 or place > 4:
        return {}
    var earned: int = maxi(0, 4-place)
    var old: int = int(results.get(str(event),0))
    results[str(event)] = maxi(old, earned)
    var prize: int = int(Catalog.EVENTS[event].reward * [1.0,0.65,0.4,0.15][place-1])
    credits += prize
    save_game()
    return {"credits":prize,"stars":maxi(0,earned-old),"place":place}

func buy(car: int) -> bool:
    if car < 0 or car >= Catalog.VEHICLES.size():
        return false
    if car not in owned:
        var data: Dictionary = Catalog.VEHICLES[car]
        if credits < data.price or stars() < data.stars:
            return false
        credits -= data.price
        owned.append(car)
    selected = car
    save_game()
    return true

func upgrade() -> bool:
    var current: int = level(selected)
    var cost: int = Catalog.upgrade_cost(current)
    if current >= 3 or credits < cost:
        return false
    credits -= cost
    upgrades[str(selected)] = current+1
    save_game()
    return true

func save_game() -> bool:
    var data: Dictionary = {"version":1,"credits":credits,"results":results,"owned":owned,"selected":selected,"upgrades":upgrades,"sound":sound,"low_quality":low_quality}
    var file := FileAccess.open(path+".tmp",FileAccess.WRITE)
    if file == null:
        push_warning("Career could not be saved: " + str(FileAccess.get_open_error()))
        return false
    file.store_string(JSON.stringify(data)); file.flush(); file.close()
    if FileAccess.file_exists(path):
        DirAccess.copy_absolute(path,path+".bak")
    var error := DirAccess.rename_absolute(path+".tmp",path)
    return error == OK

func load_game() -> void:
    for candidate in [path,path+".bak"]:
        if not FileAccess.file_exists(candidate):
            continue
        var parser:=JSON.new()
        if parser.parse(FileAccess.get_file_as_string(candidate))!=OK:
            continue
        var parsed = parser.data
        if parsed is Dictionary and parsed.get("version",0) == 1:
            _apply(parsed)
            return

func _apply(data: Dictionary) -> void:
    credits = clampi(int(data.get("credits",0)),0,9999999)
    results.clear(); upgrades.clear(); owned = [0]
    var saved_results = data.get("results",{})
    if saved_results is Dictionary:
        for i in range(Catalog.EVENTS.size()):
            results[str(i)] = clampi(int(saved_results.get(str(i),0)),0,3)
    var saved_owned = data.get("owned",[])
    if saved_owned is Array:
        for car in saved_owned:
            if (car is int or car is float) and int(car) >= 0 and int(car) < Catalog.VEHICLES.size() and int(car) not in owned:
                owned.append(int(car))
    var saved_upgrades = data.get("upgrades",{})
    if saved_upgrades is Dictionary:
        for car in owned:
            upgrades[str(car)] = clampi(int(saved_upgrades.get(str(car),0)),0,3)
    selected = int(data.get("selected",0))
    if selected not in owned:
        selected = 0
    sound = bool(data.get("sound",true))
    low_quality = bool(data.get("low_quality",low_quality))
