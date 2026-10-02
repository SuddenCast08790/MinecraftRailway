package com.metro.map.model;

import java.util.List;

/**
 * 线路段：连接 fromStation → toStation，path 为按行走顺序采样的路径点列表。
 */
public class LineSegment {
    public String id;
    public String fromStation; // Station.id
    public String toStation;   // Station.id
    public List<Pos> path;     // 采样后的路径点（含首尾）

    public LineSegment() {
    }

    public LineSegment(String id, String fromStation, String toStation, List<Pos> path) {
        this.id = id;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.path = path;
    }
}
