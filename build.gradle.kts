plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.0.0")
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    implementation("io.arrow-kt:arrow-core:1.2.4")

    // kchesslib (PGN parsing)
    implementation("io.github.cvb941:kchesslib:0.7.0")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("coach.AppKt")
}

tasks.test {
    useJUnitPlatform()
}
