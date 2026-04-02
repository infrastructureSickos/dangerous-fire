package com.infrastructuresickos.dangerous_fire;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DangerousFire.MOD_ID)
public class DangerousFire {
    public static final String MOD_ID = "dangerous_fire";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public DangerousFire() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DFConfig.SPEC);
        // Single manual registration on the Forge bus — FireEventHandler has no
        // @Mod.EventBusSubscriber annotation, preventing the double-registration bug.
        MinecraftForge.EVENT_BUS.register(new FireEventHandler());
        LOGGER.info("DangerousFire initialized");
    }
}
