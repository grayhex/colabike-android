# kotlinx.serialization and OkHttp ship their own R8 rules. Navigation 3 keys are
# @Serializable classes saved in the back stack; keep their serializers.
-keepclassmembers @kotlinx.serialization.Serializable class ru.colabike.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
