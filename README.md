# 胶片测光表 · lightmeter-apk

无广告的胶片相机测光表。读取手机环境光传感器，直接换算成光圈/快门组合。

## 功能

- **真测光**：读 `TYPE_LIGHT` 环境光传感器，不是查表估算
- **EV 换算**：实时显示 EV、尺烛光和推荐快门
- **ISO 可调**：100 – 3200
- **光圈/快门互算**：知道 EV 反推组合，或已知快门反推光圈
- **长曝光补偿**：倒易律失效计算，内置 6 款常见胶片的补偿系数
- **拍摄记录**：一键记下当时的 EV / 光圈 / 快门 / ISO，等冲洗出来对照

## 拍摄记录

胶片拍完要等一周才冲洗。等到看片时，「这张是在窗边还是傍晚拍的」基本靠猜。

测完光按「记一笔」，会存下：时间、照度、EV、ISO、推荐光圈与快门、可选备注。
长按单条可删除，底部可一键复制全部记录（纯文本，方便粘到备忘录）。

- 记录存在应用私有目录，不需要任何存储权限，卸载即清空
- 最多保留 200 条，超出丢弃最老的
- 存档损坏时按「没有记录」处理，不会让 App 打不开

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
