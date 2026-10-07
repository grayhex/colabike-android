# kotlinx.serialization and OkHttp ship their own R8 rules. Navigation 3 keys are
# @Serializable classes saved in the back stack; keep their serializers.
-keepclassmembers @kotlinx.serialization.Serializable class ru.colabike.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# The RuStore push SDK names Tracer (its crash reporter) only to look it up by name and falls back
# to a stub; the app ships without it (docs/adr/0017), so what refers to it is not an error.
-dontwarn ru.ok.tracer.**

# Yandex MapKit names Google Play Services (the fused location, Play Integrity) in classes that only
# run when the app asks the SDK to locate the phone or to attest, and neither is asked; the app
# carries no Play Services (AGENTS.md), so what refers to them is not an error. docs/adr/0025.
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.play.core.integrity.**
