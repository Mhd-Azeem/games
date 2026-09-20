extends RefCounted
## Original modular geometry; no external art dependencies.
static var materials: Dictionary = {}

static func material(color: Color, metallic: float = 0.0) -> StandardMaterial3D:
    var key: String = color.to_html() + str(metallic)
    if materials.has(key):
        return materials[key]
    var mat := StandardMaterial3D.new()
    mat.albedo_color = color; mat.metallic = metallic
    mat.roughness = 0.3 if metallic > 0 else 0.85
    materials[key] = mat
    return mat

static func box(parent: Node3D, pos: Vector3, size: Vector3, color: Color, collision: bool = false, metallic: float = 0.0) -> MeshInstance3D:
    var node := MeshInstance3D.new()
    var shape := BoxMesh.new(); shape.size = size
    node.mesh = shape; node.material_override = material(color,metallic)
    parent.add_child(node); node.position = pos
    if collision:
        var body := StaticBody3D.new(); body.collision_layer = 1; body.collision_mask = 0
        var collider := CollisionShape3D.new(); var bounds := BoxShape3D.new(); bounds.size = size
        collider.shape = bounds; body.add_child(collider); node.add_child(body)
    return node

static func cylinder(parent: Node3D, pos: Vector3, radius: float, height: float, color: Color, top: float = -1) -> MeshInstance3D:
    var node := MeshInstance3D.new(); var shape := CylinderMesh.new()
    shape.bottom_radius = radius; shape.top_radius = radius if top < 0 else top
    shape.height = height; shape.radial_segments = 10; shape.rings = 1
    node.mesh = shape; node.material_override = material(color)
    parent.add_child(node); node.position = pos
    return node

static func car(parent: Node3D, color: Color, variant: int = 0) -> Dictionary:
    var root := Node3D.new(); parent.add_child(root)
    var length: float = 4.5 if variant == 1 else 4.2
    box(root,Vector3(0,0.65,0),Vector3(1.92,0.5,length),color, false,0.55)
    box(root,Vector3(0,0.42,0),Vector3(1.97,0.16,length-0.1),Color("14202c"))
    # Sloped windshield and tapered roof using a custom indexed surface.
    var vertices: Array[Vector3] = [Vector3(-0.82,0.88,-0.85),Vector3(0.82,0.88,-0.85),Vector3(0.82,0.88,1.25),Vector3(-0.82,0.88,1.25),Vector3(-0.66,1.48,-0.30),Vector3(0.66,1.48,-0.30),Vector3(0.66,1.48,0.73),Vector3(-0.66,1.48,0.73)]
    var st := SurfaceTool.new(); st.begin(Mesh.PRIMITIVE_TRIANGLES)
    for face in [[0,4,5,1],[1,5,6,2],[2,6,7,3],[3,7,4,0]]:
        for i in [0,1,2,0,2,3]: st.add_vertex(vertices[face[i]])
    st.generate_normals()
    var glass := MeshInstance3D.new(); glass.mesh = st.commit()
    var glass_material := material(Color("203a4b"),0.6).duplicate() as StandardMaterial3D
    glass_material.cull_mode=BaseMaterial3D.CULL_DISABLED
    glass.material_override=glass_material; root.add_child(glass)
    box(root,Vector3(0,1.49,0.21),Vector3(1.38,0.08,1.13),color,false,0.6)
    box(root,Vector3(0,0.94,-1.39),Vector3(1.75,0.12,1.22),color,false,0.5)
    for x in [-0.56,0.56]:
        var head := box(root,Vector3(x,0.73,-length/2-0.012),Vector3(0.53,0.16,0.04),Color("fff0cf"))
        var glow := material(Color("fff0cf")).duplicate() as StandardMaterial3D
        glow.emission_enabled = true; glow.emission = Color("ffe1a0"); glow.emission_energy_multiplier = 1.2
        head.material_override = glow
        box(root,Vector3(x,0.74,length/2+0.015),Vector3(0.62,0.12,0.04),Color("fb594c"))
        box(root,Vector3(x*1.84,1.05,-0.3),Vector3(0.22,0.13,0.27),color)
    box(root,Vector3(0,0.53,-length/2-0.035),Vector3(1.0,0.16,0.06),Color("09121c"))
    box(root,Vector3(0,0.69,length/2+0.04),Vector3(0.43,0.16,0.02),Color("e2d4b5"))
    if variant != 1:
        box(root,Vector3(0,1.12,1.7),Vector3(2.06,0.08,0.36),Color("182737"))
        for x in [-0.6,0.6]: box(root,Vector3(x,0.99,1.7),Vector3(0.065,0.26,0.13),Color("182737"))
    var wheels: Array = []
    for z in [-1.34,1.34]:
        for x in [-0.99,0.99]:
            var pivot := Node3D.new(); root.add_child(pivot); pivot.position = Vector3(x,0.43,z)
            var tire := cylinder(pivot,Vector3.ZERO,0.43,0.25,Color("141a21")); tire.rotation.z = PI/2
            var rim := cylinder(pivot,Vector3(signf(x)*0.14,0,0),0.28,0.016,Color("c3ccd0")); rim.rotation.z = PI/2
            var hub := cylinder(pivot,Vector3(signf(x)*0.15,0,0),0.11,0.02,Color("233340")); hub.rotation.z = PI/2
            wheels.append(pivot)
    return {"root":root,"wheels":wheels}

static func palm(parent: Node3D, pos: Vector3, height: float) -> void:
    cylinder(parent,pos+Vector3(0,height/2,0),0.24,height,Color("907658"),0.16)
    for i in range(6):
        var angle: float = i*TAU/6
        var leaf := box(parent,pos+Vector3(sin(angle)*1.7,height,cos(angle)*1.7),Vector3(0.85,0.13,4.6),Color("397e64"))
        leaf.rotation = Vector3(0.17,angle,0)

static func building(parent: Node3D, pos: Vector3, size: Vector3, color: Color, style: int) -> void:
    box(parent,pos+Vector3(0,size.y/2,0),size,color,true)
    box(parent,pos+Vector3(0,size.y+0.15,0),Vector3(size.x+0.5,0.3,size.z+0.5),color.darkened(0.24))
    if style == 0:
        box(parent,pos+Vector3(0,size.y+1,0),Vector3(size.x*0.7,1.8,size.z*0.7),Color("b17863"))
    for floor_index in range(maxi(1,int(size.y/4))):
        var y: float = 2.4+floor_index*4
        if y > size.y-0.5: break
        for side in [-1,1]:
            box(parent,pos+Vector3(0,y,side*(size.z/2+0.02)),Vector3(size.x*0.78,1.45,0.045),Color("466576"),false,0.35)
            box(parent,pos+Vector3(side*(size.x/2+0.02),y,0),Vector3(0.045,1.45,size.z*0.78),Color("466576"),false,0.35)
    box(parent,pos+Vector3(0,1.2,-size.z/2-0.03),Vector3(1.8,2.4,0.07),Color("273944"))
