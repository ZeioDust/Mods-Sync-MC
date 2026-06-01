package com.modssync;

import net.neoforged.neoforge.common.ModConfigSpec;

// Common config for ModsSync. Add config options to the builder as the mod grows.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();
}
