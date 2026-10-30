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

# 倒易律补偿：纯计算类，但会被自定义 ArrayAdapter 与 Spinner 回调引用，
# 保留类名便于混淆后的崩溃栈定位（ProGuard 会保留被引用到的成员，
# 这里加规则是为了保证 toString/枚举式访问不被裁掉）
-keep class com.jikegang.lightmeter.Reciprocity { *; }
-keep class com.jikegang.lightmeter.Reciprocity$Profile { *; }
