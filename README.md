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
build/distributions/arkuibuildertool-0.4.0.zip
```

Flow: **Platform → GIF gallery → code** (optional Category filter, Firestore `mainCategory` / `category`). Previews use `proxyImage` and load lazily as you scroll.
## Install in DevEco Studio

DevEco Studio's Marketplace tab only lists plugins on Huawei's built-in whitelist, so searching
for ArkUIBuilder there finds nothing. Use the custom repository instead:

1. `File` → `Settings` → `Plugins`
2. Gear → **Manage Plugin Repositories…** → **+** and add
   `https://raw.githubusercontent.com/aea91/arkuibuilder-tool/main/updatePlugins.xml`
3. **Marketplace** tab → search `ArkUIBuilder` → **Install** → restart IDE

Updates then arrive through the normal plugin update check.

Or install from disk: gear → **Install Plugin from Disk…** → select the ZIP (do not unzip) → restart IDE.

## Usage

- Right tool window: **ArkUIBuilder**
- Or `Tools` → **Open ArkUIBuilder**
- Catalog loads from Firebase (`getWidgetCatalog`); PNG/GIF via `proxyImage`
- Pick a platform → click a GIF → code appears below → **Add to code** / **Copy code** / **Refresh**

### Adding a widget to your code

Catalog widgets are full `@Component` structs, so they are imported, not pasted:

1. the struct is saved as `<module>/src/main/ets/components/<Name>.ets` (reused if it is already
   there; if that file differs you choose Keep Existing / Replace)
2. the current file gets `import { Name } from '../components/Name'`
3. only the call is inserted at the caret, e.g. `Name({ step: this.step })`; Tab moves through the
   required parameters (`@Link`, `@ObjectLink`, `@Prop`/`@Param` without a default, `@Require`)

If the widget uses `$r('app.…')` / `$rawfile()` resources the module lacks, a notification lists
them. Plain snippets (no struct) and files outside `src/main/ets` get the code itself, re-indented.

Ways to trigger it, all with the same result:

- **Add to code** in the tool window
- Code completion in an `.ets` file: type part of the component name, e.g. `ColorSt`
- **⌘⌥⇧A** (Ctrl+Alt+Shift+A), **Generate** (⌘N / Alt+Insert) or the editor context menu →
  **ArkUIBuilder Widget…**, a searchable list of all widgets
- Drag a preview from the gallery into the editor

### My Widgets

Users can keep their own widgets next to the catalog. They are stored only on that machine and are
available in every project.

- **New widget** (top of the panel), or select code in the editor → right-click →
  **Save Selection to My Widgets**
- Fields: name, platform, category, description, tags, optional preview image (GIF/PNG/JPG), code
- Pick **My Widgets** in the Platform list to browse them; select one to **Edit** or **Delete**
- Stored in `<IDE config dir>/arkuibuilder/my-widgets.json`, preview images in `previews/` next to
  it. Copy that folder to move the widgets to another machine.

## Publish to JetBrains Marketplace

DevEco Studio's **Plugins → Marketplace** tab is served by JetBrains Marketplace, but DevEco
filters it down to a whitelist, so DevEco users install through `updatePlugins.xml` (see above).

1. Check compatibility: `./gradlew verifyPlugin` (report in `build/reports/pluginVerifier`)
2. First release: upload `build/distributions/arkuibuildertool-<version>.zip` by hand at
   https://plugins.jetbrains.com (profile → **Upload plugin**) and wait for moderation
3. Later releases: bump `version`, add an entry to `changeNotes` in `build.gradle.kts`, then
   `PUBLISH_TOKEN=<token> ./gradlew publishPlugin` (token from https://plugins.jetbrains.com/author/me/tokens)
4. Update `version` and `url` in `updatePlugins.xml` to the new Marketplace download
   (Versions tab → download link) and push, so DevEco users get the update

## Next steps

- Manager tab + theme templates
- Match `sinceBuild` / `untilBuild` to your DevEco version if install fails
