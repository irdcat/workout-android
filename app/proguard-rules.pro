# Keep line numbers in crash stack traces, without the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google API client (Sheets, Drive): models are read and written by reflection over
# @Key fields, with their generic types.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.services.sheets.v4.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.appengine.**
-dontwarn javax.annotation.**
-dontwarn org.apache.http.**
