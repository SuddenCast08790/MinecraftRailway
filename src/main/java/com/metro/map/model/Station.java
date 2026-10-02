package com.metro.map.model;

/**
 * 站点。id 在所属线路内唯一；photo 为无 HUD 高清截图的相对文件名（screenshots/xxx.png）。
 */
public class Station {
    public String id;
    public String name;
    public String dimension; // e.g. "minecraft:overworld"
    public Pos pos;
    public float yaw;   // 拍照时玩家朝向，供后续渲染参考
    public long createdAt; // epoch millis
    public String photo;   // 相对 .metro_map/screenshots/ 的文件名，可为 null

    public Station() {
    }

    public Station(String id, String name, String dimension, Pos pos, float yaw) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
        this.createdAt = System.currentTimeMillis();
    }
}
