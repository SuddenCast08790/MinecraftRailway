package com.metro.map.client.autowalk;

import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量"视觉"：抓取当前帧中心小窗口像素，做规则分析（无模型、无大AI）：
 *   - blockedAhead : 正前方近处大面积深色 → 疑似墙/阴影障碍
 *   - waterAhead   : 前方下半部偏蓝绿占比高 → 疑似水域
 * 必须在渲染线程调用 grab()（GL 读取），分析在后台线程完成。
 */
public class Vision {
    private static final Logger LOG = LoggerFactory.getLogger("metro_map:vision");
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(
            r -> { Thread t = new Thread(r, "metro-vision"); t.setDaemon(true); return t; });

    public static class Snapshot {
        public volatile boolean blockedAhead = false;
        public volatile boolean waterAhead = false;
        public volatile long updatedAt = 0;
    }
    public static final Snapshot STATE = new Snapshot();

    private static final int W = 64, H = 48;          // 低分辨率足够规则判断
    private static ByteBuffer buf;

    /** 渲染线程调用：抓帧中心区域并异步分析。约每 N tick 一次即可。 */
    public static void grab(MinecraftClient client) {
        if (client == null || client.getWindow() == null) return;
        int fw = client.getWindow().getFramebufferWidth();
        int fh = client.getWindow().getFramebufferHeight();
        if (fw <= 0 || fh <= 0) return;
        int x0 = (fw - W) / 2, y0 = (fh - H) / 2;
        if (buf == null || buf.capacity() < W * H * 4) buf = BufferUtils.createByteBuffer(W * H * 4);
        buf.rewind();
        try {
            RenderSystem.assertOnRenderThread();
            GL11.glReadPixels(x0, y0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        } catch (Throwable t) {
            LOG.debug("glReadPixels failed", t);
            return;
        }
        byte[] px = new byte[W * H * 4];
        buf.rewind();
        buf.get(px);
        CompletableFuture.runAsync(() -> analyze(px), POOL);
    }

    /** 纯 CPU 规则分析（可单测）。GL_RGBA，行序自下而上。 */
    static void analyze(byte[] px) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;   // GL 底朝上 → 转正
                img.setRGB(x, y, ((px[i] & 0xFF) << 16) | ((px[i + 1] & 0xFF) << 8) | (px[i + 2] & 0xFF));
            }
        }
        // 中央带（画面中部 50%宽 × 中下 40%高）为"正前方地面/墙"关注区
        int cx0 = W / 4, cx1 = W * 3 / 4, cy0 = (int)(H * 0.35), cy1 = (int)(H * 0.75);
        int total = 0, dark = 0, blueGreen = 0;
        for (int y = cy0; y < cy1; y++) {
            for (int x = cx0; x < cx1; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                total++;
                int lum = (r + g + b) / 3;
                if (lum < 40) dark++;                                   // 近黑：墙影/未加载chunk
                else if (b > r + 25 && g > r + 10 && b > 90) blueGreen++; // 偏蓝绿：水
            }
        }
        if (total == 0) return;
        STATE.blockedAhead = dark > total * 0.55;
        STATE.waterAhead = blueGreen > total * 0.45;
        STATE.updatedAt = System.currentTimeMillis();
    }
}
