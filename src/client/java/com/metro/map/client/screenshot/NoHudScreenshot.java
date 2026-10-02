package com.metro.map.client.screenshot;

import com.metro.map.MetroMapMod;
import com.metro.map.storage.StorageManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 无 HUD 高清截图。
 *
 * 实现原理：vanilla 的 MinecraftClient#takeScreenshot(Path) 在渲染一帧并保存时
 * 本身就不绘制 HUD / BossBar / 聊天栏（与 F2 行为一致），因此：
 *   1. 记录并临时关闭当前 Screen（如命名对话框），保证画面纯净；
 *   2. takeScreenshot() 同步渲染并写入 PNG；
 *   3. IO 线程把文件移动到 metro_map/screenshots/；
 *   4. 主线程恢复 Screen 并回调（回填 station.photo）。
 * 若目标路径已存在则 vanilla 会自动追加 (n)，移动逻辑按“最新文件”匹配，始终正确。
 */
public class NoHudScreenshot {

    public interface Callback {
        /** fileName 为 metro_map/screenshots/ 下的文件名；失败时为 null。在主线程回调。 */
        void onDone(String fileName);
    }

    public static void capture(MinecraftClient client, Callback cb) {
        Screen saved = client.currentScreen;
        if (saved != null) client.setOverlay(null); // 关闭 GUI，只留世界画面（本就无 HUD）

        String name = "station_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path target = client.runDirectory.toPath()
                .resolve("screenshots").resolve(name + ".png");
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException ignored) {
        }

        Path written;
        try {
            written = client.takeScreenshot(target); // 同步渲染+写盘（vanilla 原生能力）
        } catch (Exception e) {
            MetroMapMod.LOGGER.error("takeScreenshot failed", e);
            restore(client, saved);
            cb.onDone(null);
            return;
        }
        if (written == null) { // 渲染器未就绪（如暂停瞬间）
            restore(client, saved);
            cb.onDone(null);
            return;
        }

        final Path file = written;
        Util.getIoWorkerExecutor().execute(() -> {
            String result = null;
            try {
                Path dest = StorageManager.getScreenshotsDir().resolve(file.getFileName().toString());
                Files.createDirectories(dest.getParent());
                Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
                result = dest.getFileName().toString();
            } catch (IOException e) {
                MetroMapMod.LOGGER.error("Failed to move screenshot to metro_map/screenshots", e);
            }
            final String r = result;
            client.execute(() -> {
                restore(client, saved);
                cb.onDone(r);
            });
        });
    }

    private static void restore(MinecraftClient client, Screen saved) {
        if (saved != null && client.currentScreen == null) client.setOverlay(saved);
    }
}
