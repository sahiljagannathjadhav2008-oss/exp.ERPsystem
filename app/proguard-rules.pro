# Room entities/DAOs are referenced via reflection at compile time by the
# annotation processor, but keep them explicitly for safety with reflection
# based tooling (e.g. schema validation) in release builds.
-keep class com.builtdifferent.erp.data.local.entity.** { *; }
-keep class com.builtdifferent.erp.data.local.dao.** { *; }
-keep @androidx.room.Entity class *
-dontwarn org.robolectric.**
