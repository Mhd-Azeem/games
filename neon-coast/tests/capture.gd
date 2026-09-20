extends SceneTree
## Deterministic screenshot utility: godot --path neon-coast -s tests/capture.gd -- --test-mode
func _initialize() -> void:
    call_deferred("capture")

func capture() -> void:
    var game=load("res://scenes/main.tscn").instantiate(); root.add_child(game)
    game.on_action("resume",-1)
    game.player.respawn(Vector3(-240,0,45),Vector3.FORWARD)
    for i in 30: await process_frame
    await RenderingServer.frame_post_draw
    var image:=root.get_texture().get_image()
    image.save_png("res://preview.png")
    game.on_action("home",-1)
    for i in 5: await process_frame
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png("res://menu-preview.png")
    quit()
