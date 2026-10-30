# 应用入口：Activity 由系统按名字反射创建，混淆掉就闪退
-keep class com.jikegang.lightmeter.MainActivity { *; }
-keep class com.jikegang.lightmeter.CalcActivity { *; }

# 传感器 / 系统回调：框架通过反射查找
-keepclassmembers class * implements android.hardware.SensorEventListener {
    public void onSensorChanged(android.hardware.SensorEvent);
    public void onAccuracyChanged(android.hardware.Sensor, int);
}

# View 的 XML 构造器
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# 保留行号，崩溃栈才有意义
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
