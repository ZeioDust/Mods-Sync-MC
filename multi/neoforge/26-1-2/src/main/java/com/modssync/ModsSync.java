package com.modssync;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import com.modssync.net.ManifestPayload;
import com.modssync.model.ServerMod;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import java.util.List;
import java.util.function.Consumer;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ModsSync.MODID)
public class ModsSync {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "modssync";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModsSync(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // RegisterConfigurationTasksEvent fires on the MOD bus — register there.
        modEventBus.addListener(ModsSync::onRegisterConfigurationTasks);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("ModsSync common setup");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("ModsSync server starting");
    }

    /**
     * Registers a configuration-phase task that sends the server's mod manifest to the
     * joining client before the PLAY phase begins.
     *
     * <p>API used: {@code net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent}
     * (MOD bus event) + {@code net.neoforged.neoforge.network.configuration.ICustomConfigurationTask}.
     * The task's {@code run(Consumer<CustomPacketPayload>)} sends the payload and
     * {@code event.getListener().finishCurrentTask(type())} signals completion.
     * Both APIs confirmed present in NeoForge 26.1.2.64-beta universal jar.</p>
     */
    private static void onRegisterConfigurationTasks(RegisterConfigurationTasksEvent event) {
        List<ServerMod> serverMods = ModList.get().getMods().stream()
                .map(mi -> ServerMod.ofIdVersion(mi.getModId(), mi.getVersion().toString()))
                .toList();
        event.register(new ManifestConfigTask(serverMods, event));
    }

    /** Configuration task that delivers the server mod manifest to the client. */
    private record ManifestConfigTask(
            List<ServerMod> serverMods,
            RegisterConfigurationTasksEvent event
    ) implements ICustomConfigurationTask {

        private static final ConfigurationTask.Type TYPE =
                new ConfigurationTask.Type("modssync:send_manifest");

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(new ManifestPayload(serverMods));
            event.getListener().finishCurrentTask(type());
        }
    }
}
