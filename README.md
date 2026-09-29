# ArkUIBuilder Tool

DevEco Studio / IntelliJ plugin for browsing ArkUI snippets and inserting ArkTS into the editor.

Companion to [ArkUIBuilder](https://huaweidevelopers.com) (`arkuibuild`).

## Requirements

- JDK 17+
- Gradle (wrapper included)
- DevEco Studio (or IntelliJ IDEA) for installing the ZIP

## Build

```bash
./gradlew buildPlugin
```

ZIP output:

```text
build/distributions/arkuibuildertool-0.3.2.zip
```

Flow: **Platform → GIF gallery → code** (optional Category filter, Firestore `mainCategory` / `category`). Previews use `proxyImage` and load lazily as you scroll.
## Install in DevEco Studio

1. `File` → `Settings` → `Plugins`
2. Gear → **Install Plugin from Disk…**
3. Select the ZIP (do not unzip)
4. Restart IDE

## Usage

- Right tool window: **ArkUIBuilder**
- Or `Tools` → **Open ArkUIBuilder**
- Catalog loads from Firebase (`getWidgetCatalog`); PNG/GIF via `proxyImage`
- Pick a platform → click a GIF → code appears below → **Insert into editor** / **Copy code** / **Refresh**

## Publish to JetBrains Marketplace

DevEco Studio's **Plugins → Marketplace** tab is served by JetBrains Marketplace, so a plugin
published there can be installed from inside DevEco Studio.

1. Check compatibility: `./gradlew verifyPlugin` (report in `build/reports/pluginVerifier`)
2. First release: upload `build/distributions/arkuibuildertool-<version>.zip` by hand at
   https://plugins.jetbrains.com (profile → **Upload plugin**) and wait for moderation
3. Later releases: bump `version`, add an entry to `changeNotes` in `build.gradle.kts`, then
   `PUBLISH_TOKEN=<token> ./gradlew publishPlugin` (token from https://plugins.jetbrains.com/author/me/tokens)

## Next steps

- Manager tab + theme templates
- Match `sinceBuild` / `untilBuild` to your DevEco version if install fails
