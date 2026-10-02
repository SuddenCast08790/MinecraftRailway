package com.metro.map.model;

/**
 * 世界坐标。存储为方块级浮点数（保留3位小数），JSON 中序列化为 [x, y, z]。
 */
public class Pos {
    public double x;
    public double y;
    public double z;

    public Pos() {
    }

    public Pos(double x, double y, double z) {
        this.x = round3(x);
        this.y = round3(y);
        this.z = round3(z);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public double distanceTo(Pos other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
