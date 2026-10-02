# MetroMap (fabric-metro-map)

Minecraft **Java Edition 1.21.8** + **Fabric Loader 0.17.2** 客户端模组。
根据玩家行走的路线，生成一张**轨道交通线路图**，并可导出为 JSON 文件。

> ⚠️ 重要说明见文末「关于 PaperMC 服务器」。

---

## 1. 功能概览

| 功能 | 说明 |
|---|---|
| 线路（Line）管理 | 每条线路至少包含 `id` / `name` / `color` / `operator`，另含状态、创建时间等 |
| 站点（Station）设置 | 按键绑定在当前位置设站，**自动为该站拍照**（截图存档为 PNG） |
| 路径识别 | 模组持续记录玩家走动路径（带采样去噪），第二个站点设置时自动生成**线路段（Segment）** |
| 多站多段 | 一条线路 = N 个站点 + N-1 个线路段；继续设第 3、4… 个站会依次追加线路段 |
| 即时保存 | 每次操作（建线/设站/成段/改名/改色…）后**原子写入磁盘**（先写 `.tmp` 再 rename），随时关游戏不丢数据 |
| JSON 导出 | 导出到 `config/metro_map/export/`，同时提供 `/mm export` 命令与导出按键 |

## 2. 指令（服务端安装本模组的 jar 后可用，OP 权限）

```
/mm line create <线路名> [颜色hex] [运营商]   新建线路并设为当前工作线路
/mm line list                                 列出所有线路
/mm line select <线路ID>                      切换当前工作线路
/mm line delete <线路ID>                      删除线路（含其站点与线路段）
/mm line name <线路ID> <新名称>               修改线路名
/mm line color <线路ID> <hex如ff5050>         修改线路颜色
/mm line operator <线路ID> <运营商名>          修改运营商
/mm station <站名>                            在当前工作线路的当前位置设站（等效默认按键）
/mm export                                    导出 JSON（config/metro_map/export/）
/mm reload                                    重新从磁盘载入数据
```

示例：

```
/mm line create 1号线 ff5050 市地铁集团
/mm station 火车站
（沿规划好的轨道走一段路……）
/mm station 人民广场        ← 此时自动生成 "火车站 → 人民广场" 线路段
```

## 3. 默认按键（可在 选项 → 按键绑定 → Metro Map 中修改）

| 按键 | 作用 |
|---|---|
| `G` | 在当前线路、当前位置**设站并自动拍照** |
| `H` | 撤销上一个站点（连同其后的线路段一并回滚） |
| `J` | 导出 JSON |
| `K` | 切换当前工作线路（循环） |

聊天栏支持 Fabric ClientCommands 语法前缀 `/`、`#`、`*`（见配置文件）。

## 4. 数据与文件位置

```
config/metro_map/
├── config.json                 # 模组配置（采样间隔、最小站距、路径点上限等）
├── data.json                   # 主数据文件（线路/站点/线路段），即时原子保存
├── photos/<线路ID>/<站ID>.png   # 每个站点的现场照片
└── export/metro_map_<时间戳>.json  # JSON 导出的线路图
```

### 导出 JSON 结构（示例节选）

```json
{
  "version": 1,
  "generated_at": "2026-10-02T03:20:00Z",
  "lines": [{
    "id": "line_1",
    "name": "1号线",
    "color": "#FF5050",
    "operator": "市地铁集团",
    "stations": [{
      "id": "line_1_station_1",
      "name": "火车站",
      "dimension": "minecraft:overworld",
      "pos": {"x": 100.5, "y": 64.0, "z": -200.2},
      "facing": 1.57,
      "photo": "photos/line_1/line_1_station_1.png",
      "sequence": 1
    }],
    "segments": [{
      "id": "line_1_segment_1",
      "from_station": "line_1_station_1",
      "to_station": "line_1_station_2",
      "path": [[100.5, 64.0, -200.2], [104.1, 64.0, -198.0]],
      "length": 214.66
    }]
  }]
}
```

## 5. 构建

需要 JDK 21（MC 1.21.8 要求 Java 21）：

```bash
./gradlew build
# 产物: build/libs/metro_map-1.0.0.jar
```

开发期可直接 `./gradlew runClient`。

## 6. 安装

1. 安装 Fabric Loader ≥ 0.17.2（官方安装器或 server installer）。
2. 将 `metro_map-1.0.0.jar` 与 [Fabric API](https://modrinth.com/mod/fabric-api) 放入 `mods/`。
   （本模组编译期依赖 Fabric API 以使用 KeyBinding/Command/HUD 事件；运行期若缺 Fabric API，除按键绑定外的核心功能仍可用。）

## 7. 关于 PaperMC 服务器（请务必阅读）

**Fabric 模组无法直接加载进 Paper/Purkinha/Spigot 服务端**——Paper 使用 Bukkit 插件体系，没有 Fabric Loader。
本模组按需求以 Fabric 实现，属于**纯客户端逻辑**（按键、拍照、路径记录都在客户端），要让它"适用于 PaperMC 服务器"，有三种可行部署方式：

1. **仅客户端安装（推荐，零改动服务端）**：玩家在连接 Paper 服务器时于自己的客户端安装本模组即可。线路图数据保存在玩家本机 `config/metro_map/`，导出的 JSON 可另行上传给服务器管理端。
2. **服务端也装 Fabric（换端或混合端）**：如果希望 `/mm` 指令在服务端生效（例如多人协作建图、统一数据），可将服务器改为 Fabric（Paper 兼容类服务端如 **Leaves / Magma-Fabric 类项目**可在保留 Bukkit API 的同时加载 Fabric 模组）。此时把本 jar 放入服务端 `mods/`，指令与数据即全端共享。
3. **Bukkit 桥接插件（可选扩展）**：由服务器管理员写一个极薄的 Paper 插件，读取本模组导出的 JSON 并在地图插件（如 BlueMap/WebMap）中渲染线路图。数据结构已按此设计（含 dimension、坐标、路径点序列）。

如果你的实际目标是方案 2/3，或需要「服务端权威存储 + 客户端只负责按键拍照」的架构调整，请告诉我，我可以继续改造。

## 8. 已知限制

- 路径记录发生在"上一站之后"，若中途跨维度移动，超出部分会被截断（上限 `max_path_points_per_segment`）。
- 拍照为屏幕截图（所见即所得），非独立渲染相机；建议正对车站位置再按键。
- 撤销仅支持逐级回滚最后一个站点。
