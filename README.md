# 胶片测光表 · lightmeter-apk

无广告的胶片相机测光表。读取手机环境光传感器，直接换算成光圈/快门组合。

## 功能

- **真测光**：读 `TYPE_LIGHT` 环境光传感器，不是查表估算
- **EV 换算**：实时显示 EV、尺烛光和推荐快门
- **ISO 可调**：100 – 3200
- **光圈/快门互算**：知道 EV 反推组合，或已知快门反推光圈

## 构建

```bash
# 调试版（包名带 .debug 后缀，可与正式版共存）
./gradlew assembleDebug

# 正式版
./gradlew assembleRelease
```

需要 JDK 17 + Android SDK 34。

## 发布签名

**仓库里不带任何密钥。** 正式版签名从 `keystore.properties` 读取：

1. 生成密钥库：

```bash
keytool -genkeypair -v -keystore release.jks -alias lightmeter \
  -keyalg RSA -keysize 2048 -validity 10000
```

2. 把 `keystore.properties.example` 复制成 `keystore.properties` 并填好
3. `./gradlew assembleRelease`

没有这个文件时，release 会回退到 **debug 签名**——能装能跑，但**不能上 Google Play**，
而且每台机器的 debug key 不同，导致同包名无法互相覆盖安装。

验证当前 APK 用的是哪个签名：

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

看到 `CN=Android Debug` 就是 debug 签名。

> ⚠️ 密钥库丢了，已上架的应用就再也无法更新。务必离线备份 `release.jks`。

## 混淆

`release` 开启了 `minifyEnabled` + `shrinkResources`，体积从 5.7MB 降到 2.4MB。

`proguard-rules.pro` 里保留了：

- `MainActivity` / `CalcActivity`（系统按名字反射创建，混淆就闪退）
- 传感器回调方法（框架反射查找）
- View 的 XML 构造器
- 行号信息（崩溃栈才有意义）

改混淆规则后务必验证两个 Activity 还在：

```bash
aapt2 dump xmltree app-release.apk --file AndroidManifest.xml | grep Activity
```

## 权限

只需要环境光传感器，声明为 `required="false"`——没有光感设备的机器也能装，
运行时会有「无传感器」提示。

## 许可

MIT
