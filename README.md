# 施工队：多多采购 / Construction DuoDuo Supply

Minecraft 1.20.1 Forge 附属：施工终端按当前工程缺料，从多多卖菜系统商城勾选采购。

## 硬依赖

- 车万女仆：施工队 (`touhou_maid_construction`)
- 多多卖菜 (`homestead_sell_veg`)
- 车万女仆 (`touhou_little_maid`，随施工队)

## Build

先分别 `build` 施工队与多多卖菜（生成 `build/libs`），再：

```bat
gradlew.bat build
```
