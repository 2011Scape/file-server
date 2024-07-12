buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.8.0"))
    }
}

plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "world.gregs.rs2.file"
group = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.displee:rs-cache-library:6.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    implementation("io.ktor:ktor-server-core:2.3.0")
    implementation("io.ktor:ktor-network:1.5.0")

    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger-jvm:1.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.6.2")
    testImplementation("io.mockk:mockk:1.10.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "world.gregs.rs2.file.Main"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

application {
    mainClass.set("world.gregs.rs2.file.Main")
}
