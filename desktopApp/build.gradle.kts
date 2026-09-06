import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.unixsocket)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.regtho.musicor.MainKt"

        nativeDistributions {
            // Package only the formats supported by the build OS. Each CI job
            // builds on its native runner (Linux -> AppImage/deb, Windows -> exe).
            val osName = System.getProperty("os.name").lowercase()
            targetFormats(
                *when {
                    osName.contains("windows") -> listOf(TargetFormat.Exe)
                    osName.contains("mac") -> listOf(TargetFormat.Dmg)
                    else -> listOf(TargetFormat.Deb, TargetFormat.AppImage)
                }.toTypedArray(),
            )
            packageName = "com.regtho.musicor"
            packageVersion = "1.0.0"
        }
    }
}