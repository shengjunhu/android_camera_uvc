-keep class com.hsj.camera.* {*;}
-dontwarn com.hsj.camera.**

-keepclasseswithmembernames class * {
    native <methods>;
}