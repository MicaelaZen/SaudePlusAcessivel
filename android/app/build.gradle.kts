plugins {
    id("com.android.application")
}

android {
    namespace = "org.cuidarmais.acessivel"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.cuidarmais.acessivel"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/webAssets"))
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}

val generatedWebAssets = layout.buildDirectory.dir("generated/webAssets")
val copyWebApp = tasks.register<Copy>("copyWebApp") {
    from(rootProject.projectDir.parentFile) {
        include("index.html")
    }
    into(generatedWebAssets)
}

tasks.named("preBuild").configure {
    dependsOn(copyWebApp)
}