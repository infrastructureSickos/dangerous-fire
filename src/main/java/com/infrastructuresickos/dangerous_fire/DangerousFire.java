package com.infrastructuresickos.dangerous_fire;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("dangerous_fire")
public class DangerousFire {
    public static final Logger LOGGER = LogManager.getLogger();

    public DangerousFire() {
        LOGGER.info("DangerousFire mod initialized");
    }
}
