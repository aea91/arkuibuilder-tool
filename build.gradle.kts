import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.arkuibuilder"
version = "0.3.2"

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
    }
    implementation("com.google.code.gson:gson:2.11.0")
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
              <li>Pick a platform (Mobile, Smart Wearable, PC&nbsp;2in1) to see a gallery of animated previews</li>
              <li>Filter by category or search by name, description and tags</li>
              <li>Click a preview to see its code, then <b>Insert into editor</b> or <b>Copy code</b></li>
              <li>Previews load lazily as you scroll</li>
            </ul>
            <p>Open it from the <b>ArkUIBuilder</b> tool window or <b>Tools | Open ArkUIBuilder</b>.</p>
            <p><b>Network use:</b> the plugin downloads the public widget catalog and preview images from
            the ArkUIBuilder backend. It does not collect or send any personal or project data.</p>
        """.trimIndent()

        changeNotes = """
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
