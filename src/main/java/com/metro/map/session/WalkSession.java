package com.metro.map.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.metro.map.MetroMapMod;
import com.metro.map.model.LineSegment;
import com.metro.map.model.Pos;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径会话：记录两次设站之间的行走采样点。
 * 纯逻辑（不依赖 Minecraft 类），便于离线编译与单元测试。
 *
 * 采样规则（tick 回调调用 addPoint）：
 *  - 距上一点 >= minSpacing(默认1.0格) 才记录，避免数据爆炸；
 *  - 疑似传送造成的大跳跃会打日志但仍保留真实轨迹；
 *  - 每 accumulateTicks(默认20 tick = 1s) 触发一次外部即时保存。
 */
public class WalkSession {
    public static final double MIN_SPACING = 1.0;
    public static final double MAX_SPACING = 8.0;
    public static final int ACCUMULATE_TICKS = 20;

    private final String lineId;
    private final String fromStationId; // 上一个站点（本段起点），首个站点时为空串
    private final List<Pos> points = new ArrayList<>();
    private int ticksSinceSave = 0;
    private boolean dirty = false;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public WalkSession(String lineId, String fromStationId) {
        this.lineId = lineId;
        this.fromStationId = fromStationId == null ? "" : fromStationId;
    }

    public String getLineId() {
        return lineId;
    }

    public String getFromStationId() {
        return fromStationId;
    }

    public List<Pos> getPoints() {
        return points;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * 添加一个候选采样点。
     * @return true 表示该点被采纳
     */
    public boolean addPoint(double x, double y, double z) {
        Pos p = new Pos(x, y, z);
        if (!points.isEmpty()) {
            double d = points.get(points.size() - 1).distanceTo(p);
            if (d < MIN_SPACING) return false;
            if (d > MAX_SPACING * 4) {
                MetroMapMod.LOGGER.warn("Large jump in walk path ({} blocks), point kept", String.format("%.1f", d));
            }
        }
        points.add(p);
        dirty = true;
        return true;
    }

    /** tick 计数；返回 true 表示应当做一次持久化保存。 */
    public boolean tickSaveTimer() {
        if (!dirty) return false;
        ticksSinceSave++;
        if (ticksSinceSave >= ACCUMULATE_TICKS) {
            ticksSinceSave = 0;
            return true;
        }
        return false;
    }

    public void markSaved() {
        dirty = false;
    }

    // ---------------- 序列化（sessions/<lineId>.json） ----------------

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("lineId", lineId);
        o.addProperty("fromStation", fromStationId);
        JsonArray arr = new JsonArray();
        for (Pos p : points) {
            JsonArray a = new JsonArray();
            a.add(p.x);
            a.add(p.y);
            a.add(p.z);
            arr.add(a);
        }
        o.add("points", arr);
        return GSON.toJson(o);
    }

    public static WalkSession fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        WalkSession s = new WalkSession(
                o.get("lineId").getAsString(),
                o.has("fromStation") ? o.get("fromStation").getAsString() : "");
        if (o.has("points")) {
            for (JsonElement pe : o.getAsJsonArray("points")) {
                JsonArray a = pe.getAsJsonArray();
                s.points.add(new Pos(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
            }
        }
        s.dirty = false;
        return s;
    }

    /** 结算为线路段：path = [起点站pos] + 采样点 + [终点站pos]（去重相邻过近点）。 */
    public LineSegment toSegment(String segmentId, Pos fromPos, Pos toPos, String toStationId) {
        List<Pos> path = new ArrayList<>();
        appendPath(path, fromPos);
        for (Pos p : points) {
            appendPath(path, p);
        }
        appendPath(path, toPos);
        return new LineSegment(segmentId, fromStationId, toStationId, path);
    }

    private static void appendPath(List<Pos> path, Pos p) {
        if (p == null) return;
        if (!path.isEmpty() && path.get(path.size() - 1).distanceTo(p) < 0.001) return;
        path.add(p);
    }

    /** Douglas-Peucker 简化（导出前可选压缩路径精度）。tolerance<=0 时原样返回。 */
    public static List<Pos> simplify(List<Pos> input, double tolerance) {
        if (tolerance <= 0 || input.size() < 3) return new ArrayList<>(input);
        return dp(input, 0, input.size() - 1, tolerance);
    }

    private static List<Pos> dp(List<Pos> pts, int first, int last, double tol) {
        double maxDist = -1;
        int index = -1;
        Pos a = pts.get(first);
        Pos b = pts.get(last);
        for (int i = first + 1; i < last; i++) {
            double d = pointLineDistance(pts.get(i), a, b);
            if (d > maxDist) {
                maxDist = d;
                index = i;
            }
        }
        List<Pos> out = new ArrayList<>();
        if (maxDist > tol && index > 0) {
            out.addAll(dp(pts, first, index, tol));
            out.remove(out.size() - 1);
            out.addAll(dp(pts, index, last, tol));
        } else {
            out.add(a);
            out.add(b);
        }
        return out;
    }

    private static double pointLineDistance(Pos p, Pos a, Pos b) {
        double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        double apx = p.x - a.x, apy = p.y - a.y, apz = p.z - a.z;
        double cx = aby * apz - abz * apy;
        double cy = abz * apx - abx * apz;
        double cz = abx * apy - aby * apx;
        double cross = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double abLen = Math.sqrt(abx * abx + aby * aby + abz * abz);
        if (abLen < 1e-9) return p.distanceTo(a);
        return cross / abLen;
    }
}
