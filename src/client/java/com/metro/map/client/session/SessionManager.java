package com.metro.map.client.session;

import com.metro.map.MetroMapMod;
import com.metro.map.model.Line;
import com.metro.map.model.MetroData;
import com.metro.map.model.Station;
import com.metro.map.session.WalkSession;
import com.metro.map.storage.StorageManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局状态中枢（仅客户端）：
 *  - data       : 所有线路（内存镜像，任何修改立即原子落盘）
 *  - activeLine : 当前正在建设的线路 id（null = 未激活，不采样）
 *  - sessions   : 每条线进行中的行走采样会话（每秒即时保存；崩溃/退出可恢复）
 */
public class SessionManager {
    public static MetroData data = new MetroData();
    private static final Map<String, WalkSession> sessions = new HashMap<>();
    private static String activeLineId = null;

    public static void init() {
        StorageManager.ensureDirs();
        data = StorageManager.load();
        // 恢复上次未结算的行走会话（玩家随时关游戏的断点续画）
        for (Line line : data.lines) {
            String json = StorageManager.loadSession(line.id);
            if (json != null) {
                try {
                    WalkSession ws = WalkSession.fromJson(json);
                    sessions.put(line.id, ws);
                    MetroMapMod.LOGGER.info("Restored walk session for {} ({} points)",
                            line.id, ws.getPoints().size());
                } catch (Exception e) {
                    StorageManager.deleteSession(line.id);
                }
            }
        }
    }

    // ---------------- 激活线路 ----------------

    public static String getActiveLineId() { return activeLineId; }

    public static Line getActiveLine() {
        return activeLineId == null ? null : data.findLine(activeLineId);
    }

    public static void setActiveLine(String lineId) { activeLineId = lineId; }

    public static void deactivate() { activeLineId = null; }

    /** 按 id 或名称查找线路。 */
    public static Line findLine(String nameOrId) {
        if (nameOrId == null) return null;
        Line l = data.findLine(nameOrId);
        if (l != null) return l;
        for (Line x : data.lines) if (x.name.equalsIgnoreCase(nameOrId)) return x;
        return null;
    }

    // ---------------- 行走会话 ----------------

    public static WalkSession getSession(String lineId) { return sessions.get(lineId); }

    public static boolean hasSession(String lineId) { return sessions.containsKey(lineId); }

    public static void startSession(String lineId, String fromStationId) {
        sessions.put(lineId, new WalkSession(lineId, fromStationId));
        saveSessionNow(lineId);
    }

    public static void cancelSession(String lineId) {
        sessions.remove(lineId);
        StorageManager.deleteSession(lineId);
    }

    public static void saveSessionNow(String lineId) {
        WalkSession ws = sessions.get(lineId);
        if (ws != null) StorageManager.saveSession(lineId, ws.toJson());
    }

    // ---------------- 数据保存（即时） ----------------

    public static void saveData() {
        StorageManager.save(data);
    }

    /** 强制全量落盘（断线 / 关闭游戏时调用）。 */
    public static void flushAll() {
        for (String id : new HashMap<>(sessions).keySet()) saveSessionNow(id);
        StorageManager.save(data);
    }

    // ---------------- tick 循环 ----------------

    /** 每客户端 tick 末尾调用：对活动线路做行走路径采样 + 每秒即时保存。 */
    public static void onEndTick(MinecraftClient client) {
        if (activeLineId == null || client.player == null || client.world == null) return;
        ClientPlayerEntity p = client.player;
        WalkSession ws = sessions.get(activeLineId);
        if (ws == null) {
            Line line = getActiveLine();
            List<Station> sts = line != null ? line.stations : Collections.emptyList();
            String from = sts.isEmpty() ? "" : sts.get(sts.size() - 1).id;
            ws = new WalkSession(activeLineId, from);
            sessions.put(activeLineId, ws);
        }
        ws.addPoint(p.getX(), p.getY(), p.getZ());
        if (ws.tickSaveTimer()) saveSessionNow(activeLineId);
    }
}
