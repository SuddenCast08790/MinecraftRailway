import com.metro.map.model.*;
import com.metro.map.session.WalkSession;
import com.metro.map.storage.StorageManager;
import java.nio.file.*;

public class SmokeTest {
    public static void main(String[] args) throws Exception {
        // 采样 + 会话序列化往返
        WalkSession ws = new WalkSession("line-01", "st-001");
        for (int i = 0; i < 30; i++) ws.addPoint(100 + i, 64, 200);
        String sj = ws.toJson();
        WalkSession ws2 = WalkSession.fromJson(sj);
        assert ws2.getPoints().size() == ws.getPoints().size();

        // 数据模型 + 原子保存 + 读取往返
        MetroData data = new MetroData();
        Line line = new Line(data.nextLineId(), "1号线", "#E4002B", "MetroOperator");
        Station a = new Station(line.nextStationId(), "始发站", "minecraft:overworld", new Pos(100, 64, 200), 90f);
        Station b = new Station(line.nextStationId(), "终点站", "minecraft:overworld", new Pos(130, 64, 200), 90f);
        line.stations.add(a); line.stations.add(b);
        line.segments.add(new LineSegment(line.nextSegmentId(), a.id, b.id, ws.toSegment(a.pos, b.pos)));
        data.lines.add(line);

        StorageManager.save(data);
        MetroData back = StorageManager.load();
        if (back.lines.size() != 1) throw new RuntimeException("save/load roundtrip failed");
        Path exported = StorageManager.export(back, "smoke");
        System.out.println("Exported: " + exported.toAbsolutePath());
        System.out.println(Files.readString(exported).substring(0, 400));
        System.out.println("SMOKE TEST OK");
    }
}
