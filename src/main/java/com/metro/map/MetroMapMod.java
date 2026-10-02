package com.metro.map;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用入口（仅客户端功能，但保留标准 Mod 初始化以便在 PaperMC+Fabric 环境安全加载）。
 */
public class MetroMapMod implements ModInitializer {
    public static final String MOD_ID = "metro_map";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Metro Map loaded (client-only features).");
    }
}
