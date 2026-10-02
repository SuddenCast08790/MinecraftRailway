package com.metro.map.client.autowalk;

import com.metro.map.MetroMapMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 自动寻路控制器（方案A：规则驱动，无大模型）。
 *  - /metro auto start <目标站|坐标>   设定终点，A*规划，AI接管移动
 *  - J键 / /metro auto stop            玩家请求停止
 *  - AI判定到达：水平距离≤2格 且 速度≈0 持续3秒 → 自动停止并播报
 * 每 tick：平滑转向 waypoint → 前进；台阶跳、卡住检测、前方视觉预警辅助。
 */
public class AutoWalker {

    public enum State { IDLE, WALKING }

    private static State state = State.IDLE;
    private static double tx, ty, tz;          // 终点
    private static String targetLabel = "";
    private static List<Voxel> path;           // A* 方块路径
    private static int wpIdx;                  // 当前 waypoint 索引
    private static int tickCounter = 0;
    private static int stillTicks = 0;         // 距终点且静止的连续 tick
    private static int stuckTicks = 0;         // 无法前进的连续 tick
    private static double lastProgressDist = Double.MAX_VALUE;

    public static State state() { return state; }
    public static boolean isWalking() { return state == State.WALKING; }
    public static String targetLabel() { return targetLabel; }

    /** 启动。终点为世界坐标。 */
    public static void start(MinecraftClient client, double x, double y, double z, String label) {
        if (client.player == null || client.world == null) return;
        ClientPlayerEntity p = client.player;
        Voxel from = new Voxel(floor(p.getX()), floor(p.getY()), floor(p.getZ()));
        Voxel to = new Voxel(floor(x), floor(y), floor(z));
        // 目标点若不可站立（如站在站牌旁），就近搜索可站立格
        Voxel goal = findNearStandable(client, to);
        if (goal == null) { msg(client, "§c[Auto] §r目标附近没有可站立的地面，请靠近目标再试。"); return; }
        path = AStar.find(client.world, from, goal);
        if (path == null) { msg(client, "§c[Auto] §r未找到可行路径（超出搜索上限或完全阻断）。平地版仅支持连续地面。"); return; }
        tx = x; ty = y; tz = z; targetLabel = label;
        wpIdx = 0; tickCounter = 0; stillTicks = 0; stuckTicks = 0;
        lastProgressDist = distXZ(p.getX(), p.getZ(), goal.x() + 0.5, goal.z() + 0.5);
        state = State.WALKING;
        msg(client, String.format("§a[Auto] §r开始自动行走 → %s（路径 %d 格）。按 J 或 /metro auto stop 停止。", label, path.size()));
    }

    /** 玩家请求停止（J键 / 命令）。 */
    public static void stop(MinecraftClient client, String reason) {
        if (state != State.WALKING) { msg(client, "§e[Auto] §r当前没有进行中的自动行走。"); return; }
        state = State.IDLE;
        path = null; wpIdx = 0;
        releaseKeys(client);
        msg(client, "§e[Auto] §r已停止自动行走：" + reason);
    }

    /** 每客户端 tick 调用（END_CLIENT_TICK，在采样之后）。 */
    public static void onTick(MinecraftClient client) {
        if (state != State.WALKING) return;
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || path == null) { stop(client, "世界不可用"); return; }
        tickCounter++;

        // ---- 到达判定：水平≤2格 且 速度≈0 持续3秒 ----
        double dGoal = distXZ(p.getX(), p.getZ(), tx, tz);
        net.minecraft.entity.Entity e = p;
        double speed = Math.hypot(e.getX() - e.lastX, e.getZ() - e.lastZ);
        if (dGoal <= NavConfig.ARRIVE_DIST && Math.abs(p.getY() - ty) <= NavConfig.ARRIVE_DY
                && speed <= NavConfig.STILL_SPEED) {
            if (++stillTicks >= NavConfig.ARRIVE_STILL_TICKS) {
                state = State.IDLE; path = null; releaseKeys(client);
                msg(client, "§a[Auto] §r已到达终点 [" + targetLabel + "]，自动停止。");
                return;
            }
        } else stillTicks = 0;

        // ---- 推进 waypoint ----
        while (wpIdx < path.size()) {
            Voxel wp = path.get(wpIdx);
            double wx = wp.x() + 0.5, wz = wp.z() + 0.5;
            if (distXZ(p.getX(), p.getZ(), wx, wz) < 0.75
                    && Math.abs(p.getY() - wp.y()) <= 1.6) {
                wpIdx++;
            } else break;
        }
        if (wpIdx >= path.size()) { // 路径走完但还没满足"静止3秒"，直接原地停下等判定
            p.input.movementForward = 0; p.input.movementSideways = 0;
            return;
        }

