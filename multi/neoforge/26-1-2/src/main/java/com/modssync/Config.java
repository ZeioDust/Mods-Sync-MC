package com.modssync;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config for ModsSync. */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> CURSEFORGE_API_KEY = BUILDER
            .comment("Optional CurseForge API key, used as a fallback download source. Leave blank to disable.")
            .define("curseforgeApiKey", "");

    public static final ModConfigSpec SPEC = BUILDER.build();
}
