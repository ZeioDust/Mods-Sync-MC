package com.modssync.client;

import com.modssync.ModsSync;
import com.modssync.core.PendingDisableStore;
import com.modssync.core.SyncFolders;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches {@link com.modssync.DisablerMain} as a detached, windowless subprocess
 * that will rename mod jars to {@code .disabled} <em>after</em> the game exits.
 *
 * <h3>Why a detached process?</h3>
 * <p>Fabric's loader holds every mod {@code .jar} open for the life of the Minecraft
 * process. Trying to rename those jars while the game is running fails on Windows
 * (file in use) and is unreliable on other platforms. The solution is to spawn a
 * separate JVM that can outlive the game: it runs after {@code CLIENT_STOPPING} fires
 * but <em>before</em> the main JVM has fully exited, then retries the renames until
 * the handles are free (up to 30 seconds; see {@code DisablerMain}).
 *
 * <h3>No console window on Windows</h3>
 * <p>On Windows, launching {@code java.exe} opens a console window which flashes
 * briefly and confuses players. We therefore use {@code javaw.exe} (same JVM, no
 * console) when available. On macOS/Linux there is no equivalent distinction — both
 * executables are simply {@code java}.
 *
 * <h3>Finding the jar / class directory</h3>
 * <p>The headless process needs ModsSync on its classpath so it can run
 * {@code DisablerMain}. We first ask Fabric for the mod container's origin paths
 * (works for both the production jar and a dev-env exploded-classes directory).
 * If that fails, we fall back to the {@code ProtectionDomain} code-source URL,
 * which is the standard Java way to locate "where did this class come from?".
 */
public final class ExternalDisabler {

    private ExternalDisabler() {}

    /**
     * Builds and starts the detached disabler process for the given pending-disable entries.
     * Entries that don't pass the folder/filename safety checks are silently skipped.
     * Logs a warning (but does not throw) if the java binary or classpath can't be located.
     *
     * @param gameDir the game's root directory, used to resolve absolute file paths
     * @param entries the mod files that should be renamed to {@code .disabled}
     */
    public static void scheduleAfterExit(Path gameDir, List<PendingDisableStore.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        // Resolve each entry to an absolute path, filtering out anything that looks unsafe.
        List<String> sources = new ArrayList<>();
        for (PendingDisableStore.Entry e : entries) {
            if (SyncFolders.isAllowed(e.folder()) && SyncFolders.isSafeName(e.fileName())) {
                sources.add(gameDir.resolve(e.folder()).resolve(e.fileName()).toString());
            }
        }
        if (sources.isEmpty()) {
            return;
        }

        try {
            String javaBin = javaExecutable();
            String classpath = locateSelfJar();
            if (javaBin == null || classpath == null) {
                ModsSync.LOGGER.warn("ModsSync: couldn't locate java/jar for the disabler; {} mod(s) left enabled.",
                        sources.size());
                return;
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("com.modssync.DisablerMain");
            cmd.addAll(sources); // each absolute path becomes one argument

            // Fully detach: discard stdout/stderr and do NOT wait for the process.
            // The game will continue shutting down; the helper outlives it.
            new ProcessBuilder(cmd)
                    .directory(gameDir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            ModsSync.LOGGER.info("ModsSync: scheduled {} extra mod(s) to disable after exit (headless, no window).",
                    sources.size());
        } catch (Exception ex) {
            ModsSync.LOGGER.warn("ModsSync: failed to launch the after-exit disabler: {}", ex.toString());
        }
    }

    /**
     * Returns the path to a suitable {@code java} executable for the currently running JVM.
     *
     * <p>Prefers {@code $JAVA_HOME/bin/javaw.exe} on Windows (no console window) and
     * {@code $JAVA_HOME/bin/java} on other platforms. Falls back to the bare command name
     * (relying on {@code PATH}) if the expected binary doesn't exist at that location,
     * which can happen in unusual JRE layouts.
     */
    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path bin = Path.of(javaHome, "bin");
        // javaw.exe on Windows suppresses the console window that java.exe would create.
        Path exe = windows ? bin.resolve("javaw.exe") : bin.resolve("java");
        if (Files.isRegularFile(exe)) {
            return exe.toString();
        }
        // Fallback to plain "java"/"javaw" on PATH.
        return windows ? "javaw" : "java";
    }

    /**
     * Finds the jar (or dev-env classes directory) that contains this class so the
     * headless JVM can load {@code DisablerMain}.
     *
     * <p>The primary approach asks Fabric's mod container for the origin paths — this
     * works reliably in both production (a single fat jar) and in a Gradle dev environment
     * (an exploded directory of compiled classes). The secondary fallback reads the
     * {@code ProtectionDomain} code-source location, which is the standard Java mechanism
     * for "where did this bytecode come from?".
     *
     * @return an absolute path string suitable for use as a {@code -cp} argument, or
     *         {@code null} if neither strategy succeeds.
     */
    private static String locateSelfJar() {
        try {
            var mc = FabricLoader.getInstance().getModContainer(ModsSync.MODID).orElse(null);
            if (mc != null) {
                for (Path p : mc.getOrigin().getPaths()) {
                    String s = p.toString();
                    // Accept either a .jar file or a directory (Gradle dev-env exploded classes).
                    if (s.toLowerCase().endsWith(".jar") || Files.isDirectory(p)) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Last-resort: ask the class loader where it loaded this very class from.
        try {
            return new File(ExternalDisabler.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getPath();
        } catch (Throwable ignored) {
        }
        return null;
    }
}
