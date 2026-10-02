package com.metro.map.client.autowalk;

/**
 * 自动寻路参数（平地版 + 1格落差）。
 */
public class NavConfig {
    /** 到达判定：水平距离阈值（格）。 */
    public static final double ARRIVE_DIST = 2.0;
    /** 到达判定：垂直容差（格，允许站上下几格内）。 */
    public static final double ARRIVE_DY = 3.0;
    /** 到达判定：速度≈0 需持续的 tick 数（3 秒 = 60 tick）。 */
    public static final int ARRIVE_STILL_TICKS = 60;
    /** 速度≈0 的阈值（格/tick）。 */
    public static final double STILL_SPEED = 0.02;
    /** 单次 A* 展开节点上限（防卡死）。 */
    public static final int MAX_EXPANSIONS = 8000;
    /** 路径重算间隔（tick）。 */
    public static final int REPLAN_INTERVAL = 40;
    /** 连续无法前进多少 tick 后自动停止并提示（防被墙卡死）。 */
    public static final int STUCK_TICKS = 100;
    /** 每 tick 转向平滑系数（0~1，越大转得越快）。 */
    public static final double TURN_LERP = 0.25;
    /** 前方碰撞检测的水平采样距离（格）。 */
    public static final double PROBE_DIST = 0.9;
}
