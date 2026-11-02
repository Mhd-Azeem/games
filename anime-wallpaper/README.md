# AZ Anime Motion Wallpaper Studio

Isolated native Android project in `Mhd-Azeem/games`, branch `feature/az-anime-motion-wallpaper`. Other games are untouched. Android 11+; target Android 15. No network permission, advertising, analytics, accounts or paid services.

## Build and tests

Java 17, Gradle wrapper 8.9, AGP 8.7.3, Kotlin 2.0.21, compile/target SDK 35.

```
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

Instrumentation tests require an emulator or attached device. GitHub Actions runs these on API 35. Real accelerometer feel, lock-screen behavior and Nubia battery/OEM behavior need physical-device testing.

## Architecture

- `models`: versioned project data, normalized source coordinates and physics parameters.
- `selection`: bounded brush, eraser, closed polygon/lasso, ellipse, rectangle, flood fill, feathering and transforms.
- `physics`: substepped bounded springs and calibrated accelerometer filtering/orientation mapping.
- `rendering`: shared OpenGL ES 2 textured mesh and invertible image/view transforms. Cached influence weights, no bitmap allocation in the render loop. Regions compose in layer order; assigned points multiply region masks; fixed anchors attenuate nearby motion.
- `storage`: private original working image, atomically replaced JSON, bounded archive IO, traversal rejection, original procedural starter artwork, independent applied snapshot.
- `editor`: explicit tools and separate panning, reusable selection overlay, point and polygon vertex editing.
- `MainActivity`: home/library/editor/settings, bounded undo history, manual/sensor preview, system picker and backup flows.
- `MotionWallpaperService`: EGL ownership on a render thread, Android surface/visibility lifecycle and sensor cleanup.

Masks are 128×128 normalized weights, interpolated and feathered into a 32–96-subdivision mesh. Points use smooth radial falloff. Limits: 512 points, 32 regions, 80 MiB input images, 90 MiB expanded archive. Native ImageDecoder handles EXIF orientation and supported HEIF/WEBP decoding; textures are bounded to 2048 pixels. Cropping is nondestructive.

A flat photograph has no hidden background. The renderer warps continuous source pixels without pretending to reconstruct occluded content. Extreme motion can stretch or fold the mesh. Keep displacements small around detailed boundaries. Optional advanced transparent overlay/repaired-background composition and gyro fusion remain unimplemented.

The six built-in illustrations are original vector drawing code in `Artwork.kt`, generated entirely offline. They include a stylized anime silhouette, forest, abstract graphite ribbons, fantasy mountains, constellations and minimal charcoal curves. They are not downloaded third-party images.

## Signing and release

No signing key is generated or committed. Configure repository Actions secrets with a stable user-owned key:

- `AZ_SIGNING_STORE_BASE64`
- `AZ_SIGNING_STORE_PASSWORD`
- `AZ_SIGNING_KEY_ALIAS`
- `AZ_SIGNING_KEY_PASSWORD`

Local signed builds use `AZ_SIGNING_STORE` (absolute keystore path) and the password/alias variables above. Debug builds use `com.az.animewallpaper.dev` so they install alongside the old app without replacing it. Export/import projects when migrating between test and production builds. The workflow always produces a debug test artifact; it builds and verifies a production APK only when all four secrets exist. Dispatch `publish=true` with a unique tag to publish after unit/lint and emulator checks pass. Never treat a debug APK as the final production release. Keep a secure backup of the keystore; updates require the same signing identity.

## Editing update 0.3

Use **Multi-select** and tap point handles to toggle them. Drag any selected handle to move the whole selection; group spacing is preserved at image edges. The floating point bar offers Remove points, All and Clear. Removal supports Undo. Physics on multiple selected points opens batch controls; only the changed field is shared. **Focus canvas** hides editing toolbars, with Show tools to restore them. Selection survives preview navigation and activity recreation.

The **×** on a Home recent card hides that item from Recents. It keeps the saved project and currently applied wallpaper. Open it in My Wallpapers to return it to Recents, or use Restore removed recent items.
