import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    application
    kotlin("jvm") version "2.4.10"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

application {
    mainClass.set("com.geno1024.ai.freezip.cli.MainKt")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Self-contained runnable jar: `java -jar build/libs/free-zip-kotlin-all.jar ...`
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a self-contained runnable jar."
    archiveBaseName.set("free-zip-kotlin")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
