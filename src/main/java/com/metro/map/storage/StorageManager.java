package com.metro.map.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.metro.map.MetroMapMod;
import com.metro.map.model.Line;
import com.metro.map.model.MetroData;
import com.metro.map.model.Pos;
import com.metro.map.model.Station;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 存储管理器：
 * - 数据目录: <game>/metro_map/
 *   - lines.json      : 线路主数据（原子写 + fsync，即时保存）
 *   - sessions/       : 行走路径原始采样（断线/崩溃后可恢复未成段的路径）
 *   - screenshots/    : 站点无 HUD 高清照片
 *   - export/         : 导出的 JSON 文件
 * Pos 序列化为 [x, y, z] 数组。
 */
public class StorageManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path baseDir;

    public static Path getBaseDir() {
        if (baseDir == null) {
            baseDir = FabricLoader.getInstance().getGameDir().resolve("metro_map");
        }
        return baseDir;
    }

    public static Path getLinesFile() {
        return getBaseDir().resolve("lines.json");
    }

    public static Path getSessionsDir() {
        return getBaseDir().resolve("sessions");
    }

    public static Path getScreenshotsDir() {
        return getBaseDir().resolve("screenshots");
    }

    public static Path getExportDir() {
        return getBaseDir().resolve("export");
    }

    public static void ensureDirs() {
        try {
            Files.createDirectories(getBaseDir());
            Files.createDirectories(getSessionsDir());
            Files.createDirectories(getScreenshotsDir());
            Files.createDirectories(getExportDir());
        } catch (IOException e) {
            MetroMapMod.LOGGER.error("Failed to create metro_map dirs", e);
        }
    }

    // ---------------- JSON <-> model (Pos as [x,y,z]) ----------------

    private static JsonElement posToJson(Pos p) {
        JsonArray a = new JsonArray();
        a.add(p.x);
        a.add(p.y);
        a.add(p.z);
        return a;
    }

    private static Pos jsonToPos(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new Pos(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    /** 手工构建 Line 的 JSON，保证 pos 输出为 [x,y,z]。 */
    public static JsonObject buildLineJson(Line line) {
        JsonObject o = new JsonObject();
        o.addProperty("id", line.id);
        o.addProperty("name", line.name);
        o.addProperty("color", line.color);
        o.addProperty("operator", line.operator);
        o.addProperty("dimension", line.dimension);
        o.addProperty("createdAt", line.createdAt);

        JsonArray stations = new JsonArray();
        for (Station s : line.stations) {
            JsonObject so = new JsonObject();
            so.addProperty("id", s.id);
            so.addProperty("name", s.name);
            so.addProperty("dimension", s.dimension);
            so.add("pos", posToJson(s.pos));
            so.addProperty("yaw", s.yaw);
            so.addProperty("createdAt", s.createdAt);
            if (s.photo != null) so.addProperty("photo", s.photo);
            stations.add(so);
        }
        o.add("stations", stations);

        JsonArray segments = new JsonArray();
        line.segments.forEach(seg -> {
            JsonObject go = new JsonObject();
            go.addProperty("id", seg.id);
            go.addProperty("fromStation", seg.fromStation);
            go.addProperty("toStation", seg.toStation);
            JsonArray path = new JsonArray();
            for (Pos p : seg.path) {
                path.add(posToJson(p));
            }
            go.add("path", path);
            segments.add(go);
        });
        o.add("segments", segments);
        return o;
    }

    public static String toJson(MetroData data) {
        JsonArray lines = new JsonArray();
        data.lines.forEach(l -> lines.add(buildLineJson(l)));
        JsonObject root = new JsonObject();
        root.addProperty("version", data.version);
        root.addProperty("generatedAt", System.currentTimeMillis());
        root.addProperty("mod", MetroMapMod.MOD_ID);
        root.add("lines", lines);
        return GSON.toJson(root);
    }

    @SuppressWarnings("deprecation")
    public static MetroData fromJson(String json) {
        MetroData data = new MetroData();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("version")) data.version = root.get("version").getAsInt();
        JsonArray lines = root.getAsJsonArray("lines");
        if (lines == null) return data;
        for (JsonElement le : lines) {
            data.lines.add(readLine(le.getAsJsonObject()));
        }
        return data;
    }

    private static Line readLine(JsonObject o) {
        Line line = new Line();
        line.id = str(o, "id");
        line.name = str(o, "name");
        line.color = str(o, "color");
        line.operator = str(o, "operator");
        line.dimension = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
        line.createdAt = o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L;
        if (o.has("stations")) {
            for (JsonElement se : o.getAsJsonArray("stations")) {
                JsonObject so = se.getAsJsonObject();
                Station s = new Station();
                s.id = str(so, "id");
                s.name = str(so, "name");
                s.dimension = str(so, "dimension");
                s.pos = jsonToPos(so.get("pos"));
                s.yaw = so.has("yaw") ? so.get("yaw").getAsFloat() : 0f;
                s.createdAt = so.has("createdAt") ? so.get("createdAt").getAsLong() : 0L;
                s.photo = so.has("photo") ? so.get("photo").getAsString() : null;
                line.stations.add(s);
            }
        }
        if (o.has("segments")) {
            for (JsonElement ge : o.getAsJsonArray("segments")) {
                JsonObject go = ge.getAsJsonObject();
                List<Pos> path = new ArrayList<>();
                if (go.has("path")) {
                    for (JsonElement pe : go.getAsJsonArray("path")) {
                        path.add(jsonToPos(pe));
                    }
                }
                line.segments.add(new com.metro.map.model.LineSegment(
                        str(go, "id"), str(go, "fromStation"), str(go, "toStation"), path));
            }
        }
        return line;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : null;
    }

    // ---------------- 原子化即时保存 ----------------

    /**
     * 原子写：先写 .tmp 并 fsync，再 ATOMIC_MOVE 覆盖目标；随后 fsync 父目录。
     * 任何时刻进程被杀，旧文件保持完整。
     */
    public static synchronized void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                tmp, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
            ch.write(java.nio.ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
            ch.force(true);
        }
        // 若目标已存在，先备份（用于 lines.json 损坏时回退）
        Path bak = target.resolveSibling(target.getFileName() + ".bak");
        if (Files.exists(target)) {
            try {
                Files.copy(target, bak, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        syncDir(target.getParent());
    }

    private static void syncDir(Path dir) {
        if (dir == null) return;
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            ds.iterator().hasNext(); // 触发一次目录读取
        } catch (IOException ignored) {
        }
    }

    public static synchronized void save(MetroData data) {
        ensureDirs();
        try {
            atomicWrite(getLinesFile(), toJson(data));
        } catch (IOException e) {
            MetroMapMod.LOGGER.error("Failed to save lines.json", e);
        }
    }

    public static synchronized MetroData load() {
        ensureDirs();
        Path f = getLinesFile();
        if (!Files.exists(f)) return new MetroData();
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            return fromJson(json);
        } catch (Exception e) {
            MetroMapMod.LOGGER.error("Failed to read lines.json, trying backup", e);
            Path bak = f.resolveSibling("lines.json.bak");
            if (Files.exists(bak)) {
                try {
                    return fromJson(Files.readString(bak, StandardCharsets.UTF_8));
                } catch (Exception e2) {
                    MetroMapMod.LOGGER.error("Backup also unreadable", e2);
                }
            }
            return new MetroData();
        }
    }

    /** 导出整个数据集到 export/<name>.json，返回文件路径。 */
    public static Path export(MetroData data, String fileName) throws IOException {
        ensureDirs();
        if (!fileName.endsWith(".json")) fileName = fileName + ".json";
        Path out = getExportDir().resolve(fileName);
        atomicWrite(out, toJson(data));
        return out;
    }

    /** 导出单条线路。 */
    public static Path exportLine(Line line, String fileName) throws IOException {
        ensureDirs();
        if (!fileName.endsWith(".json")) fileName = fileName + ".json";
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("generatedAt", System.currentTimeMillis());
        root.addProperty("mod", MetroMapMod.MOD_ID);
        JsonArray arr = new JsonArray();
        arr.add(buildLineJson(line));
        root.add("lines", arr);
        Path out = getExportDir().resolve(fileName);
        atomicWrite(out, GSON.toJson(root));
        return out;
    }

    // ---------------- 会话（进行中路径）持久化 ----------------

    public static synchronized void saveSession(String lineId, String json) {
        try {
            atomicWrite(getSessionsDir().resolve(lineId + ".json"), json);
        } catch (IOException e) {
            MetroMapMod.LOGGER.error("Failed to save session " + lineId, e);
        }
    }

    public static synchronized String loadSession(String lineId) {
        Path f = getSessionsDir().resolve(lineId + ".json");
        if (!Files.exists(f)) return null;
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static synchronized void deleteSession(String lineId) {
        try {
            Files.deleteIfExists(getSessionsDir().resolve(lineId + ".json"));
        } catch (IOException ignored) {
        }
    }
}
