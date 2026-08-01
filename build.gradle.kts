buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20-Beta2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20-Beta2" apply false
}
