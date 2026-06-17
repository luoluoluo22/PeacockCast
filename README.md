# 孔雀开屏 (PeacockCast)

孔雀开屏 (PeacockCast) 是一款面向 AR 眼镜及外接辅助显示器的智能投屏与空间管理伴侣应用。它能够将手机画面无缝延展至外接设备，支持双屏异显、屏幕镜像投屏、多窗口排列与显示密度调整，旨在为 AR 设备和外设提供更加华丽、高效的多任务工作空间。

## 🌟 核心功能

1. **AR 独立空间与双屏异显 (DemoPresentation)**
   - 在检测到的外接显示器或 AR 眼镜上，启动完全独立的 [DemoPresentation](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/DemoPresentation.java)，在不占用手机主屏的同时展示专属的 3D 或 2D 界面。
2. **极速屏幕镜像 (MirrorService)**
   - 基于 Android `MediaProjection` 技术，低延迟地将手机屏幕实时镜像到外接设备中，用于大屏分享与演示。
3. **空间窗口管理器 (Window Manager)**
   - 支持多应用窗口排列、自动填充正在运行的应用列表，帮助用户在 AR 空间中轻松管理多个程序。
4. **屏幕密度智能自适应**
   - 提供屏幕紧凑密度（DPI）调节和重置功能，使应用能够完美适配 AR 眼镜的显示特性，获取更大的显示区域和更细腻的文字效果。
5. **快捷设置瓷砖 (Quick Settings Tile)**
   - 提供系统级的快捷按钮 [AirBeamTileService](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/AirBeamTileService.java)，在系统下拉通知栏中即可一键控制 AR 服务的开启和关闭。

## 📂 核心代码结构

- [MainActivity.java](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/MainActivity.java)：程序主入口，提供开关服务、镜像开关、窗口重排、密度缩放等控制交互。
- [CompanionService.java](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/CompanionService.java)：核心前台服务，前台类型为 `specialUse`，负责 AR 交互相关的后台生命周期管理。
- [MirrorService.java](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/MirrorService.java)：投屏镜像前台服务，前台类型为 `mediaProjection`，负责屏幕捕获与流转。
- [DemoPresentation.java](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/DemoPresentation.java)：副屏演示 Presentation 逻辑，定义了在外接 AR 屏幕上呈现的独立 UI。
- [AirBeamTileService.java](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/java/com/yukino/airbeamcompanion/AirBeamTileService.java)：快捷设置磁贴，提供便捷的服务开关。
- [AndroidManifest.xml](file:///c:/Users/Administrator/Documents/antigravity/modest-davinci/AirBeamCompanion/app/src/main/AndroidManifest.xml)：声明了应用所需的权限（如 `FOREGROUND_SERVICE`、`WAKE_LOCK`、`MEDIA_PROJECTION`）及组件的 foregroundServiceType。

## 🛠️ 构建与运行

1. 使用 Android Studio 打开此项目根目录。
2. 确保已连接支持 DisplayPort Alternate Mode (DP Alt Mode) 的手机以及 AR 眼镜或外接显示器。
3. 编译并安装应用到手机中，开启“孔雀开屏”服务，体验大屏流转。
