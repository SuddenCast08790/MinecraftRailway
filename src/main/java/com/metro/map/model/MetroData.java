package com.metro.map.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶层数据结构：所有线路。持久化到 .metro_map/lines.json（原子写、即时保存）。
 */
public class MetroData {
    public int version = 1;
    public List<Line> lines = new ArrayList<>();

    public Line findLine(String lineId) {
        for (Line l : lines) {
            if (l.id.equals(lineId)) return l;
        }
        return null;
    }

    /** 生成全局唯一线路 id：line-01, line-02 ... */
    public String nextLineId() {
        int n = lines.size() + 1;
        String id;
        do {
            id = String.format("line-%02d", n++);
        } while (findLine(id) != null);
        return id;
    }
}
