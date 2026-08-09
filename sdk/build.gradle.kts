import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka.javadoc)
    `signing`
    `maven-publish`
}

android {
    namespace = "top.ntutn.tokenfamily.sdk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        aarMetadata {
            minCompileSdk = 30
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        aidl = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    description = "Javadoc JAR containing Dokka documentation"
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.gson)
    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "top.ntutn"
            artifactId = "tokenfamily-sdk"
            version = "0.2.0"

            afterEvaluate {
                from(components["release"])
            }

            artifact(dokkaJavadocJar)

            pom {
                name.set("TokenFamily SDK")
                description.set(
                    "Android AI middleware SDK for forwarding OpenAI-compatible Chat Completions " +
                        "requests and streaming responses over Binder IPC.",
                )
                url.set("https://github.com/zerofancy/TokenFamily")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("zerofancy")
                        name.set("zerofancy")
                        email.set("ntutn.top@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:github.com/zerofancy/TokenFamily.git")
                    developerConnection.set("scm:git:ssh://github.com/zerofancy/TokenFamily.git")
                    url.set("https://github.com/zerofancy/TokenFamily")
                }
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
        .map { it.replace("\\n", "\n") }
        .orNull
    val signingPassword = providers.gradleProperty("signingPassword")
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
        .getOrElse("")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["release"])
}
