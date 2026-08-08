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
tasks.register<Jar>("dokkaJavadocJar") {
    description = "Javadoc JAR containing Dokka documentation"
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
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
            version = "0.1.0"

            afterEvaluate {
                from(components["release"])
            }

            artifact(tasks.named("dokkaJavadocJar"))

            pom {
                name.set("TokenFamily SDK")
                description.set("Android 跨进程 AI 中间件 SDK，基于 Binder IPC 实现 OpenAI 兼容的 Chat Completions 转发与流式输出")
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
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["release"])
}