        // ---- 周期性重规划（应对动态障碍）----
        if (tickCounter % NavConfig.REPLAN_INTERVAL == 0) {
            Voxel from = new Voxel(floor(p.getX()), floor(p.getY()), floor(p.getZ()));
            Voxel goal = path.get(path.size() - 1);
            List<Voxel> np = AStar.find(client.world, from, goal);
            if (np != null && np.size() > 1) {
                path = np; wpIdx = 1; stuckTicks = 0; // 跳过脚下当前格
                lastProgressDist = distXZ(p.getX(), p.getZ(), goal.x() + 0.5, goal.z() + 0.5);
            }
        }

        // ---- 转向 & 前进 ----
        Voxel wp = path.get(wpIdx);
        double wx = wp.x() + 0.5, wz = wp.z() + 0.5;
        double wantYaw = yawTo(p.getX(), p.getZ(), wx, wz);
        p.setYaw(lerpAngle(p.getYaw(), wantYaw, NavConfig.TURN_LERP));
        p.setPitch(-15f); // 略俯视，方便视觉抓前方地面

        double dyStep = wp.y() - floor(p.getY());
        boolean facingOk = Math.abs(angleDiff(p.getYaw(), wantYaw)) < 25;
        p.input.jumping = false; // 每 tick 重置，仅在需要上台阶的那一 tick 置 true
        if (facingOk) {
            p.input.movementForward = 1;
            p.input.movementSideways = 0;
            // 1格台阶：正对且落地时起跳（按住持续跳直到 waypoint 推进后自动复位）
            if (dyStep >= 1 && p.isOnGround()) p.input.jumping = true;
        } else {
            p.input.movementForward = 0;
        }

        // ---- 视觉辅助：前方疑似大面积深色/水 → 减速观察（不粗暴停止）----
        if (tickCounter % 10 == 0) Vision.grab(client);
        if (Vision.STATE.blockedAhead && facingOk && p.isOnGround()) {
            p.input.movementForward = 0; // 停一帧观察，下帧由碰撞/卡住逻辑决定绕行或停止
        }

        // ---- 卡住检测 ----
        double nowDist = distXZ(p.getX(), p.getZ(), path.get(path.size() - 1).x() + 0.5, path.get(path.size() - 1).z() + 0.5);
        if (nowDist < lastProgressDist - 0.15) { stuckTicks = 0; lastProgressDist = nowDist; }
        else if (facingOk && ++stuckTicks >= NavConfig.STUCK_TICKS) {
            stop(client, "长时间无法前进（可能被阻挡/需要搭路，平地版不支持）");
        }
        if (nowDist < lastProgressDist) lastProgressDist = nowDist;
    }

    /** 断线/退出时强制释放按键状态。 */
    public static void forceStopSilent(MinecraftClient client) {
        state = State.IDLE; path = null; wpIdx = 0; releaseKeys(client);
    }

    // ---------------- helpers ----------------

    private static void releaseKeys(MinecraftClient client) {
        if (client.player != null) {
            client.player.input.movementForward = 0;
            client.player.input.movementSideways = 0;
            client.player.input.jumping = false;
        }
    }

    private static Voxel findNearStandable(MinecraftClient c, Voxel to) {
        if (AStar.canStand(c.world, to.x(), to.y(), to.z())) return to;
        for (int r = 1; r <= 4; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = 2; dy >= -2; dy--) {
                        Voxel v = new Voxel(to.x() + dx, to.y() + dy, to.z() + dz);
                        if (AStar.canStand(c.world, v.x(), v.y(), v.z())) return v;
                    }
        return null;
    }

    private static int floor(double d) { return (int) Math.floor(d); }
    private static double distXZ(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2; return Math.sqrt(dx * dx + dz * dz);
    }
    /** MC yaw: 0=南(+z)? 实际 0=-z(北)。用 atan 匹配: yaw = atan2(-dx, dz) 度。 */
    private static double yawTo(double fx, double fz, double tx, double tz) {
        double dx = tx - fx, dz = tz - fz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return yaw;
    }
    private static double angleDiff(double a, double b) {
        double d = (b - a) % 360.0;
        if (d > 180) d -= 360; if (d < -180) d += 360;
        return d;
    }
    private static float lerpAngle(float cur, double target, double t) {
        double d = angleDiff(cur, target);
        return (float) (cur + d * t);
    }
    private static void msg(MinecraftClient c, String text) {
        if (c != null && c.inGameHud != null) c.inGameHud.getChatHud().addMessage(Text.literal(text));
        MetroMapMod.LOGGER.info(text.replaceAll("§.", ""));
    }
}
