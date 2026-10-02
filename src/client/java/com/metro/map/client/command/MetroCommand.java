package com.metro.map.client.command;

import com.metro.map.client.MetroMapClient;
import com.metro.map.client.autowalk.AutoWalker;
import com.metro.map.model.Station;
import com.metro.map.client.StationFlow;
import com.metro.map.client.gui.LineConfigScreen;
import com.metro.map.client.session.SessionManager;
import com.metro.map.model.Line;
import com.metro.map.model.MetroData;
import com.metro.map.storage.StorageManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * 客户端命令 /metro ...（与 GUI/按键双通道，命令面向脚本化批量操作）。
 *
 * /metro gui                                  打开线路设置 GUI
 * /metro new <name> <#color> <operator...>    新建并激活线路
 * /metro activate <lineId|名称>               切换激活线路
 * /metro station [名称]                       在当前位置设站（无 GUI，自动命名兜底）
 * /metro cancel                               取消当前线路段路径 / 停用线路
 * /metro export [文件名]                      导出全部 JSON
 * /metro export line <id|名称> [文件名]        导出单条线路 JSON
 * /metro list                                 列出所有线路
 * /metro info [id|名称]                       线路详情
 * /metro simplify <tolerance>                 Douglas-Peucker 简化所有路径(预览,不落盘)
 */
