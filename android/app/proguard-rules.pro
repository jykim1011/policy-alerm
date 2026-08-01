# Add project specific ProGuard rules here.

# ---- Gson (Retrofit 응답 모델) ----
# Gson은 필드명 기반 리플렉션으로 역직렬화하므로, 모델 클래스는 필드명이
# 그대로 보존돼야 한다. 제네릭(List<T>) 역직렬화에는 Signature 속성이 필요하다.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.policyalarm.data.model.** { <fields>; }
-dontwarn com.google.gson.**

# ---- Retrofit ----
-keepattributes Exceptions, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.AnnotationStub
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- Room ----
# 생성된 *_Impl 클래스가 이름으로 참조하므로 Entity/DAO 를 보존한다.
-keep class com.policyalarm.data.local.** { *; }
