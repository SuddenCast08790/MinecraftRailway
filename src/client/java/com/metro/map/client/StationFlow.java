package com.metro.map.client;

import com.metro.map.MetroMapMod;
import com.metro.map.client.gui.NameDialog;
import com.metro.map.client.screenshot.NoHudScreenshot;
import com.metro.map.client.session.SessionManager;
import com.metro.map.model.Line;
import com.metro.map.model.LineSegment;
import com.metro.map.model.Pos;
import com.metro.map.model.Station;
import com.metro.map.session.WalkSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * 绑键设站主流程：
 *   第 1 次按键 → 弹命名 GUI（默认自动编号）→ 创建站点 + 无HUD高清拍照 + 开始记录行走路径
 *   之后每走 ≥1 格采样一个路径点（每秒即时落盘）
 *   第 2 次按键 → 新站点 + 拍照 + 用刚才的路径结算生成线路段（seg = [起点] + 采样 + [终点]）
 *   继续按键 → 以上一段终点为起点，循环“走路→设站→成段”
 */
public class StationFlow {

    public static void onAddStationKey(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Line line = SessionManager.getActiveLine();
        if (line == null) {
            msg(client, "§c[Metro] §r尚未激活线路。使用 /metro new <名称> <颜色> <运营方> 或按绑定键前先建线。");
            return;
        }
        String defaultName = "站-" + (line.stations.size() + 1);
        client.setScreen(new NameDialog("设置站点名称（留空则自动编号）", defaultName,
                name -> placeStation(client, line, name)));
    }

    private static void placeStation(MinecraftClient client, Line line, String stationName) {
        ClientPlayerEntity p = client.player;
        if (p == null) return;
        Pos pos = new Pos(p.getX(), p.getY(), p.getZ());
        float yaw = p.getYaw();
        String dim = client.world.getRegistryKey().getValue().toString();

        Station st = new Station(line.nextStationId(), stationName, dim, pos, yaw);
        line.stations.add(st);

        // 与上一个站点之间：若存在行走会话 → 结算线路段
        WalkSession ws = SessionManager.getSession(line.id);
        boolean segmentCreated = false;
        if (ws != null && !ws.getFromStationId().isEmpty()) {
            Station from = line.findStation(ws.getFromStationId());
            if (from != null) {
                LineSegment seg = ws.toSegment(line.nextSegmentId(), from.pos, pos, st.id);
                line.segments.add(seg);
                segmentCreated = true;
                msg(client, String.format("§a[Metro] §r线路段 %s: %s → %s（%d 个路径点）",
                        seg.id, from.name, st.name, seg.path.size()));
            }
            SessionManager.cancelSession(line.id); // 删除已结算的会话文件
        }

        // 即时保存（原子写）——先存站点/线段，照片稍后异步回填再存一次
        SessionManager.saveData();

        // 开始新的行走会话（从本站出发）
        SessionManager.startSession(line.id, st.id);

        // 无 HUD 高清拍照；完成后回填 photo 字段并再次即时保存
        NoHudScreenshot.capture(client, fileName -> {
            if (fileName != null) {
                st.photo = fileName;
                SessionManager.saveData();
                msg(client, "§a[Metro] §r站点 [" + st.name + "] 已保存，照片: screenshots/" + fileName);
            } else {
                msg(client, "§e[Metro] §r站点 [" + st.name + "] 已保存，但拍照失败");
            }
            if (!segmentCreated && line.stations.size() == 1) {
                msg(client, "§b[Metro] §r第一个站点完成，请沿规划路线行走，到达位置后再次按键设第二站。");
            }
        });
    }

    /** /metro station 命令入口：在当前玩家位置直接设站（不弹 GUI）。 */
    public static void placeStationByCommand(MinecraftClient client, Line line, String stationName) {
        placeStation(client, line, stationName);
    }

    /** H 键 / /metro cancel：放弃当前进行中的线路段路径。 */
    public static void onCancelKey(MinecraftClient client) {
        Line line = SessionManager.getActiveLine();
        if (line == null) {
            msg(client, "§e[Metro] §r没有激活的线路。");
            return;
        }
        if (SessionManager.hasSession(line.id)) {
            SessionManager.cancelSession(line.id);
            // 以最新站点重新开启会话
            String last = line.stations.isEmpty() ? "" : line.stations.get(line.stations.size() - 1).id;
            SessionManager.startSession(line.id, last);
            msg(client, "§e[Metro] §r已取消当前线路段路径，重新开始记录。");
        } else {
            SessionManager.deactivate();
            msg(client, "§e[Metro] §r已停用线路 " + line.name + "（数据均已保存）。");
        }
    }

    static void msg(MinecraftClient client, String text) {
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(text));
        }
        MetroMapMod.LOGGER.info(text.replaceAll("§.", ""));
    }
}
