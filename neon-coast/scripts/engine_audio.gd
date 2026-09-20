extends AudioStreamPlayer
## Procedural engine tone; no sampled/copyrighted audio.
var phase: float = 0
var car: CharacterBody3D
var playback: AudioStreamGeneratorPlayback

func _ready() -> void:
    var generator := AudioStreamGenerator.new(); generator.mix_rate=16000; generator.buffer_length=0.1
    stream=generator; volume_db=-23; play(); playback=get_stream_playback()

func _process(_delta: float) -> void:
    if playback==null or not is_instance_valid(car): return
    var frequency: float=42+fmod(absf(car.speed)*5,115)
    var amplitude: float=0.16+absf(car.throttle)*0.22
    var frames: int=mini(playback.get_frames_available(),3200)
    for i in frames:
        phase=fmod(phase+frequency/16000,1)
        var sample: float=(sin(phase*TAU)+sin(phase*TAU*2)*0.25+sin(phase*TAU*4)*0.12)*amplitude
        playback.push_frame(Vector2.ONE*sample)
