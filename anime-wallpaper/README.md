# AZ Anime Motion Wallpaper
Native Android live wallpaper. Branch-isolated from the existing game.
## Artwork
The app builds without assets and shows a placeholder. For the intended anime illustration, add three **matching full-canvas** PNGs to `app/src/main/assets/`:
- `background.png`: fixed background and retouched stationary body
- `hair.png`: transparent movable hair only
- `chest.png`: transparent upper-clothing layer only

These assets are **not included** in this GitHub commit. A flattened image is not a suitable replacement for separately retouched layers.
## Build
GitHub Actions -> Build AZ Anime Wallpaper -> Run workflow, or push this branch. Download the `AZAnimeWallpaper-debug-apk` artifact.
## Limitations
The current renderer translates layers, rather than deforming a Live2D mesh. It is a functional prototype, not final realistic physics.
