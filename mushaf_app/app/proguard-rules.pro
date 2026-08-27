# ProGuard/R8 rules for the release build (isMinifyEnabled + isShrinkResources).
#
# Most of what this app uses ships its own consumer rules, so this file stays deliberately small:
#   - Room bundles rules that keep the generated *_Impl classes reachable from Room.databaseBuilder.
#   - Compose, Coil 3 and DataStore likewise ship their own.
#   - org.json is part of the Android platform and is never shrunk.
# Anything added here has to justify itself; a wall of defensive -keep lines just disables R8.

# ---------------------------------------------------------------------------
# Readable crash reports
# ---------------------------------------------------------------------------
# Without these a Play Console stack trace has no line numbers, so an obfuscated
# release crash cannot be traced back to a source line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin puts the parameter/nullability metadata reflection and Room rely on here.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Entities are constructed by generated code that reads their fields by name, and the schema of the
# prebuilt asset database (createFromAsset) is matched against these classes at open time. Room's
# own rules cover the DAOs and the *_Impl classes; the entities are kept here explicitly because a
# renamed field turns into an opaque "migration didn't properly handle" crash at startup.
-keep class com.mushaf.reader.data.stats.SessionEntity { *; }
-keep class com.mushaf.reader.data.stats.KhatmaEntity { *; }
-keep class com.mushaf.reader.data.content.** { *; }

# ---------------------------------------------------------------------------
# Noise
# ---------------------------------------------------------------------------
# Coroutines references these optional debug/internal classes that are not on the classpath.
-dontwarn kotlinx.coroutines.**
