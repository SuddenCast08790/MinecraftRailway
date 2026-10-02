package com.metro.map.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 功能按键：
 *  - G     : 设置站点（自动无HUD高清拍照；第二个站结算生成线路段）
 *  - H     : 取消当前进行中的线路段会话
 * 均可在 选项 -> 按键绑定 -> Metro Map 分类中修改。
 */
public class MetroKeys {
    public static KeyBinding ADD_STATION;
    public static KeyBinding CANCEL;
    public static KeyBinding AUTO_STOP;

    public static void register() {
        ADD_STATION = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.metro_map.add_station",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.metro_map"));
        CANCEL = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.metro_map.cancel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.metro_map"));
        AUTO_STOP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.metro_map.auto_stop",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.metro_map"));
    }
}
