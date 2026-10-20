# Living Inventory (Fabric, client-side)

Replaces the survival inventory panel itself (the 176x166 window with slots and the player) with an
animated GIF / image, cropped to fit, under a semi-transparent overlay that keeps the vanilla layout.

## Download
Every push to `main` publishes all jars to the **[latest release](../../releases/tag/latest)**.
Pick the jar that matches your Minecraft version:

| Jar | Minecraft |
|---|---|
| `mc26.3` | 26.3 |
| `mc26.1-26.2` | 26.1 - 26.2 |
| `mc1.21.6-1.21.11` | 1.21.6 - 1.21.11 |
| `mc1.21.5` | 1.21.5 |
| `mc1.21.2-1.21.4` | 1.21.2 - 1.21.4 |
| `mc1.20.5-1.21.1` | 1.20.5 - 1.21.1 |
| `mc1.20-1.20.4` | 1.20 - 1.20.4 |
| `mc1.19.4` / `mc1.19.3` / `mc1.19-1.19.2` | 1.19.4 / 1.19.3 / 1.19 - 1.19.2 |
| `mc1.17-1.18.2` | 1.17 - 1.18.2 |
| `mc1.16.5` | 1.16.5 |

Requires Fabric Loader and Fabric API.

## Use
Open the inventory -> small **GIF** button in the top-left corner -> pick a file
(or drag & drop files into that screen). Files live in `config/livinginv/`.

`config/livinginv/config.json`:
- `maxDurationSeconds` (default 600) - hard cap on animation length
- `memoryBudgetMb` (default 384) - RAM for frames; long GIFs get a lower resolution automatically
- `maxResolution` (default 512)

The panel look comes from `assets/livinginv/textures/gui/inventory_overlay.png` (animation underneath, this
semi-transparent overlay on top, rounded corners cut by its silhouette). A resource pack can replace it.

## Project layout
- `common/` - version-independent code (GIF decoder, config)
- `shared/`, `eras/` - code for each Minecraft API generation
- `versions/` - one tiny Gradle project per jar, **generated** by `tools/gen.py`
  (also generates `.github/workflows/build.yml`, including compile checks on the edge versions of each range)

No third-party code or assets are included. Not affiliated with Mojang or Microsoft. MIT license.
