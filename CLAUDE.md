# Grid Menu

Client-only Fabric extension for Mod Menu: a compact grid replaces the mods list. Mod id `gridmenu`, GitHub RasmusKD/grid-menu, Modrinth lFTRTlfB (unlisted by staff), branch master (26.3 work on `mc-26.3`). Memory note: `gridmenu-mod.md`.

Read the `minecraft-mods` skill first (`C:\Users\Rasmus\.claude\skills\minecraft-mods\SKILL.md`). It holds the cross-mod rules: separate jars per Minecraft line, release flow, CurseForge/Modrinth, testing, config and icon style.

## Build

- Java 25: `JAVA_HOME=C:\Users\Rasmus\.jdks\jdk-25.0.3+9`, then `./gradlew build`. The jar is written to `build/libs/`.
- No unit tests; the check is a clean build (`verifyMixinClasses` is part of `check`), `linkcheck.py` per jar and Minecraft version, and a run in a Modrinth App instance (see the skill).
- Two jars: `./gradlew build` (26.1.x and 26.2, Mod Menu 18+, `gridmenu-X.Y.Z+26.1.jar`) and `./gradlew build -PmcTarget=26.3` (Mod Menu 21+, `+26.3`); see `VERSIONS.md`. Run `clean` between the two. Mod Menu 18 and 20 diverge (library visibility enum, button class, configure sprite), resolved by name through reflection in the 26.1 line; compile against 26.2 with `-Pminecraft_version=26.2 -Pfabric_version=0.158.0+26.2 -Pmodmenu_version=20.0.3 -Pclothconfig_version=26.2.155` before claiming it.

## Release

No workflow. Publish by hand through the Modrinth API (recipe in the memory note). Do not publish unless Rasmus asks.

## Keep skills and docs current (same rule as Nordvec)

When you learn something new, update the doc the next agent reads in the same change: this file for build and release facts, `README.md` for user-facing behavior, and the `minecraft-mods` skill for cross-mod lessons. Correct or delete outdated text instead of adding a contradicting note. Then run `python C:\Users\Rasmus\.claude\skills\minecraft-mods\scripts\check_citations.py`.

No em or en dashes in any text. Commit messages describe the code change.
