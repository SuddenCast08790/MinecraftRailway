package com.metro.map.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨道交通线路。至少包括 [id, name, color, operator]。
 * stations / segments 构成完整线路；segments 按建设顺序排列。
 */
public class Line {
    public String id;
    public String name;
    public String color;     // "#RRGGBB"
    public String operator;  // 运营方
    public String dimension = "minecraft:overworld";
    public List<Station> stations = new ArrayList<>();
    public List<LineSegment> segments = new ArrayList<>();
    public long createdAt;

    public Line() {
    }

    public Line(String id, String name, String color, String operator) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.operator = operator;
        this.createdAt = System.currentTimeMillis();
    }

    public Station findStation(String stationId) {
        for (Station s : stations) {
            if (s.id.equals(stationId)) return s;
        }
        return null;
    }

    /** 生成线路内唯一的站点 id：st-001, st-002 ... */
    public String nextStationId() {
        int n = stations.size() + 1;
        String id;
        do {
            id = String.format("st-%03d", n++);
        } while (findStation(id) != null);
        return id;
    }

    /** 生成线路内唯一的线路段 id：seg-001 ... */
    public String nextSegmentId() {
        int n = segments.size() + 1;
        String id;
        boolean exists;
        do {
            id = String.format("seg-%03d", n++);
            final String candidate = id;
            exists = segments.stream().anyMatch(sg -> sg.id.equals(candidate));
        } while (exists);
        return id;
    }
}
