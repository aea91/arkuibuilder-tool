import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.arkuibuilder"
version = "0.4.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against IntelliJ Community; install the ZIP into DevEco Studio.
        intellijIdeaCommunity("2023.3.7")
        instrumentationTools()
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}

// Target Java 17 bytecode; any JDK 17+ (e.g. DevEco's bundled JBR 21) can build.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

intellijPlatform {
    pluginConfiguration {
        id = "com.arkuibuilder.tool"
        name = "ArkUIBuilder Tool"
        version = project.version.toString()
        description = """
            <p>Browse ready-made ArkUI widgets from the ArkUIBuilder catalog and insert their ArkTS
            code into the current editor in DevEco Studio or any IntelliJ-based IDE.</p>
            <ul>
              <li>Pick a platform (Mobile, Wearable, PC&nbsp;2in1) to see a gallery of animated previews</li>
              <li>Filter by category or search by name, description and tags</li>
              <li>Click a preview to see its code, then <b>Add to code</b> or <b>Copy code</b></li>
              <li><b>Add to code</b> saves the component as <code>components/&lt;Name&gt;.ets</code>, adds the
                import and inserts the call at the caret, with Tab stops for its parameters</li>
              <li>Also from the editor: type a widget name for code completion, press
                <b>⌘⌥⇧A</b> / <b>Ctrl+Alt+Shift+A</b> to search, or drag a preview into the code</li>
              <li>Previews load lazily as you scroll</li>
              <li><b>My Widgets:</b> save your own widgets locally and reuse them in any project</li>
            </ul>
            <p>Open it from the <b>ArkUIBuilder</b> tool window or <b>Tools | Open ArkUIBuilder</b>.</p>
            <p><b>Network use:</b> the plugin downloads the public widget catalog and preview images from
            the ArkUIBuilder backend. It does not collect or send any personal or project data.</p>
        """.trimIndent()

        changeNotes = """
            <h3>0.4.0</h3>
            <ul>
              <li><b>Add to code</b> imports a widget properly: the component goes to
                <code>src/main/ets/components/&lt;Name&gt;.ets</code>, the current file gets the import and
                only the call is inserted, with Tab stops for required parameters (@Link, @Prop, …)</li>
              <li>Code completion in .ets files offers every widget by name</li>
              <li>Search popup: <b>⌘⌥⇧A</b> (Ctrl+Alt+Shift+A), or Generate / editor menu → ArkUIBuilder Widget…</li>
              <li>Drag a preview from the gallery into the editor</li>
              <li>Plain snippets are re-indented to the caret line; missing <code>${'$'}r()</code> resources are reported</li>
              <li>My Widgets: save your own widgets on this machine and reuse them in every project</li>
              <li>Add one with <b>New widget</b>, or select code and choose <b>Save Selection to My Widgets</b> from the editor menu</li>
              <li>Optional preview image (GIF, PNG, JPG); edit or delete saved widgets</li>
            </ul>
            <h3>0.3.3</h3>
            <ul><li>"Smart Wearable" platform is now shown as "Wearable"</li></ul>
            <h3>0.3.2</h3>
            <ul><li>Fixed animated previews not appearing in the gallery</li></ul>
            <h3>0.3.1</h3>
            <ul>
              <li>Selecting a platform shows a gallery of animated previews</li>
              <li>Clicking a preview shows its code</li>
              <li>Previews load lazily while scrolling</li>
            </ul>
            <h3>0.3.0</h3>
            <ul><li>Widget catalog loaded from Firebase with platform and category filters</li></ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }

        vendor {
            name = "ArkUIBuilder"
            url = "https://huaweidevelopers.com"
        }
    }

    publishing {
        // Marketplace token: https://plugins.jetbrains.com/author/me/tokens
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // The 243 platform DevEco Studio 6.0 is based on; the verifier cannot read a DevEco
            // installation directly. (The plugin compiles against 2023.3.7, the oldest supported.)
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.6")
        }
    }

    instrumentCode = false
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
