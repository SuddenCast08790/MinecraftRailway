package com.metro.map.client;

import com.metro.map.client.command.MetroCommand;
import com.metro.map.client.hud.MetroHud;
import com.metro.map.client.session.SessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：注册按键、tick 采样、命令与退出即时保存钩子。
 */
public class MetroMapClient implements ClientModInitializer {
    public static final String MOD_ID = "metro_map";
    public static final Logger LOGGER = LoggerFactory.getLogger("metro_map:client");

    @Override
    public void onInitializeClient() {
        // 加载持久化数据（含崩溃恢复：进行中的行走会话）
        SessionManager.init();

        // 绑定按键（可在 选项->按键绑定 中修改）
        MetroKeys.register();

        // 每 tick：处理按键按下 + 行走路径采样 + 每秒即时保存
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (MetroKeys.ADD_STATION.wasPressed()) {
                StationFlow.onAddStationKey(client);
            }
            if (MetroKeys.CANCEL.wasPressed()) {
                StationFlow.onCancelKey(client);
            }
            SessionManager.onEndTick(client);
        });

        // HUD 状态提示
        MetroHud.register();

        // 客户端命令 /metro ...
        MetroCommand.register();

        // 断开连接 / 关闭游戏时强制落盘
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SessionManager.flushAll());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SessionManager.flushAll());

        LOGGER.info("Metro Map client initialized. Press {} to add a station.",
                MetroKeys.ADD_STATION.getBoundKeyLocalizedText().getString());
    }
}
