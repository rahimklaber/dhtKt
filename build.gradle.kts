import com.google.protobuf.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    java
    idea
    kotlin("jvm") version "1.3.72"
    id("com.google.protobuf") version ("0.8.9")
    id("org.openjfx.javafxplugin") version "0.0.8"
    application
}


group = "me.rahimklaber"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = URI("http://clojars.org/repo/")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    //grpc
    implementation("io.grpc:grpc-netty-shaded:1.26.0")
    implementation("io.grpc:grpc-protobuf:1.26.0")
    implementation("io.grpc:grpc-stub:1.26.0")

    implementation("io.grpc:grpc-kotlin-stub:0.2.0")

    // logging
    implementation("io.github.microutils:kotlin-logging:1.7.9")
    implementation("org.slf4j:slf4j-simple:1.7.29")

    implementation("javax.annotation:javax.annotation-api:1.3.2")
    //tornadofx
    implementation("no.tornado:tornadofx:1.7.17")

    // coroutine
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.3.9")

    //testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("ch.tutteli.atrium:atrium-fluent-en_GB:0.12.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.11.0"
    }
    plugins {
        id("grpc-kt") {
            /**
             * Todo
             * Should be version 0.2.0:jdk7@jar and the protobuff pulgin should be 0.8.13
             * but when using this protobuff plugin, gradle can't resolve the coroutine
             * dependency for some reason.
             */
            artifact = "io.grpc:protoc-gen-grpc-kotlin:0.1.5"
        }
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.26.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without options.
                id("grpc")
                id("grpc-kt")
            }
        }
    }
}
configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "me.rahimklaber.dhtrpc.ChordNode"
}
javafx {
    modules("javafx.controls", "javafx.fxml")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
