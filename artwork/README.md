# IntelliDo 美术资产

本目录保存 IntelliDo 的原创产品美术资产。视觉语言采用 IDEA 风格的几何产品图标结构，并使用 LINUX DO 语境中的黑、白、黄配色；它不复刻 LINUX DO Logo 或任何 JetBrains 产品 Logo。

## 可直接使用的产物

- `final/app-icon-master.png`：稳定版透明主图标，2048×2048 RGBA。
- `final/app-icon-nightly-master.png`：Nightly 透明主图标，2048×2048 RGBA。
- `final/icons/stable/`：稳定版 16–1024px PNG 图标，由主图裁掉周围透明边后缩放。`icon.svg` / `icon-16.svg` 由对应 PNG 生成，供 2026.2 ApplicationInfo 的 svg 属性使用，不是另一套绘制。
- `final/icons/nightly/`：Nightly 16–1024px PNG 图标；SVG 同样由 PNG 生成。
- `final/intellido.ico`：稳定版 Windows 多尺寸 ICO。
- `final/intellido-nightly.ico`：Nightly Windows 多尺寸 ICO。
- `final/splash.png`：稳定版启动画面母版，2048×1152 RGB。
- `final/splash-window.png`：稳定版产品启动窗口，800×450 RGB。平台把 PNG 像素尺寸当成窗口大小，因此安装包使用这一份，而不是母版。
- `final/splash-nightly.png`：Nightly 启动画面母版，2048×1152 RGB。
- `final/splash-window-nightly.png`：Nightly 产品启动窗口，800×450 RGB。

启动画面母版只内嵌 `ID` 标记。`splash-window.png` 保持无字；构建时把产品名、版本号和构建信息盖上去，交给平台原生 Splash 在主窗口出现前显示。不要再叠加第二层启动窗。

## 来源与复现

`source/` 保存 `gpt-image-2` 返回并完成透明背景提取后的原始生成文件，`prompts/` 保存生成和定向编辑提示词。第三方兼容端点返回的原始尺寸可能与请求值不同，因此最终产物由 `scripts/export-raster-assets.py` 统一裁切、缩放并导出：

```powershell
python artwork/scripts/export-raster-assets.py
```

脚本需要 Pillow。主色参考：近黑 `#111111`、白色 `#FFFFFF`、信号黄 `#F6C344`。

## 许可边界

这些名称、图标和品牌美术资产不因与源码位于同一仓库而自动适用 Apache License 2.0；其许可遵循项目单独发布的品牌资产政策。
