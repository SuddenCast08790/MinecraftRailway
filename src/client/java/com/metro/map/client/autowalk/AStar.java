package com.metro.map.client.autowalk;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * 简易 A*（平地版）：
 *  - 状态 = 玩家脚部所在方块格；
 *  - 移动 = 4 向水平 ±1，允许上 1 格台阶 / 下最多 3 格落差；
 *  - 可通行判定：脚下实心、站人处两格空气（或可穿过方块如草/花）、头顶不卡。
 * 纯客户端世界读取，不发包，服务器无感知。
 */
public class AStar {

    private static final int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}};

    /** 该格是否可站立。 */
    public static boolean canStand(World w, int x, int y, int z) {
        BlockState below = w.getBlockState(new BlockPos(x, y - 1, z));
        if (!below.isSolid()) return false;                       // 脚下需实心
        for (int dy = 0; dy <= 1; dy++) {
            BlockState s = w.getBlockState(new BlockPos(x, y + dy, z));
            if (s.isSolid()) return false;                        // 身体两格需空
        }
        return true;
    }

    /** 从 start 到 goal 的方块路径；失败返回 null。 */
    public static List<Voxel> find(World w, Voxel start, Voxel goal) {
        if (start.equals(goal)) return List.of(start);

        Map<Voxel, Double> g = new HashMap<>();
        Map<Voxel, Voxel> parent = new HashMap<>();
        PriorityQueue<Voxel> open = new PriorityQueue<>(
                Comparator.comparingDouble(v -> g.getOrDefault(v, Double.MAX_VALUE) + v.distTo(goal)));
        g.put(start, 0.0);
        open.add(start);
        int expansions = 0;

        while (!open.isEmpty() && expansions++ < NavConfig.MAX_EXPANSIONS) {
            Voxel cur = open.poll();
            if (cur.equals(goal)) return reconstruct(parent, cur);
            double cg = g.get(cur);

            for (int[] d : DIRS) {
                for (int dy = -3; dy <= 1; dy++) {   // 下最多3格（自由落体），上最多1格（台阶）
                    int nx = cur.x() + d[0], nz = cur.z() + d[1];
                    Voxel nb = new Voxel(nx, cur.y() + dy, nz);
                    if (!canStand(w, nb.x(), nb.y(), nb.z())) continue;
                    if (dy < 0) { // 下落路径中间不能穿过实心方块
                        boolean blocked = false;
                        for (int yy = cur.y() - 1; yy >= nb.y(); yy--) {
                            if (w.getBlockState(new BlockPos(nx, yy, nz)).isSolid()) { blocked = true; break; }
                        }
                        if (blocked) continue;
                    }
                    double ng = cg + Math.sqrt(1 + (double) dy * dy);
                    if (ng < g.getOrDefault(nb, Double.MAX_VALUE)) {
                        g.put(nb, ng);
                        parent.put(nb, cur);
                        open.add(nb);
                    }
                }
            }
        }
        return null; // 未找到 / 超出展开上限
    }

    private static List<Voxel> reconstruct(Map<Voxel, Voxel> parent, Voxel goal) {
        LinkedList<Voxel> path = new LinkedList<>();
        for (Voxel c = goal; c != null; c = parent.get(c)) path.addFirst(c);
        return path;
    }
}
