#Natvie方法不混淆
-keepclasseswithmembernames class * {
    native <methods>;
}

#UVC包不混淆
-keep class com.serenegiant.** {*;}

