<div align="center">
  <img src="images/brand.png" alt="logo" width="300"/>
  <h1>YSM-Multiplayer</h1>
  <p>在任意多人服务器上使用本地 YSM 自定义模型</p>

  ![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green?style=flat-square)
  ![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=flat-square)
  ![YSM](https://img.shields.io/badge/YSM-2.6.5.3-orange?style=flat-square)
</div>

---

## 简介

标准的 [Yes Steve Model](https://www.curseforge.com/minecraft/mc-mods/yes-steve-model) 要求服务端和客户端安装相同版本的 YSM 才能正常显示自定义模型。连接到没有安装 YSM、版本不兼容、或主动禁用客户端模型的服务器时，模型会被强制还原为原版皮肤。

**YSM-Multiplayer** 是对 OpenYSM 2.6.5.3（Minecraft 1.21.8 Fabric）的客户端补丁，让你在任意服务器上都能使用存放在本地的自定义模型，完全不依赖服务端。

---

## 功能

### 🌐 多人服务器本地模型加载
进入任意多人服务器时自动加载本地模型，无需服务端安装或配置 YSM。

### 🔒 抵御服务端禁用指令
服务端发送 `disabled=true` 数据包时，本地玩家的模型不受影响，始终保持显示。

### 🔄 子服 / 维度切换无缝保持
切换子服务器或跨维度传送后，模型不会重置为默认状态，保持玩家设置的模型和贴图。

### ✨ 修复原版皮肤闪现
修复了与 EntityCulling 等渲染优化 mod 共存时，切换子服后短暂出现一帧原版皮肤的问题。

### 🖼️ 修复 GUI 预览崩溃
修复了在多人服务器上打开模型选择界面时偶发的崩溃问题。

---

## 模型文件放置

将 `.zip` 或 `.ysm` 格式的模型文件放入以下目录，进入服务器后会自动加载：

```
.minecraft/
└── config/
    └── yes_steve_model/
        ├── custom/   ← 放置自定义模型（.zip / .ysm）
        └── built/    ← 内置模型
```

---

## 兼容性

| Mod | 兼容状态 | 备注 |
|-----|---------|------|
| EntityCulling | ✅ | 已修复共存时的闪现问题 |
| ImmediatelyFast | ✅ | |
| Iris + Sodium | ✅ | |
| CustomSkinLoader | ✅ | |
| FirstPerson Mod | ✅ | |
| Shoulder Surfing | ✅ | |

---

## 编译

**环境要求：** Java 21、Gradle

```bash
git clone https://github.com/Justice-ocr/YSM-Multiplayer.git
cd YSM-Multiplayer
gradle :fabric:build
```

编译产物位于 `fabric/build/libs/`。

---

## 已知问题

- 第一人称视角下低头查看完整身体模型的功能暂未完全适配

---

## 致谢

- [OpenYSM](https://github.com/OpenYSM/yes_steve_model) — 本项目基于此移植版本修改
- [Yes Steve Model](https://www.curseforge.com/minecraft/mc-mods/yes-steve-model) — 原版 mod 作者
