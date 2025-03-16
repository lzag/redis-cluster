plugins {
    kotlin("jvm") version "2.1.10"
    application
}

group = "com.lzag"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
val vertxVer = "4.5.13"

dependencies {
    implementation("io.vertx:vertx-core:$vertxVer")
    implementation("io.vertx:vertx-redis-client:$vertxVer")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVer")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVer")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
}

application {
    mainClass.set("RedisBenchmarkVerticleKt") // Kotlin generates this class for top-level main
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
