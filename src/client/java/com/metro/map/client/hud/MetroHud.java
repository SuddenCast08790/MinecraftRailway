package com.metro.map.client.hud;

import com.metro.map.client.MetroKeys;
import com.metro.map.client.session.SessionManager;
import com.metro.map.model.Line;
import com.metro.map.session.WalkSession;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

/**
 * 左上角状态提示：激活线路 / 站点数 / 当前采样点数 / 按键提示。
 */
@Environment(EnvType.CLIENT)
public class MetroHud {

    public static void register() {
        HudRenderCallback.EVENT.register(MetroHud::render);
    }

    private static void render(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null && client.options.hudHidden) return;
        Line line = SessionManager.getActiveLine();
        if (line == null) return;

        int x = 4, y = 20;
        WalkSession ws = SessionManager.getSession(line.id);
        draw(ctx, x, y, "§b[Metro] §r" + line.name + " (" + line.id + ")", false);
        draw(ctx, x, y + 10, String.format("§7站点 %d | 线路段 %d | 采样 %d 点%s",
                line.stations.size(), line.segments.size(),
                ws != null ? ws.getPoints().size() : 0,
                ws != null ? "" : " §c(未在记录)"), false);
        KeyBinding k = MetroKeys.ADD_STATION;
        if (k != null) {
            draw(ctx, x, y + 20, "§7[" + k.getBoundKeyLocalizedText().getString() + "]设站 · ["
                    + MetroKeys.CANCEL.getBoundKeyLocalizedText().getString() + "]取消/停用", false);
        }
    }

    private static void draw(DrawContext ctx, int x, int y, String text, boolean fade) {
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(text), x, y, 0xFFFFFF);
    }
}
