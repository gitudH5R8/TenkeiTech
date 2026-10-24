<h1 align="center">TenkeiTech</h1>

<p align="center"><em>面向 GTNH 的 ME 自动化扩展与多功能工具</em></p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.7.10-62B47A" alt="Minecraft 1.7.10">
  <img src="https://img.shields.io/badge/GTNH-2.9.0--beta--3-3F51B5" alt="GTNH 2.9.0-beta-3">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0--only-blue" alt="License: GPL-3.0-only"></a>
</p>

<p align="center"><a href="#简体中文">简体中文</a> · <a href="#english">English</a></p>

## 简体中文

TenkeiTech 是面向 **GregTech: New Horizons** 的附属模组。它添加大容量样板输入总成、自动模板总线、
无线 ME 网络、谐振样板和无用之杖，让大量配方的管理、机器供料和日常采集建造更方便。

[方块与机器](#blocks) · [工具与物品](#items) · [性能优化](#optimizations) · [安装与配置](#installation)

> TenkeiTech 是玩家制作的非官方 GTNH 附属模组，不属于 GT New Horizons 官方项目。

<a id="blocks"></a>

### 方块与机器

| 新增方块 | 主要用途 |
| --- | --- |
| ME 超级样板总成 | 集中存放处理样板，为多方块提供物品与流体输入 |
| ME 超级样板总成镜像 | 让另一台机器使用已绑定总成的输入 |
| 固定模板自动 ME 输入总线 | 根据形状或模具自动提供挤压、流体固化配方 |
| 固定模板自动 ME 输入总线镜像 | 与已绑定的模板总线共享材料 |
| 线缆机模板输入总线 | 自动提供七种电路对应的导线加工配方 |
| 卷板机模板输入总线 | 自动提供七种电路对应的板材、箔加工配方 |
| 无线 ME 网络核心 | 将 GT ME 仓室无线接入主 ME 网络 |
| 无线 ME 网络收藏器 | 选择常用网络，方便批量绑定仓室 |

#### ME 超级样板总成

面向拥有大量加工配方的 GT 多方块。一台总成提供 **162 个独立样板槽**，分三页显示，也能在 AE 接口终端中集中管理。

- 支持物品、流体及混合处理样板，各槽材料独立存放。
- 支持样板独立编程电路，普通处理样板可批量写入或清除电路；电路不参与消耗。
- 中键点击样板槽可设置催化剂。使用带自动催化剂标记的样板时，缺少的物品催化剂可从 ME 获取；有合成配方时可自动追加合成并安装。
- 提供 **8 个共享物品补货槽和 8 个共享流体补货槽**，可设置目标数量、定时补货和配方启动时补货。
  共享材料供全部样板使用，也可在未安装样板时按机器当前配方供料。
- 保留手动物品槽、共享电路和共享流体催化剂。流体催化剂不提供可消耗流体，实际用料请放入供料槽。
- 支持智能倍增发料与大数量输入缓存，无需手动放大编码样板中的数量。
- 安装 **Wildcard Pattern** 后可使用通配样板。
- 支持按样板类别切换适用的多模式机器，详见[多模式机器自动切换](#machine-modes)。

多方块仍需另配物品／流体输出部件。共享补货会实际提取 ME 中的材料，设置超大目标量会影响性能。

**配套镜像：**使用数据棒绑定总成，让镜像所在机器共享主机输入；使用螺丝刀切换配方读取顺序。
镜像与主机共用材料和样板。

**鸿蒙之眼：**可额外安装一个超级样板总成或有效镜像，但仍需保留原本的一个普通物品输入总线和两个普通流体输入仓。

#### 固定模板与固定电路输入总线

适合集中处理大量材料的挤压、固化、拉丝和卷板配方。总线会自动向 ME 提供适用的加工配方，
不需要为每种材料单独编码样板，也不需要由 ME 提供形状、模具或编程电路。

| 总线 | 模板／电路 | 提供的配方 |
| --- | --- | --- |
| 固定模板自动 ME 输入总线 | 挤压器形状或流体固化模具 | 锭的挤压加工，或对应材料的流体固化 |
| 线缆机模板输入总线 | 1、2、3、4、8、12、16 | 单倍、双倍、细、四倍、八倍、十二倍、十六倍导线 |
| 卷板机模板输入总线 | 1、2、3、4、5、9、10 | 板、双重板、三重板、四重板、五重板、致密板、箔 |

- **绑定模板：**将固定模板总线与形状或模具无序合成，模板不会消耗；放置后可手持模板潜行右键重新绑定。
- **选择配方：**打开 GUI 可按产物名称搜索，逐项、按电压层或整页启用／禁用。批量操作仍包含被搜索隐藏的配方。
- **电路分页：**线缆机和卷板机各有七页独立设置与供料，所有已启用页面同时向 ME 提供配方。
- **自动适配：**按机器实际电压筛选配方；适用的多模式机器可自动切换。缺少对应机器模式时，界面会提示并停用相关配方。
- **退回材料：**界面提供手动退回按钮；更换模板或修改配方开关前，会先将相关材料退回 ME，未退完则保持原设置。

总线主要覆盖同一种材料的一进一出加工，例如铜锭加工为铜导线；跨材料转换和不符合要求的配方不会自动加入。
有效模板与绑定配方可在 NEI 中查看。

**配套镜像：**用数据棒绑定同维度、已加载的模板总线，右键镜像打开主机设置。
镜像共享主机材料，可配合其他控制器使用。

#### 无线 ME 网络核心与收藏器

用无线连接代替分散机器之间的长距离布线，适用于支持 ME 的 GT 仓室。

1. 将核心正面接入主 ME 网络，右键打开界面并设置网络名称。
2. 打开收藏器，选择自己或 ServerUtilities 队伍可访问的网络作为当前收藏。
3. 潜行放置 GT ME 仓室即可自动绑定；已有仓室也可通过 GUI 左侧无线按钮选择网络或断开连接。

- 无线绑定无需数据棒，网络所有者及其队员可以绑定、解绑和收藏；只有所有者能改名。
- 无线仓室及经其连接的设备不占用核心侧频道，普通有线设备仍遵循 AE2 的频道规则。
- 支持跨维度连接，由配置控制。**核心与目标都必须保持加载**，本模组不会主动加载区块。
- 每个核心默认最多绑定 **1024 台设备**，可在配置中调整。
- 安装 Waila 时，可直接查看核心、收藏器和已绑定仓室的网络信息。

<a id="items"></a>

### 工具与物品

#### 阻挡过滤卡

让 ME 接口与二合一接口在阻挡模式下忽略指定的物品与流体。
在元件工作台配置过滤内容，提供精确 NBT 和忽略 NBT 两种版本，可独立使用。

#### 自动抽取卡

让 ME 接口与二合一接口自动从相邻库存回收样板产物，并在全部产物经当前接口返网后解锁。
普通接口仅处理物品，二合一接口同时支持物品与流体；支持设置抽取面、智能阻挡及谐振样板。

#### 谐振样板与谐振绑定卡

谐振样板让 **ME 接口按输入槽远程发送材料**。一份配方中的物品和流体可以分别送往不同机器或不同侧面，
适合多点供料及需要分开输入的加工流程。

**谐振样板的用法：**

1. 将普通、终极或 AE2FC 流体编码的**处理样板**与末影珍珠无序合成。
2. 手持样板，潜行滚轮选择输入槽，右键目标方块绑定位置和侧面。每个非空输入都需要绑定。
3. 将样板放入原版 ME 接口或 AE2FC 流体 ME 接口，方块与面板形态均可使用，然后正常从 ME 下单。

再次点击同一侧面可解绑；潜行右键空气可清除当前槽位的绑定。按住 Shift 查看样板时，会按槽显示材料数量及绑定目标。
**产物需要通过现有输出设施送回 ME，谐振样板不会自动回收。**
可搭配自动抽取卡，从当前接口相邻输出库存回收；卡不会访问远程绑定位置。

将谐振样板单独合成可还原原编码样板。也可放入样板终端编辑，但重新编码保存会移除谐振绑定；未保存直接取出则保留。

**谐振绑定卡：**用 AE 内存卡与末影珍珠无序合成，适合给多张样板复用一组目标。
潜行滚轮选槽，潜行右键切换物品／流体模式，右键方块绑定；与处理样板或谐振样板合成后，按两类输入各自顺序复制目标。
卡不会消耗，未设置的位置保留样板原有绑定。

不支持合成样板或替代输入。阻挡模式下，同一目标的多个流体输入须为同种流体、同一侧面。

<details>
<summary>谐振样板容量限制</summary>

输入、输出各最多 127 槽；每个物品输入最多 1024 件且不超过 16 个正常堆叠，每个流体输入最多 2,147,483,647 mB。

</details>

#### ME 样板总成复制工具与剪切工具

| 工具 | 潜行右键总成 | 右键目标总成 |
| --- | --- | --- |
| 复制工具 | 复制样板与自定义名称 | 粘贴样板，每张消耗一张空白样板，可重复使用 |
| 剪切工具 | 取走样板、催化剂及相关配置 | 将已存配置迁入空总成，成功后工具清空 |

复制不复制催化剂或材料。剪切前会先退回输入材料，退回失败时不会移走配置。

#### 无用之杖（Useless Tools）

集采集、建筑、战斗和辅助功能于一体的工具。具有无限耐久、快速采集、**114514 攻击力**和主手 **+8 格触及距离**，
可切换精准采集 X／时运 X，并常驻抢夺 X。默认在尚未领取的玩家登录时赠送一把，服务器可以关闭赠送。

手持时按 **G** 打开设置，功能按类别排列，悬停按钮可查看简短说明。

| 类别 | 可以做什么 |
| --- | --- |
| 采集 | 普通／增强连锁、大小矿石混合连锁、调节半径与数量、防误挖、基岩移除 |
| 农务 | 自动收获周围成熟作物，可选支持 Crops NH 作物架；剪刀剪切与锄头耕地 |
| 收集 | 采集与击杀掉落收入背包，或优先存入绑定的 ME 网络；收取对应击杀经验 |
| 建筑 | 平面扩建、天使放置、平面破坏、预览与撤回；建造可优先使用 ME 材料 |
| 战斗 | 强制击杀、范围攻击、斩首和生物捕捉；捕捉以刷怪蛋形式获得支持的生物 |
| 辅助 | 携带飞行、持续药效、玩家保护、高级隐身、Ender IO 传送与时间加速 |

##### 采集与收集

- 按住连锁键挖掘，增强连锁的半径和最大数量可在 G 界面调整；可将对应 GT 大、小矿石一起连锁。
- 开启自动收菜并手持工具后，每秒收获周围 **16×16×3** 范围内支持的成熟作物；保护模式下暂停。
  支持Thaumic Bases 的作物；开启“支持作物架”后也可采收 Crops NH 作物，保留原株和架子。
- 潜行右键 AE 无线访问点绑定网络，再次点击同一访问点取消绑定。开启 AE 优先后，掉落先存入 ME，剩余再收入背包或掉落。

##### 三种建筑核心

| 核心 | 操作与效果 |
| --- | --- |
| 建造 | 右键扩建相连的同类表面，单次最多 1024 块；副手可指定建材 |
| 天使 | 使用副手建材，空气右键在前方放一块；点击方块可向背面搜索最多 16 格放置 |
| 破坏 | 移除点击平面上的相连同类方块，单次最多 81 块，不产生采集掉落 |

开启建筑功能后，按住撤回键并右键最近操作范围或附近位置可撤回，默认保留最近 **3 次**，退出世界会清空历史。
建造核心开启“AE 接入模式”时优先从绑定 ME 取材，不足再使用背包和副手，撤回时优先返还原 ME 网络。
建造与天使核心可放置背包或副手中的 GT 机器物品，建造核心也可从绑定 ME 取材；不会复制已放置机器的库存或配置。
**GT 机器放置不支持撤回，破坏核心不能拆除机器。**其他特殊方块的可用范围以预览为准。

##### 战斗与辅助

- 范围攻击最大 **27×27**，上下各 3 格；强制击杀与追加范围攻击会排除玩家及已识别友方。
- 斩首为支持的骷髅、凋灵骷髅、僵尸和苦力怕额外掉落头颅；生物捕捉不保留原生物的装备、名称等数据。
- 携带即可获得飞行及配置的持续药效，双击跳跃起飞；飞行速度提供 **0.5×、1×、2×、4×、6×** 五档。
- 玩家保护可阻止伤害并清除负面药效；高级隐身隐藏玩家及装备，并避免由自己触发自然刷怪或原版刷怪笼。
  特殊 Boss 和自定义 AI 的效果取决于兼容情况。
- 开启传送后，使用 Ender IO **传送权杖**的操作与距离设置。建筑、收获等右键操作优先，未执行其他操作时才传送。

##### 时间加速

在 G 界面打开时间加速设置，选择倍率与持续方式，再潜行右键目标应用。支持适用机器和随机刻方块，
默认倍率档位最高为 **1024**，可由服务器配置。

- 持续方式分为 **30 秒、永久动态降频、永久无休眠降频**；动态降频可在识别到机器空闲时降低加速频率。
- 执行方式可选普通模式或 GT 模式：普通模式完整加速运行并正常耗能，GT 模式针对 GT 机器推进进度。
- 目标显示倍率与剩余时间；永久目标可在“放置历史”中查看和清理，保存后可跨重启保留。
- 改动设置后需重新应用到目标；关闭总开关会结束自己的全部加速。倍率不保证产出等比例提高，高倍率会增加服务器负担。

##### 样板辅助设置

G 界面的 TenkeiTech 设置提供四项功能：保留编程电路、记录零数量催化剂、将凝聚态替换为原材料流体、
过滤板材切割机配方中的流体。携带工具时对 NEE 写入配方生效；过滤流体只改变写入的样板，机器仍需要正常供液。

##### 常用默认按键

| 按键 | 用途 |
| --- | --- |
| G | 手持无用之杖时打开设置 |
| 按住 ~ | 连锁挖掘 |
| R | 开启相应功能后，移除准星指向的原版基岩 |
| Ctrl + 右键 | 开启建筑功能后撤回最近操作 |
| V / Shift + V | 提高／降低飞行速度 |
| 潜行 + 滚轮 | 开启时间加速并手持工具时切换加速倍率 |

可在 Minecraft 控制设置中调整已注册的快捷键。多个右键功能同时开启时，请留意当前选中的模式。

服务器可限制无用之杖的功能，被禁用的选项会灰显并提示“已禁用”。

<a id="patterns"></a>
<a id="optimizations"></a>

### 性能优化

[从终端上传样板](#pattern-upload) · [多模式机器自动切换](#machine-modes) · [任务跳过](#quest-skipping-cn)

<a id="pattern-upload"></a>

#### 从终端上传样板

在 AE 样板终端完成编码后，点击上传按钮，直接将样板安装到目标接口。

- 按接口名或配方类别搜索，支持分页与右键置顶。
- **Ctrl + U** 打开映射设置，把常用配方类别关联到指定接口。
- 开启自动上传后，点击上传按钮可直接发送到唯一匹配的目标；有多个匹配时手动选择。
- **Shift + 点击上传按钮**可撤回最近一次上传；若目标发生变化或材料无法退回，撤回不会执行。
- 目标已有相同样板时，可将重复样板对应的空白样板退回 ME。

上传需要可用的 ME 网络、操作权限和兼容的空样板槽。搜索或修改映射本身不会上传样板。

<a id="machine-modes"></a>

#### 多模式机器自动切换

让支持多种加工模式的机器按配方自动切换，减少手动操作：

- **超级样板总成及镜像：**从 NEE 转移 GT 配方后编码样板，即可按样板类别分批加工；悬停样板可查看类别。
- **固定模板与固定电路总线：**按形状、模具或电路类别选择模式，无需额外编码样板。

机器必须具备对应模式。更换配方类别或使用没有类别的旧样板时，请重新从 NEE 转移并编码；只改材料不会更新类别。
此功能不会恢复机器停机后已丢失的加工进度。

<a id="quest-skipping-cn"></a>

#### 任务跳过

在任务右键菜单中选择 **其他 → 跳过任务**，确认后完成任务并**放弃该任务奖励**。跳过的任务显示为深绿色。

仅支持已解锁、未完成且不消耗物品、流体或经验的部分任务，重复与全局任务除外。需要服务器允许并安装 BetterQuesting。

<a id="installation"></a>

### 安装与配置

当前适配 **Minecraft 1.7.10 / GTNH 2.9.0-beta-3**，需要以下组件：

| 必需组件 | 版本 |
| --- | --- |
| GT5-Unofficial | 5.09.54.133 |
| Applied Energistics 2 | rv3-beta-1050-GTNH |
| AE2 Fluid Crafting | 1.5.106-gtnh |
| ServerUtilities | 2.4.9 |

1. 将普通玩家用 JAR 放入 GTNH 实例的 mods 文件夹，移除旧版本；不要安装带 -dev 或 -sources 的开发产物。
2. 联机时，客户端与服务器安装相同版本，并保持依赖一致。
3. 在 NEI 中查看本模组物品、已注册配方与有效模板变体。

| 可选联动 | 提供的功能 |
| --- | --- |
| NotEnoughEnergistics（NEE） | 配方转移、样板类别识别与无用之杖的样板辅助设置 |
| Wildcard Pattern | 超级样板总成的通配样板支持 |
| Backhand | 无用之杖的副手建材选择及副手远距放置兼容 |
| Crops NH | 无用之杖自动收获作物架 |
| Ender IO | 无用之杖的传送功能 |
| Waila | 机器库存及无线网络信息显示 |
| BetterQuesting 3.8.84-GTNH | 跳过任务，放弃对应奖励 |

通过 **Mods → TenkeiTech → Config** 调整样板上传、无线网络和无用工具设置；联机以服务器配置为准。
样板上传总开关修改后需要重启。机器 ID 如需避让冲突，应在首次创建世界前修改 config/TenkeiTech.cfg，已有世界不应随意改动。

**服主注意：**在配置中禁用时间加速会清除本存档所有玩家的永久加速目标，重新启用不会恢复。

### 社区项目与致谢

本模组自身的问题请反馈到本项目，并附上模组版本、相关机器或操作步骤；崩溃时附上日志。

感谢 GT New Horizons、GregTech、AE2 及相关模组提供的基础功能。
同时感谢以下项目及其作者：

- [**GTLAdditions**](https://github.com/Dragonators/GTLAdditions)：ME 超级样板总成的功能参考来源。
- [**GTLCore**](https://github.com/GNSW/GTLCore)：无线 ME 网络核心与无线 ME 网络收藏器的无线 ME 网络功能参考来源。
- [**ExtendedAE_Plus**](https://github.com/GaLicn/ExtendedAE_Plus)：从终端上传样板的功能移植自该项目。
- [**AE2-Crystal-Science**](https://github.com/ExtremelyFrozen/AE2-Crystal-Science)：谐振样板移植自该项目。
- [**UselessMod**](https://github.com/SorrowMist/UselessMod)：无用之杖移植自该项目。
- [**ConstructionWand**](https://github.com/Theta-Dev/ConstructionWand)：无用之杖的建筑功能参考了该项目。

除另有标注外，本项目原创代码与原创资源采用 [GNU GPL v3.0（仅限第 3 版）](LICENSE)。保留原有 LGPL 与 MIT 文件许可及第三方版权信息，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。

---

## English

**TenkeiTech** is an addon for **GregTech: New Horizons**. It adds large pattern buffers, automatic template input buses,
wireless ME connections, remote ingredient routing, and a versatile tool for mining, building and everyday utility.

[Blocks and Machines](#blocks-and-machines) · [Items and Tools](#items-and-tools) · [Performance Optimizations](#performance-optimizations) · [Installation and Configuration](#installation-and-configuration)

This is an unofficial community addon, not an official GTNH project.

### Blocks and Machines

| Block | Purpose |
| --- | --- |
| ME Super Pattern Buffer | Stores processing patterns and supplies multiblocks with items and fluids |
| ME Super Pattern Buffer Proxy | Shares a linked buffer's inputs with another machine |
| Auto Template ME Input Bus | Provides Extruder or Fluid Solidifier recipes from a bound shape or mold |
| Auto Template ME Input Bus Mirror | Shares materials from a linked template bus |
| Wiremill Template ME Input Bus | Provides wire recipes for seven circuit settings |
| Bender Template ME Input Bus | Provides plate and foil recipes for seven circuit settings |
| Wireless ME Network Core | Connects GT ME hatches to the main network wirelessly |
| Wireless ME Network Bookmark | Selects a favorite network for convenient hatch binding |

#### ME Super Pattern Buffer

A large processing-pattern input for GT multiblocks, with **162 independent slots** across three pages and access through the AE Interface Terminal.

- Supports item, fluid and mixed processing patterns, with separate materials for each slot.
- Supports individual programmed circuits and bulk circuit editing for regular processing patterns; circuits are not consumed.
- Middle-click a slot to configure catalysts. Patterns with automatic catalyst markers can obtain missing item catalysts from ME,
  or craft and install them when a recipe is available.
- **Eight item and eight fluid refill slots** keep shared stock topped up on a timer or when a recipe starts.
  Shared materials can also feed the machine's current recipe without an installed pattern.
- Includes manual item slots, a shared circuit and shared fluid catalysts. Catalyst tanks do not supply consumable fluid.
- Batches pattern deliveries automatically and supports large input amounts without manually multiplying encoded recipes.
- Supports wildcard patterns with **Wildcard Pattern** installed.
- Switches suitable machines by recipe category; see [Automatic Machine Mode Switching](#automatic-machine-mode-switching).

The multiblock still needs output buses or hatches. Refills take real materials from ME; excessively large stock targets affect performance.

**Proxy:** bind with a data stick to share the host's inputs. A screwdriver changes recipe order. Materials and patterns are shared with the host.

**Eye of Harmony:** one buffer or valid proxy can be added alongside the required standard item input bus and two standard fluid input hatches.

#### Template Input Buses

These buses provide suitable processing recipes to ME automatically, without encoding every material separately.
ME supplies the ingredients; shapes, molds and circuits do not need to be supplied by the network.

| Bus | Template or circuits | Recipes |
| --- | --- | --- |
| Auto Template | Extruder shape or Fluid Solidifier mold | Ingot extrusion or matching fluid solidification |
| Wiremill | 1, 2, 3, 4, 8, 12, 16 | 1x, 2x, fine, 4x, 8x, 12x, 16x wire |
| Bender | 1, 2, 3, 4, 5, 9, 10 | Plate, double/triple/quadruple/quintuple/dense plate, foil |

Craft a template bus with a shape or mold to bind it without consuming the template. Sneak-right-click a placed bus with a template to rebind.
The GUI supports output-name search, individual recipe toggles, voltage-tier toggles and whole-page controls.
Wiremill and Bender buses have seven independently supplied pages; all enabled pages remain available to ME.

Recipes follow the machine's voltage and supported modes. Only suitable single-input, single-output recipes for the same material are included.
Changing settings first returns affected materials to ME; if any remain, the settings stay unchanged. A separate button returns materials manually.

**Mirror:** use a data stick to link a loaded bus in the same dimension. Right-click opens the host settings.
The mirror shares the host's materials with another controller.

#### Wireless ME Network

1. Connect the core's front face to the main ME network and give the network a name.
2. Use the bookmark to favorite a network owned by you or accessible through your ServerUtilities team.
3. Sneak-place supported GT ME hatches to bind them, or use the wireless button in an existing hatch's GUI.

No data stick is needed. Owners and teammates can bind, unbind and favorite networks; renaming is owner-only.
Bound hatches and devices connected through them do not consume channels at the core; ordinary wired devices retain AE2's channel rules.
Cross-dimension links are configurable, and **both ends must remain loaded**. TenkeiTech does not load chunks for you.
Each core allows **1024 bindings** by default. Waila can show network information when installed.

### Items and Tools

#### Blocking Filter Cards

Let ME and dual interfaces ignore selected items and fluids in blocking mode.
Configure filters in the Cell Workbench; exact-NBT and ignore-NBT variants are available and work independently.

#### Automatic Extraction Card

Let ME and dual interfaces automatically recover pattern outputs from adjacent inventories and unlock once all outputs return through the same interface.
ME interfaces handle items; dual interfaces handle items and fluids. Supports configurable extraction faces, smart blocking and Resonating Patterns.

#### Resonating Pattern and Binding Card

Resonating Patterns let ME Interfaces send each ingredient to a different location or face.
Craft a regular, ultimate or AE2FC fluid **processing pattern** with an Ender Pearl, then sneak-scroll to select an input and right-click its destination.
Bind every nonempty input before placing the pattern in a vanilla or AE2FC Fluid ME Interface; both blocks and parts are supported.

Right-click the same face again, or sneak-use in air, to clear the selected binding. Hold Shift over the pattern to see each input and destination.
**Outputs must be returned by your existing automation.** Craft the pattern alone to restore its original type.
An Automatic Extraction Card can recover outputs from inventories beside this interface; it does not access remote bindings.
Editing and re-encoding it in a terminal also removes routing; removing it without saving preserves the bindings.

Craft a **Resonating Binding Card** from an AE Memory Card and Ender Pearl. Sneak-scroll selects a slot, sneak-use switches item/fluid mode,
and right-click binds a destination. Craft it with a processing or Resonating Pattern to copy targets in item/fluid input order.
The card is reusable; unset card slots preserve existing pattern bindings.

Crafting patterns and substitute inputs are unsupported.
In blocking mode, fluid inputs sharing one destination block must use the same fluid and side.

<details>
<summary>Resonating Pattern capacity limits</summary>

Input and output limits are 127 slots each. Each item input allows up to 1024 items in no more than 16 normal stacks;
each fluid input allows up to 2,147,483,647 mB.

</details>

#### Pattern Buffer Copy and Cut Tools

- **Copy:** sneak-right-click a buffer to copy its patterns and name; right-click a target to paste, using one blank pattern per copy.
  Catalysts and materials are not copied.
- **Cut:** sneak-right-click to take patterns, catalysts and related settings after returning input materials; right-click an empty buffer to move them there.
  Successful pasting empties the tool.

#### Useless Tools / Universal Wand

An all-purpose tool with infinite durability, fast mining, **114514 attack damage**, **+8 main-hand reach**,
Silk Touch X or Fortune X, and permanent Looting X. By default, players receive one on login if they have never received the gift; servers can disable this.
Hold it and press **G** to configure its functions.

| Category | Features |
| --- | --- |
| Mining | Normal/enhanced chain mining, mixed GT large/small ores, adjustable radius and count, protection mode, bedrock removal |
| Farming | Automatic harvesting in a 16x16x3 area, optional Crops NH crop sticks, shears and hoe |
| Collection | Collect harvest and kill drops into inventory or bound ME; collect corresponding kill experience |
| Building | Surface extension, angel placement, planar destruction, previews, undo and ME materials |
| Combat | Force kill, area attacks, extra skulls and spawn eggs for supported mobs |
| Utility | Flight, potion effects, player protection, advanced stealth, Ender IO teleportation and time acceleration |

##### Mining and Collection

- Hold the chain-mining key while mining. Adjust the enhanced radius and block limit in G settings; matching GT large and small ores can be mined together.
- With automatic harvesting enabled, hold the tool to harvest supported mature crops in a **16x16x3** area every second; protection mode pauses it.
  Supports Thaumic Bases crops. Enable crop-stick support to harvest Crops NH crops while retaining the crops and sticks.
- Sneak-right-click an AE Wireless Access Point to bind it; repeat on the same point to unbind.
  AE priority stores drops in ME first, then inventory or the ground.

##### Building Cores

| Core | Action |
| --- | --- |
| Construct | Right-click to extend a matching surface by up to **1024 blocks**; the offhand can select materials |
| Angel | Place one offhand block in the air, or search up to **16 blocks** behind a clicked surface |
| Destroy | Remove up to **81 connected blocks** on the clicked plane without harvest drops |

With building enabled, hold the undo key and right-click near the latest operation to undo it. History keeps **3 operations** by default and clears on logout.
The Construct core can use bound ME materials first, falling back to inventory/offhand; undo returns materials to the original ME network first.
Construct and Angel can place GT machine items from inventory or the offhand; Construct can also use bound ME stock.
Placed machines' inventories and settings are not copied. **GT machine placements cannot be undone, and Destroy cannot remove machines.**
Check the preview for other special-block restrictions.

##### Combat and Utility

- Area attacks extend up to **27x27**, three blocks above and below. Force kill and secondary area attacks exclude players and recognized allies.
- Beheading adds skull drops for supported skeletons, wither skeletons, zombies and creepers. Captured mobs' spawn eggs do not preserve equipment or names.
- Carry the wand for flight and configured potion effects; double-tap jump to fly, with **0.5x, 1x, 2x, 4x and 6x** speed settings.
- Player protection blocks damage and clears harmful effects. Advanced stealth hides the player and equipment and stops that player from activating natural spawning or vanilla spawners.
  Special bosses and custom AI depend on compatibility.
- Teleport uses Ender IO's **Staff of Teleportation** controls and range settings. Other right-click actions, such as building or harvesting, take priority; teleport occurs when no other action succeeds.

##### Time Acceleration

Choose a rate and duration in G settings, then sneak-right-click a supported machine or random-ticking block.
Default rates reach **1024**, configurable by the server. Duration choices are **30 seconds**, **permanent with dynamic throttling**, and **permanent without idle throttling**.
Normal mode speeds up full operation with normal energy use; GT mode advances GT machine progress. Reapply after changing settings.
Targets show their rate and remaining time; permanent targets survive restarts and can be removed through placement history.
Turning the master switch off ends your acceleration tasks. Rates do not guarantee proportional output and high settings increase server load.

##### Pattern Helpers

While carrying the wand, NEE recipe transfers can preserve programmed circuits, record zero-amount catalysts,
replace condensate with source fluids, or omit Cutter fluids. Omitting fluids from a pattern does not remove the machine's actual fluid requirement.

##### Default Controls

| Default control | Action |
| --- | --- |
| G | Open settings while holding the wand |
| Hold ~ | Chain mining |
| R | Remove targeted vanilla bedrock when enabled |
| Ctrl + right-click | Undo the latest building operation when building is enabled |
| V / Shift + V | Increase/decrease flight speed |
| Sneak-scroll | Change acceleration rate while holding the wand with acceleration enabled |

Registered key bindings can be changed in Minecraft's controls menu.

Servers can restrict wand features. Disabled options appear grayed out and show a disabled tooltip.

### Performance Optimizations

[Terminal Pattern Uploads](#terminal-pattern-uploads) · [Automatic Machine Mode Switching](#automatic-machine-mode-switching) · [Quest Skipping](#quest-skipping-english)

#### Terminal Pattern Uploads

Encode a pattern in an AE Pattern Terminal, then click upload to install it directly in a target interface.

- Search by interface or recipe category, browse pages, and right-click to pin a target.
- **Ctrl + U** opens mappings to link recipe categories to interfaces.
- With automatic upload enabled, clicking upload sends the pattern to the unique matching target; choose manually when several match.
- **Shift-click the upload button** to undo the last upload. Undo is unavailable if the target changed or materials cannot be returned.
- Duplicate patterns can return a blank pattern to ME.

Uploads require a working ME network, permission and compatible empty slots. Searching or editing mappings does not upload patterns by itself.

#### Automatic Machine Mode Switching

Suitable machines switch processing modes to match their recipes:

- **Super Pattern Buffers and proxies:** transfer a GT recipe through NEE and encode it to process recipes in category groups. Hover over a pattern to see its category.
- **Template and circuit input buses:** select modes from their shape, mold or circuit category, without extra encoded patterns.

The machine must support the requested mode. Transfer and encode the recipe again to change categories or replace patterns without a category;
editing ingredients alone does not update it. This feature cannot restore processing progress already lost when a machine stopped.

<a id="quest-skipping-english"></a>

#### Quest Skipping

Choose **Other → Skip quest** in a quest's context menu and confirm to complete it while **forfeiting its rewards**. Skipped quests appear dark green.

Supports some unlocked, incomplete quests that consume no items, fluids or XP; repeatable and global quests are excluded. Requires BetterQuesting and server permission.

### Installation and Configuration

The current target is **Minecraft 1.7.10 / GTNH 2.9.0-beta-3**, with GT5-Unofficial **5.09.54.133**,
AE2 **rv3-beta-1050-GTNH**, AE2 Fluid Crafting **1.5.106-gtnh**, and ServerUtilities **2.4.9**.

Place the regular player JAR in the instance's mods folder and remove older copies. Do not install -dev or -sources artifacts.
Multiplayer clients and servers need matching versions and dependencies. NEI shows registered recipes and valid template variants.

| Optional integration | Feature |
| --- | --- |
| NotEnoughEnergistics | Recipe transfer, pattern categories and wand pattern helpers |
| Wildcard Pattern | Wildcard patterns in the Super Pattern Buffer |
| Backhand | Offhand building materials and extended-reach offhand placement |
| Crops NH | Automatic crop-stick harvesting |
| Ender IO | Wand teleportation |
| Waila | Machine stock and wireless network information |
| BetterQuesting 3.8.84-GTNH | Skip quests and forfeit their rewards |

Open **Mods → TenkeiTech → Config** for Pattern Upload, Wireless ME and Universal Wand settings. Server settings apply in multiplayer.
Changing the upload master switch requires a restart. Resolve machine-ID conflicts in config/TenkeiTech.cfg before creating a world; avoid changing IDs in an existing save.

**For server owners:** disabling time acceleration in the config clears all players' permanent acceleration targets in this save. Enabling it again does not restore them.

### Credits and License

Please report issues to this project with the mod version, affected feature and reproduction steps; include logs for crashes.
Thanks to GTNH, GregTech, AE2 and the related projects.
Special thanks to the following projects and their authors:

- [**GTLAdditions**](https://github.com/Dragonators/GTLAdditions): a functional reference for the ME Super Pattern Buffer.
- [**GTLCore**](https://github.com/GNSW/GTLCore): the wireless ME network implementation inspired the Wireless ME Network Core and Wireless ME Network Bookmark.
- [**ExtendedAE_Plus**](https://github.com/GaLicn/ExtendedAE_Plus): terminal pattern uploads are ported from this project.
- [**AE2-Crystal-Science**](https://github.com/ExtremelyFrozen/AE2-Crystal-Science): Resonating Patterns are ported from this project.
- [**UselessMod**](https://github.com/SorrowMist/UselessMod): Useless Tools / Universal Wand is ported from this project.
- [**ConstructionWand**](https://github.com/Theta-Dev/ConstructionWand): the wand's building features draw on this project.

Unless otherwise marked, this project's original code and original assets use [GNU GPL v3.0 only](LICENSE). Existing LGPL and MIT file licenses and third-party copyright notices are retained; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
