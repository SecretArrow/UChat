plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("com.diffplug.spotless") version "6.25.0"
}

spotless {
    kotlin {
        target(
            "app/src/main/**/*.kt",
            "app/src/test/**/*.kt",
            "app/src/androidTest/**/*.kt",
        )
        ktfmt("0.47").kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt("0.47").kotlinlangStyle()
    }
}
