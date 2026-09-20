"""Run engine-level tests and fail on runtime/parse errors (Godot may exit 0 for these)."""
import argparse
import pathlib
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--godot', default='godot')
parser.add_argument('--log-file')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
base = [args.godot, '--headless', '--path', str(root)]
if args.log_file:
    base += ['--log-file', args.log_file]
for options in [
    ['--editor', '--import', '--quit'],
    ['--fixed-fps', '60', '--script', 'tests/test_game.gd', '--', '--test-mode'],
]:
    result = subprocess.run(base + options, capture_output=True, text=True, timeout=240)
    output = result.stdout + result.stderr
    print(output)
    fatal = any(word in output for word in ['SCRIPT ERROR:', 'Parse Error:', 'TEST FAIL:'])
    if result.returncode or fatal:
        sys.exit(result.returncode or 1)
print('All Neon Coast checks passed.')
