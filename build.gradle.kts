// Top-level build file. Plugin versions are declared here (once) and applied
// without a version in the module build file. Keeping this pinned prevents an
// unrelated future plugin update from unexpectedly breaking the build.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