public class MetroCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("metro")
                .then(ClientCommandManager.literal("gui")
                        .executes(c -> {
                            c.getSource().getClient().setScreen(new LineConfigScreen());
                            return 1;
                        }))
                .then(ClientCommandManager.literal("new")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .then(ClientCommandManager.argument("color", StringArgumentType.string())
                                        .then(ClientCommandManager.argument("operator", StringArgumentType.greedyString())
                                                .executes(MetroCommand::newLine)))))
                .then(ClientCommandManager.literal("activate")
                        .then(ClientCommandManager.argument("line", StringArgumentType.greedyString())
                                .executes(MetroCommand::activate)))
                .then(ClientCommandManager.literal("station")
                        .executes(c -> station(c, null))
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(c -> station(c, StringArgumentType.getString(c, "name")))))
                .then(ClientCommandManager.literal("cancel")
                        .executes(c -> {
                            StationFlow.onCancelKey(c.getSource().getClient());
                            return 1;
                        }))
                .then(ClientCommandManager.literal("list")
                        .executes(MetroCommand::list))
                .then(ClientCommandManager.literal("info")
                        .then(ClientCommandManager.argument("line", StringArgumentType.greedyString())
                                .executes(MetroCommand::info)))
                .then(ClientCommandManager.literal("export")
                        .executes(c -> exportAll(c, "metro_map_export"))
                        .then(ClientCommandManager.argument("file", StringArgumentType.word())
                                .executes(c -> exportAll(c, StringArgumentType.getString(c, "file"))))
                        .then(ClientCommandManager.literal("line")
                                .then(ClientCommandManager.argument("line", StringArgumentType.string())
                                        .executes(c -> exportLine(c, StringArgumentType.getString(c, "line"), null))
                                        .then(ClientCommandManager.argument("file", StringArgumentType.word())
                                                .executes(c -> exportLine(c,
                                                        StringArgumentType.getString(c, "line"),
                                                        StringArgumentType.getString(c, "file")))))))
                .then(ClientCommandManager.literal("auto")
                        .then(ClientCommandManager.literal("start")
                                .then(ClientCommandManager.argument("station", StringArgumentType.greedyString())
                                        .executes(MetroCommand::autoToStation))
                                .then(ClientCommandManager.literal("pos")
                                        .then(ClientCommandManager.argument("x", FloatArgumentType.floatArg(-3e7f, 3e7f))
                                                .then(ClientCommandManager.argument("y", FloatArgumentType.floatArg(-2e9f, 2e9f))
                                                        .then(ClientCommandManager.argument("z", FloatArgumentType.floatArg(-3e7f, 3e7f))
                                                                .executes(MetroCommand::autoToCoord))))))
                        .then(ClientCommandManager.literal("stop")
                                .executes(c -> { AutoWalker.stop(c.getSource().getClient(), "命令请求停止"); return 1; }))
                        .then(ClientCommandManager.literal("status")
                                .executes(MetroCommand::autoStatus)))
                .then(ClientCommandManager.literal("simplify")
                        .then(ClientCommandManager.argument("tolerance", FloatArgumentType.floatArg(0f, 64f))
                                .executes(MetroCommand::simplifyPreview)))
        );
    }

    // ---------------- handlers ----------------

    private static int newLine(CommandContext<FabricClientCommandSource> c) {
        String name = StringArgumentType.getString(c, "name");
        String color = StringArgumentType.getString(c, "color");
        String operator = StringArgumentType.getString(c, "operator");
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            c.getSource().sendError(Text.literal("颜色必须为 #RRGGBB 格式"));
            return 0;
        }
        var client = c.getSource().getClient();
        Line line = new Line(SessionManager.data.nextLineId(), name, color, operator);
        if (client.world != null) line.dimension = client.world.getRegistryKey().getValue().toString();
        SessionManager.data.lines.add(line);
        SessionManager.setActiveLine(line.id);
        SessionManager.saveData();
        c.getSource().sendFeedback(Text.literal("§a[Metro] §r已创建并激活线路 " + line.id + " (" + name + ")"));
        return 1;
    }

    private static int activate(CommandContext<FabricClientCommandSource> c) {
        Line line = SessionManager.findLine(StringArgumentType.getString(c, "line"));
        if (line == null) {
            c.getSource().sendError(Text.literal("找不到线路"));
            return 0;
        }
        SessionManager.setActiveLine(line.id);
        c.getSource().sendFeedback(Text.literal("§a[Metro] §r已激活线路 " + line.name + " (" + line.id + ")"));
        return 1;
    }

    private static int station(CommandContext<FabricClientCommandSource> c, String name) {
        var client = c.getSource().getClient();
        Line line = SessionManager.getActiveLine();
        if (line == null) {
            c.getSource().sendError(Text.literal("先 /metro new ... 或 /metro activate ... 激活一条线路"));
            return 0;
        }
        String finalName = (name == null || name.isBlank())
                ? "站-" + (line.stations.size() + 1) : name.trim();
        // 直接设站（不弹 GUI），复用主流程逻辑
        invokeStation(client, line, finalName);
        return 1;
    }

    /** 通过反射避免对包私有方法的依赖问题——这里直接调用公共入口。 */
    private static void invokeStation(net.minecraft.client.MinecraftClient client, Line line, String name) {
        StationFlow.placeStationByCommand(client, line, name);
    }

    private static int list(CommandContext<FabricClientCommandSource> c) {
        MetroData data = SessionManager.data;
        if (data.lines.isEmpty()) {
            c.getSource().sendFeedback(Text.literal("§e[Metro] §r还没有线路。/metro new <名称> <#颜色> <运营方>"));
            return 0;
        }
        for (Line l : data.lines) {
            String mark = l.id.equals(SessionManager.getActiveLineId()) ? "§7*§r " : "  ";
            c.getSource().sendFeedback(Text.literal(mark + l.id + " · " + l.color + " · "
                    + l.name + " §7(" + l.stations.size() + "站/" + l.segments.size() + "段, " + l.operator + ")"));
        }
        return data.lines.size();
    }

    private static int info(CommandContext<FabricClientCommandSource> c) {
        Line line = SessionManager.findLine(StringArgumentType.getString(c, "line"));
        if (line == null) {
            c.getSource().sendError(Text.literal("找不到线路"));
            return 0;
        }
        c.getSource().sendFeedback(Text.literal(line.id + " | " + line.name + " | " + line.color
                + " | " + line.operator + " | " + line.dimension));
        line.stations.forEach(s -> c.getSource().sendFeedback(Text.literal(
                "  " + s.id + " " + s.name + " @ [" + s.pos.x + ", " + s.pos.y + ", " + s.pos.z + "]"
                        + (s.photo != null ? " 📷" + s.photo : ""))));
        line.segments.forEach(g -> c.getSource().sendFeedback(Text.literal(
                "  " + g.id + ": " + g.fromStation + "→" + g.toStation + " (" + g.path.size() + " pts)")));
        return 1;
    }

    private static int exportAll(CommandContext<FabricClientCommandSource> c, String file) {
        try {
            java.nio.file.Path p = StorageManager.export(SessionManager.data, sanitize(file));
            c.getSource().sendFeedback(Text.literal("§a[Metro] §r已导出: " + p.toAbsolutePath()));
            return 1;
        } catch (Exception e) {
            c.getSource().sendError(Text.literal("导出失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int exportLine(CommandContext<FabricClientCommandSource> c, String lineRef, String file) {
        Line line = SessionManager.findLine(lineRef);
        if (line == null) {
            c.getSource().sendError(Text.literal("找不到线路"));
            return 0;
        }
        try {
            String fname = file != null ? sanitize(file) : sanitize(line.id + "_" + line.name);
            java.nio.file.Path p = StorageManager.exportLine(line, fname);
            c.getSource().sendFeedback(Text.literal("§a[Metro] §r已导出线路: " + p.toAbsolutePath()));
            return 1;
        } catch (Exception e) {
            c.getSource().sendError(Text.literal("导出失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int simplifyPreview(CommandContext<FabricClientCommandSource> c) {
        float tol = FloatArgumentType.getFloat(c, "tolerance");
        int before = 0, after = 0;
        for (Line l : SessionManager.data.lines) {
            for (var g : l.segments) {
                before += g.path.size();
                after += com.metro.map.session.WalkSession.simplify(g.path, tol).size();
            }
        }
        c.getSource().sendFeedback(Text.literal(
                "§b[Metro] §r容差 " + tol + " 格: " + before + " → " + after + " 点（预览未修改数据；导出时自动应用配置）"));
        return 1;
    }

    private static String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    // ---------------- auto walk ----------------

    private static int autoToStation(CommandContext<FabricClientCommandSource> c) {
        var client = c.getSource().getClient();
        if (AutoWalker.isWalking()) { c.getSource().sendError(Text.literal("自动行走已在进行中，先 /metro auto stop")); return 0; }
        String ref = StringArgumentType.getString(c, "station");
        Station st = null; String label = null;
        for (Line l : SessionManager.data.lines) {
            for (Station s2 : l.stations) {
                if (s2.id.equalsIgnoreCase(ref) || s2.name.equalsIgnoreCase(ref)) { st = s2; label = l.name + "·" + s2.name; break; }
            }
            if (st != null) break;
        }
        if (st == null) { c.getSource().sendError(Text.literal("找不到站点: " + ref)); return 0; }
        AutoWalker.start(client, st.pos.x, st.pos.y, st.pos.z, label);
        return 1;
    }

    private static int autoToCoord(CommandContext<FabricClientCommandSource> c) {
        var client = c.getSource().getClient();
        if (AutoWalker.isWalking()) { c.getSource().sendError(Text.literal("自动行走已在进行中，先 /metro auto stop")); return 0; }
        float x = FloatArgumentType.getFloat(c, "x");
        float y = FloatArgumentType.getFloat(c, "y");
        float z = FloatArgumentType.getFloat(c, "z");
        AutoWalker.start(client, x, y, z, String.format("(%.0f, %.0f, %.0f)", x, y, z));
        return 1;
    }

    private static int autoStatus(CommandContext<FabricClientCommandSource> c) {
        if (AutoWalker.isWalking())
            c.getSource().sendFeedback(Text.literal("§a[Auto] §r行走中 → " + AutoWalker.targetLabel()));
        else
            c.getSource().sendFeedback(Text.literal("§e[Auto] §r空闲。/metro auto start <站名|坐标>"));
        return 1;
    }
}
