# Living Inventory (Fabric, Minecraft 1.21.1, client-side)

Replaces the survival inventory panel itself (the 176x166 window with slots and the player) with an animated GIF / image, cropped to fit. Dark plates under the slots keep items readable.

- Files live in `.minecraft/config/livinginv/` (drag & drop into the picker screen also works)
- Open inventory -> small **GIF** button in the top-left corner -> pick a file
- `config/livinginv/config.json`:
  - `maxDurationSeconds` (default 600) - hard cap on animation length
  - `memoryBudgetMb` (default 384) - RAM for frames; long GIFs get lower resolution automatically to fit
  - `maxResolution` (default 512), `slotOpacity` (0-100)

## Build
Needs JDK 21 and Gradle 8.8+:

    gradle wrapper --gradle-version 8.8
    ./gradlew build

Jar: `build/libs/living-inventory-1.0.0.jar`

No third-party code or assets are included. Not affiliated with Mojang or Microsoft.
