# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   var *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Proguard configuration for Jackson 2.x
# Jackson
-keep public class com.lagradost.shiro.utils.AniListApi.**
-keep public enum com.lagradost.shiro.AniListApi$AniListStatusType** {
    **[] $VALUES;
    public *;
}
-keep class com.lagradost.shiro.ui.result.ResultFragment.** { *; }

-keepattributes *Annotation*

-keep class kotlin.** { *; }
-keep class org.jetbrains.** { *; }

-keep class * {
    enum **;
}
-keep class com.lagradost.shiro.ui.result.ResultFragment$loadGetDataAboutId$CardAniListInfo

-keep        class android.support.v13.** { *; }
-keep        class android.support.v7.** { *; }
-keep        class android.support.v4.** { *; }
-keep class androidx.mediarouter.app.MediaRouteActionProvider { public <methods>; }
-keep public class androidx.mediarouter.app.MediaRouteActionProvider { public <methods>; }
-keepclassmembernames class * { *;}
# Jackson
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonCreator class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonValue class * { *; }
-keep class com.fasterxml.** { *; }
-keep class org.codehaus.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers public final enum com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility {
    public static final com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility *;
}

# Proguard configuration for Jackson 2.x
-keep class com.fasterxml.jackson.databind.ObjectMapper {
    public <methods>;
    protected <methods>;
}
-keep class com.fasterxml.jackson.databind.ObjectWriter {
    public ** writeValueAsString(**);
}
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# Proguard configuration for Jackson 2.x
-keepclassmembers class * {
     @com.fasterxml.jackson.annotation.* *;
}

-keep class com.lagradost.shiro.utils.CastOptionsProvider{ *; }
-keep class androidx.mediarouter.app.MediaRouteActionProvider { *; }