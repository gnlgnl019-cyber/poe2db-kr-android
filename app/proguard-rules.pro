# 웹뷰 JS 브리지를 쓰지 않으므로 별도 keep 규칙은 최소로 둔다.
-keepattributes JavascriptInterface
-keep class com.sunuhwi.poe2dbkr.** { *; }
-dontwarn org.jetbrains.annotations.**
