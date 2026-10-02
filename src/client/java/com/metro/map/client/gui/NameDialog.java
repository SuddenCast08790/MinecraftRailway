package com.metro.map.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * 站点命名对话框：文本框 + 确认/取消。
 * 留空时由调用方使用自动编号（如 “站-3”）。
 */
public class NameDialog extends Screen {
    private TextFieldWidget input;
    private final String defaultName;
    private final Consumer<String> onConfirm; // null = 用户取消
    private final Screen parentFallback;

    public NameDialog(String title, String defaultName, Consumer<String> onConfirm) {
        super(Text.literal(title));
        this.defaultName = defaultName == null ? "" : defaultName;
        this.onConfirm = onConfirm;
        this.parentFallback = MinecraftClient.getInstance().currentScreen;
    }

    @Override
    protected void init() {
        int w = Math.min(240, this.width - 40);
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 20;

        this.input = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal("站名"));
        this.input.setPlaceholder(Text.literal(this.defaultName));
        this.input.setText("");
        addDrawableChild(this.input);
        this.setInitialFocus(this.input);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> confirm())
                .dimensions(x, y + 28, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(x + w / 2 + 2, y + 28, w / 2 - 2, 20).build());
    }

    private void confirm() {
        String name = this.input.getText().trim();
        if (name.isEmpty()) name = this.defaultName; // 自动编号兜底
        this.onConfirm.accept(name);
        this.close();
    }

    @Override
    public void close() {
        // Esc 视为取消
        if (this.client != null) this.client.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, this.title,
                this.width / 2 - this.textRenderer.getWidth(this.title) / 2,
                this.height / 2 - 44, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false; // 打开对话框时游戏不暂停，行走采样继续
    }
}
