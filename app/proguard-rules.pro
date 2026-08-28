# Gson 用字段名反序列化，不能被 R8 改名。
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

-keep class com.renovation.ledger.data.remote.** { *; }
-keep class com.renovation.ledger.data.trash.TrashEntry { *; }
-keep class com.renovation.ledger.domain.taxonomy.TaxonomyIconRef { *; }

-keep class com.renovation.ledger.wxapi.** { *; }
-keep class com.tencent.mm.opensdk.** { *; }

-keep class com.github.mikephil.charting.** { *; }

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
