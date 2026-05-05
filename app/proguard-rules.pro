# ─── GeckoView ───────────────────────────────────────────────────────────────
# GeckoView usa reflexão internamente — não pode ser obfuscado
-keep class org.mozilla.geckoview.** { *; }
-keep interface org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# ─── JavaScript Bridge (JavascriptInterface) ─────────────────────────────────
# Todos os métodos anotados com @JavascriptInterface precisam ser preservados.
# O WebView chama via reflexão pelo nome exato do método.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.example.waspbrowser.BeeActivity$BeeBridgeInner { *; }
-keepclassmembers class com.example.waspbrowser.BeeBridge { *; }
-keepclassmembers class com.example.waspbrowser.SearchBridge { *; }

# ─── AdMob / Google Play Services ────────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.**

# ─── AndroidX e Material ─────────────────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**

# ─── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# ─── Activities, Services, Receivers, Providers ──────────────────────────────
# O Android instancia por reflexão via Manifest — não pode obfuscar nomes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ─── Parcelable ──────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ─── Enums ───────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── FileProvider ─────────────────────────────────────────────────────────────
-keep class androidx.core.content.FileProvider { *; }

# ─── Wasp classes principais ─────────────────────────────────────────────────
-keep class com.example.waspbrowser.** { *; }