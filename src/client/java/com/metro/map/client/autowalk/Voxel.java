package com.metro.map.client.autowalk;

/** A* 节点键：方块坐标（脚部位置）。 */
public record Voxel(int x, int y, int z) {
    public double distTo(Voxel o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
