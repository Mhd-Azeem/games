extends Node3D
const Models = preload("res://scripts/models.gd")
const Catalog = preload("res://scripts/catalog.gd")
const SPACING: float = 80.0
const LIMIT: float = 280.0
var traffic_routes: Array = []
var modules: Array[Node3D] = []

func _ready() -> void:
    build()

func build() -> void:
    var rng := RandomNumberGenerator.new(); rng.seed = 2049
    Models.box(self,Vector3(0,-0.5,0),Vector3(620,1,620),Color("a4a285"),true)
    Models.box(self,Vector3(-400,-1.2,0),Vector3(200,1,1000),Color("287b8b"))
    Models.box(self,Vector3(-292,-0.05,0),Vector3(36,0.12,580),Color("d2bb8e"))
    # Roads are shared seams. Blocks are independent reusable modules.
    for i in range(-3,4):
        var p: float = i*SPACING
        Models.box(self,Vector3(p,-0.04,0),Vector3(17,0.12,560),Color("343e49"))
        Models.box(self,Vector3(0,-0.03,p),Vector3(560,0.12,17),Color("343e49"))
        for mark in range(-27,28):
            var d: float = mark*10
            if fposmod(d+8,80)<16: continue
            Models.box(self,Vector3(p,0.04,d),Vector3(0.13,0.025,3.6),Color("d7c58b"))
            Models.box(self,Vector3(d,0.05,p),Vector3(3.6,0.025,0.13),Color("d7c58b"))
    for ix in range(-3,3):
        for iz in range(-3,3):
            var block := Node3D.new(); block.name = "Block_%s_%s" % [ix+3,iz+3]
            add_child(block); block.position = Vector3(ix*80+40,0,iz*80+40); modules.append(block)
            Models.box(block,Vector3(0,0.12,0),Vector3(62,0.35,62),Color("adb0a2"),true)
            var district: int = 0 if ix<0 else (2 if iz>0 else 1)
            if ix==-1 and iz==0:
                park(block)
            elif district==2:
                Models.building(block,Vector3(0,0.3,3),Vector3(40,9,35),Color("9c9180"),2)
                for c in range(4):
                    Models.box(block,Vector3(-21+c*13,1.8,-23),Vector3(10,3,4),Color("547d84") if c%2 else Color("b46a50"),true)
            else:
                for x in [-16,16]:
                    for z in [-16,16]:
                        var h: float = rng.randf_range(6,12) if district==0 else rng.randf_range(18,47)
                        var color := Color("d0b498") if district==0 else Color("7f98a2")
                        color = color.darkened(rng.randf_range(0,0.2))
                        Models.building(block,Vector3(x,0.3,z),Vector3(rng.randf_range(16,24),h,rng.randf_range(16,24)),color,district)
            for edge in [-1,1]:
                Models.palm(block,Vector3(edge*27,0.3,-25),rng.randf_range(6,9))
            # Only nearby modules cast shadows / render; collisions remain present.
            for node in block.get_children():
                if node is GeometryInstance3D:
                    node.visibility_range_end = 250
                    node.visibility_range_end_margin = 30
            if (ix+iz)%2==0: traffic_routes.append(Catalog.grid_route(ix*80,iz*80))
    for i in range(-12,13):
        Models.palm(self,Vector3(-264,0,i*21),8)
        Models.box(self,Vector3(-275,0.5,i*21),Vector3(0.25,1,14),Color("e1caa4"),true)
    # Collision boundaries keep the full drivable world above the ground plane.
    for x in [-282,282]: Models.box(self,Vector3(x,0.8,0),Vector3(1,1.6,564),Color("c4bca7"),true)
    for z in [-282,282]: Models.box(self,Vector3(0,0.8,z),Vector3(564,1.6,1),Color("c4bca7"),true)
    for i in range(12):
        var hill := Models.cylinder(self,Vector3(380+i*35,15,-370+i*52),90,100,Color("718b87"),0)
        hill.rotation.z = 0.1
    make_sign(Vector3(-255,7,-80),"NEON COAST",Color("f8d39c"))
    make_sign(Vector3(105,12,-110),"MERIDIAN",Color("3dd3c6"))
    make_sign(Vector3(110,11,130),"PORT / 04",Color("f8d39c"))
    # Garage marker is visible in free roam and on the minimap.
    Models.box(self,Vector3(-160,0.07,27),Vector3(13,0.08,9),Color("216e70"))
    make_sign(Vector3(-160,4,30),"GARAGE",Color("65e6cf"))

func park(block: Node3D) -> void:
    Models.box(block,Vector3(0,0.34,0),Vector3(54,0.15,54),Color("6b936f"))
    Models.cylinder(block,Vector3(0,0.7,0),7,0.8,Color("c5b9a3"))
    Models.cylinder(block,Vector3(0,1.15,0),6,0.12,Color("428f9b"))
    for x in [-20,20]:
        for z in [-20,20]: Models.palm(block,Vector3(x,0.4,z),9)

func make_sign(pos: Vector3, text: String, color: Color) -> void:
    var label := Label3D.new(); add_child(label); label.position = pos
    label.text = text; label.font_size = 80; label.pixel_size = 0.016
    label.modulate = color; label.outline_size = 12; label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    label.visibility_range_end = 190

static func district(pos: Vector3) -> String:
    if pos.x < -195: return "SUNSET BEACH"
    if pos.x < 0: return "OLD QUARTER"
    if pos.z > 70: return "PORT MERIDIAN"
    return "DOWNTOWN"

static func on_road(pos: Vector3) -> bool:
    return absf(pos.x-roundf(pos.x/80)*80)<9 or absf(pos.z-roundf(pos.z/80)*80)<9
