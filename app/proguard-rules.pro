# R8 rules for the release build (and the benchmark build the Baseline Profile plugin copies from it).
# Libraries ship their own rules (Compose, Glance, WorkManager, kotlinx.serialization,
# profileinstaller); these cover what is particular to this app.

# Stack traces from users' phones stay readable with the mapping file; keeping line numbers costs little.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Adhan: prayer-time calculation. PrayerClock maps our CalcMethod onto its CalculationMethod by name.
-keep class com.batoulapps.adhan.** { *; }

# kotlinx.serialization: the assets (adhkaar.json, collections.json, reminders.json, i18n/*.json,
# calendar/ng.json) and the user's own duas are decoded through the generated serializers. The
# library's bundled rules cover this; these are the ones its README lists, kept here as well so a
# library update can't silently break reading the content.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class org.adhkaar.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# The session's completion Summary is saved in the activity's state as java.io.Serializable, so it
# survives Android killing the process; keep what Java serialization looks up by name.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Glance stores the widget's layout as protobuf-lite messages, whose fields are read by name.
-keepclassmembers class * extends androidx.glance.appwidget.protobuf.GeneratedMessageLite {
    <fields>;
}

# OemAutostart reads Xiaomi's extra app-ops through AppOpsManager.checkOpNoThrow(int, int, String)
# by reflection. That is a framework class, which R8 never renames or removes, so it needs no rule;
# no class of the app itself is looked up by name.
