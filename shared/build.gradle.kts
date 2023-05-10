import dev.icerock.gradle.MRVisibility.Internal

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.cocoapods)
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.moko.resources)
}

multiplatformResources {
  multiplatformResourcesPackage = "dev.sasikanth.rss.reader"
  multiplatformResourcesClassName = "CommonRes"
  multiplatformResourcesVisibility = Internal
  disableStaticFrameworkWarning = true
}

kotlin {
  android()

  ios()
  iosSimulatorArm64()

  cocoapods {
    version = "1.0.0"
    summary = "RSS Reader"
    homepage = "https://github.com/msasikanth/rss_reader"
    ios.deploymentTarget = "14.1"
    podfile = project.file("../iosApp/Podfile")
    framework {
      baseName = "shared"
      isStatic = true

      export(libs.decompose)
      export(libs.essenty.lifecycle)
    }
    extraSpecAttributes["resources"] = "['src/commonMain/resources/**', 'src/iosMain/resources/**']"
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(libs.bundles.compose)
        implementation(libs.ktor.core)
        implementation(libs.napier)
        implementation(libs.sqldelight.extensions.coroutines)
        implementation(libs.kotlinx.coroutines)
        implementation(libs.kotlinx.datetime)
        implementation(libs.insetsx)
        api(libs.decompose)
        implementation(libs.decompose.extensions.compose)
        api(libs.essenty.lifecycle)
        api(libs.bundles.moko.resources)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    val androidMain by getting {
      dependencies {
        api(libs.androidx.activity.compose)
        api(libs.androidx.appcompat)
        api(libs.androidx.core)
        implementation(libs.ktor.client.okhttp)
        implementation(libs.sqldelight.driver.android)
        implementation(libs.coil.compose)
      }
    }

    val iosSimulatorArm64Main by getting
    val iosMain by getting {
      dependsOn(commonMain)
      iosSimulatorArm64Main.dependsOn(this)
      dependencies {
        implementation(libs.ktor.client.darwin)
        implementation(libs.sqldelight.driver.native)
      }
    }
  }
}

sqldelight {
  databases {
    create("ReaderDatabase") {
      packageName.set("dev.sasikanth.rss.reader.database")
    }
  }
}

android {
  compileSdk = libs.versions.android.sdk.compile.get().toInt()
  namespace = "dev.sasikanth.rss.reader.common"

  sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
  sourceSets["main"].res.srcDirs("src/androidMain/res")
  sourceSets["main"].resources.srcDirs("src/commonMain/resources")

  defaultConfig {
    minSdk = libs.versions.android.sdk.min.get().toInt()
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin {
    jvmToolchain(11)
  }
}
