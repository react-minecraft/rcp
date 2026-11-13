import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import util.VersionUtility

plugins {
    id("java-library")
    id("maven-publish")
    id("react.base-conventions")
    id("react.shadow-conventions")
}

version = VersionUtility.version(project, version.toString())

repositories {

    mavenCentral()

    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "codemc-public"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io/")
    }

}

dependencies {

    compileOnly("com.github.react-minecraft.plugin:react-api:${property("reactApiVersion")}")

    compileOnly(libs.paper.api)
    compileOnly(libs.cloud.core)
    compileOnly(libs.packetevents)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.textserializer.legacy)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly(libs.flatbuffers)
    testImplementation(libs.flatbuffers)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testImplementation("com.github.react-minecraft.plugin:react-api:${property("reactApiVersion")}")
    testRuntimeOnly(libs.junit.platform.launcher)

}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val inferenceApiJar = tasks.register<Jar>("inferenceApiJar") {
    archiveClassifier.set("api")
    from(sourceSets.main.get().output)
    include("com/g4vrk/react/inference/api/**")
}

publishing {
    publications {
        create<MavenPublication>("inferenceApi") {
            groupId = System.getenv("GROUP") ?: project.group.toString()
            artifactId = "rcp"
            version = System.getenv("VERSION") ?: project.version.toString()
            artifact(inferenceApiJar) {
                classifier = ""
            }
        }
    }
}

tasks {

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    processResources {
        val properties = mapOf("version" to project.version.toString())
        inputs.properties(properties)
        filesMatching("rcp-metadata.yml") {
            expand(properties)
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveFileName.set("${rootProject.name}-${project.version}.jar")
    }

    named("build") {
        dependsOn(named("shadowJar"))
    }

}
