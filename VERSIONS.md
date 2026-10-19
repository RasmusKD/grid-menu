# Version splits

One source tree, two jars (26.1/26.2 and 26.3). Add new finds here.

## 26.3 is a separate jar

`./gradlew build` produces `gridmenu-X.Y.Z+26.1.jar` (26.1.x and 26.2, Mod Menu >=18). `./gradlew build -PmcTarget=26.3`
produces `gridmenu-X.Y.Z+26.3.jar` (pins in `gradle-26.3.properties`, Minecraft >=26.3, Mod Menu >=21). Files under
`src/mc26_3/java` and `src/mc26_3/resources` replace the same-named file of `src/main`; `src/mc26_3/remove.txt` lists main
files the 26.3 jar drops. `verifyMixinClasses` (part of `check`) fails when a mixins.json entry has no class in that variant.
Check each jar against the Minecraft versions it claims without starting the game:
`python C:\Users\Rasmus\.claude\skills\minecraft-mods\scripts\linkcheck.py <jar> <mapped minecraft jar>`.
Expected report: the 26.1 jar lists the `setScreen` handler of `ScreenSwapMixin` as missing on 26.2 (a `require = 0` twin of
`setScreenAndShow`); the 26.3 jar is clean.

| What | 26.1/26.2 | 26.3 | Fix |
|---|---|---|---|
| Key codes | `InputConstants.KEY_F8` = 297 (GLFW keycodes), `org.lwjgl.glfw.GLFW` on the classpath | `KEY_F8` = 65 (SDL scancodes), `KeyEvent.key()` returns them, `org.lwjgl.glfw` is gone | compare `event.key()` with `InputConstants.KEY_*`; the constant is inlined per variant at compile time. Never write a GLFW constant or a number |
| Left mouse button | 0 | 1 | compare `event.button()` with `InputConstants.MOUSE_BUTTON_LEFT` (inlined per variant) |
| Screen setter on `Minecraft` | `setScreen` (26.1), `setScreenAndShow` (26.2) | `setScreenAndShow` | `src/mc26_3` overlay of `ScreenSwapMixin` with only `setScreenAndShow` |
| `RenderPipelines.GUI_TEXTURED`, `blit`, `blitSprite` | `com.mojang.blaze3d.pipeline.RenderPipeline` | `com.mojang.renderpearl.api.pipeline.RenderPipeline` | none: the direct names compile per variant, which is why the jars are separate |
| Mod Menu | 18.0.0 (20.0.3 on 26.2) | 21.0.0 | mixins on Mod Menu classes are `@Pseudo`; `modmenu` dependency range is `${modmenu_range}` in `fabric.mod.json` |

Full 26.3 table: `C:\Users\Rasmus\Desktop\Ny mappe (3)\mc-26.3-splits.md`.
