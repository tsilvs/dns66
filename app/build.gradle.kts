plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.jak_linux.dns66"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.jak_linux.dns66"
        minSdk = 21
        targetSdk = 34
        versionCode = 29
        versionName = "0.6.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.1.0")

    // Proxy stuff
    implementation("org.pcap4j:pcap4j-core:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-static:1.8.2")
    implementation("dnsjava:dnsjava:3.0.0")

    implementation("com.google.code.gson:gson:2.8.6")

    testImplementation("junit:junit:4.13.1")
    testImplementation("org.mockito:mockito-core:1.10.19")
    testImplementation ("org.powermock:powermock-api-mockito:1.6.6") {
        exclude(module = "hamcrest-core")
        exclude(module = "objenesis")
    }
    testImplementation ("org.powermock:powermock-module-junit4:1.6.6") {
        exclude(module = "hamcrest-core")
        exclude(module = "objenesis")
    }
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0") {
        exclude(group = "com.android.support", module = "support-annotations")
    }
}
