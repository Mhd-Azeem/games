"""Set Linux CI editor paths without committing machine-specific settings or keys."""
import os
from pathlib import Path

config = Path.home() / '.config/godot/editor_settings-4.4.tres'
config.parent.mkdir(parents=True, exist_ok=True)
def quote(value):
    return '"' + value.replace('\\', '/').replace('"', '\\"') + '"'
config.write_text(
    '[gd_resource type="EditorSettings" format=3]\n\n[resource]\n'
    f'export/android/android_sdk_path = {quote(os.environ["ANDROID_HOME"])}\n'
    f'export/android/java_sdk_path = {quote(os.environ["JAVA_HOME"])}\n'
    f'export/android/debug_keystore = {quote(os.environ["GODOT_ANDROID_KEYSTORE_DEBUG_PATH"])}\n',
    encoding='utf-8',
)
