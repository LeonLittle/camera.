# T10s 家庭监控固件

基于原厂 `B153_MDHL_001_M135_7.1_20251223` 固件建立的监控端工程。第一阶段保留
MT6735M 内核、Camera HAL、ISP 和 S5K3L2/OV8858 等传感器驱动，只增加 Android 应用层。

## 当前硬件验证版本

- Android 7.1 / API 25
- 开机启动监控服务
- 自动选择后置摄像头
- 选择最接近 1280×720 的预览尺寸
- 通过 `http://设备IP:8080/live.mjpg` 提供局域网 MJPEG 预览
- 每秒最多5帧，JPEG质量70，用于第一轮稳定性和温度测试
- 已加入24小时及容量双重清理策略的基础代码

当前版本尚未把设备端口暴露到公网，也尚未加入正式 H.264/WebRTC 远程链路。公网实时
查看会在确认原厂 Camera HAL 稳定后接入设备主动外连的加密通道。

## 构建

用 Android Studio 打开仓库根目录，安装 Android SDK 33，然后构建 `app`。目标设备最低
版本为 Android 7.1（API 25）。首次安装后授予摄像头、麦克风和存储权限。

## 实机验证

```text
adb install -r app-debug.apk
adb logcat -s T10sMonitor T10sCamera T10sMjpeg
```

启动后在同一局域网浏览器中打开 `http://设备IP:8080/live.mjpg`。

## 安全边界

本固件基线的 Android 安全补丁为 2017-05-05。不要在路由器中把8080端口映射到公网。
