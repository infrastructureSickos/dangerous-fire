package com.infrastructuresickos.dangerous_fire;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class DFConfig {
    public static final ForgeConfigSpec SPEC;
    public static final DFConfig INSTANCE;

    static {
        Pair<DFConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(DFConfig::new);
        INSTANCE = specPair.getLeft();
        SPEC = specPair.getRight();
    }

    public final ForgeConfigSpec.IntValue lavaIgnitionRadius;
    public final ForgeConfigSpec.IntValue magmaIgnitionRadius;
    public final ForgeConfigSpec.DoubleValue ignitionChance;
    public final ForgeConfigSpec.DoubleValue spreadChance;
    public final ForgeConfigSpec.IntValue spreadDistance;

    private DFConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ignition");
        lavaIgnitionRadius = builder.comment("Radius around lava to scan for flammable blocks (default 3)")
                                    .defineInRange("lavaRadius", 3, 1, 16);
        magmaIgnitionRadius = builder.comment("Radius around magma to scan for flammable blocks (default 2)")
                                     .defineInRange("magmaRadius", 2, 1, 16);
        ignitionChance = builder.comment("Chance per eligible block to ignite on each check (0.0–1.0)")
                                .defineInRange("ignitionChance", 0.05, 0.0, 1.0);
        builder.pop();

        builder.push("spread");
        spreadChance = builder.comment("Chance for a naturally-extinguished fire to spawn 2 new fires (0.0–1.0)")
                              .defineInRange("spreadChance", 0.5, 0.0, 1.0);
        spreadDistance = builder.comment("Max distance from extinguished fire to place new fires")
                                .defineInRange("spreadDistance", 4, 1, 16);
        builder.pop();
    }
}
