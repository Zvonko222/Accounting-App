plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.accounting"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.accounting"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room 把每个版本的表结构导出为 JSON，存进 app/schemas。
        // 这是以后编写 Database Migration 的依据（ARCHITECTURE.md 2.6），必须开启。
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // ViewBinding：编译器把每个 XML 里的 android:id 变成 Java 字段，
        // 替代手写 findViewById，没有其他魔法。
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // MVVM：ViewModel 持有界面状态，LiveData 让 UI 自动刷新
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // 本地数据库（唯一事实源）
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // 后台同步任务：可持久化，手机重启后任务不丢
    implementation(libs.work.runtime)

    // 同步子系统专用（Phase 9+），业务代码不接触它们
    implementation(libs.okhttp)
    implementation(libs.gson)

    // 拍照导入（OCR）：bundled 中文文字识别——模型打进 APK，运行时完全离线，
    // 不依赖 Google 服务框架（国产机型可用），符合 local-first 原则
    implementation(libs.mlkit.text.chinese)

    // 读取照片 EXIF 旋转信息（拍照导入用，官方小库）
    implementation(libs.exifinterface)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
