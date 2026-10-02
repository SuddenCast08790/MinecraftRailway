package com.metro.map.client.gui;

import com.metro.map.MetroMapMod;
import com.metro.map.client.session.SessionManager;
import com.metro.map.model.Line;
import com.metro.map.storage.StorageManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 线路设置 GUI：新建线路（name / color / operator），或编辑当前激活线路的属性。
 */
public class LineConfigScreen extends Screen {
    private TextFieldWidget nameField;
    private TextFieldWidget colorField;
    private TextFieldWidget operatorField;

    public LineConfigScreen() {
        super(Text.literal("Metro Map - 线路设置"));
    }

    @Override
    protected void init() {
        int w = Math.min(260, this.width - 40);
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 60;

        Line active = SessionManager.getActiveLine();

        this.nameField = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal("线路名称"));
        this.nameField.setPlaceholder(Text.literal("如: 1号线"));
        if (active != null) this.nameField.setText(active.name);
        addDrawableChild(this.nameField);

        this.colorField = new TextFieldWidget(this.textRenderer, x, y + 30, w, 20, Text.literal("颜色"));
        this.colorField.setPlaceholder(Text.literal("#RRGGBB, 如 #E4002B"));
        if (active != null) this.colorField.setText(active.color);
        else this.colorField.setText("#E4002B");
        addDrawableChild(this.colorField);

        this.operatorField = new TextFieldWidget(this.textRenderer, x, y + 60, w, 20, Text.literal("运营方"));
        this.operatorField.setPlaceholder(Text.literal("operator"));
        if (active != null) this.operatorField.setText(active.operator);
        addDrawableChild(this.operatorField);

        String btn = active == null ? "创建并激活线路" : "保存修改";
        addDrawableChild(ButtonWidget.builder(Text.literal(btn), b -> apply())
                .dimensions(x, y + 92, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> this.close())
                .dimensions(x + w / 2 + 2, y + 92, w / 2 - 2, 20).build());

        this.setInitialFocus(this.nameField);
    }

    private boolean isColorValid(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}");
    }

    private void apply() {
        String name = nameField.getText().trim();
        String color = colorField.getText().trim();
        String operator = operatorField.getText().trim();
        if (name.isEmpty()) { name = "线路-" + (SessionManager.data.lines.size() + 1); }
        if (!isColorValid(color)) {
            colorField.setText("颜色需 #RRGGBB");
            return; // 颜色格式非法，不提交
        }
        if (operator.isEmpty()) operator = "Unknown";

        boolean isNew = SessionManager.getActiveLine() == null;
        Line active = SessionManager.getActiveLine();
        if (active == null) {
            Line line = new Line(SessionManager.data.nextLineId(), name, color, operator);
            if (client != null && client.world != null) {
                line.dimension = client.world.getRegistryKey().getValue().toString();
            }
            SessionManager.data.lines.add(line);
            SessionManager.setActiveLine(line.id);
            SessionManager.saveData(); // 即时落盘
            MetroMapMod.LOGGER.info("Created line {} ({})", line.id, line.name);
        } else {
            active.name = name;
            active.color = color;
            active.operator = operator;
            SessionManager.saveData();
        }
        this.close();
        if (client != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(
                    "§a[Metro] §r线路已" + (isNew ? "创建并激活: " : "更新: ")
                            + name + "。按绑定键设第一个站。"));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int y = this.height / 2 - 76;
        int x = (this.width - Math.min(260, this.width - 40)) / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal("名称 Name"), x, y, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("颜色 Color (#RRGGBB)"), x, y + 30, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("运营方 Operator"), x, y + 60, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
