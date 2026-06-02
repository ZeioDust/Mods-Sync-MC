package com.modssync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone entry point run as a <em>separate, headless JVM process</em> after
 * Minecraft closes, for the sole purpose of renaming locked mod jars to
 * {@code <name>.disabled}.
 *
 * <h3>Why a separate process?</h3>
 * <p>On Windows (and technically on any OS), the JVM holds a file handle on every
 * {@code .jar} it loads. Fabric's loader opens mod jars at startup and keeps them
 * open until the process exits, making it impossible to rename or delete them from
 * within the game. The solution is to launch this class from {@link ExternalDisabler}
 * as a new, short-lived JVM <em>after</em> the game's {@code CLIENT_STOPPING} event
 * fires — before the game process has fully terminated. Once the game exits and
 * releases its file handles, this process can perform the renames.
 *
 * <h3>Why a polling loop?</h3>
 * <p>There is a small window between when {@code CLIENT_STOPPING} fires and when the
 * OS actually frees the file handles. The loop retries every second for up to ~30
 * seconds to handle that window gracefully. In practice the files are almost always
 * free on the first or second attempt.
 *
 * <h3>Dependencies</h3>
 * <p>This class uses only {@code java.*} so it can be invoked with a plain
 * {@code java -cp modssync.jar com.modssync.DisablerMain <path1> <path2> ...}
 * with no Minecraft or Fabric on the classpath. {@link ExternalDisabler} uses
 * {@code javaw.exe} on Windows so no console window appears to the user.
 */
public final class DisablerMain {

    private DisablerMain() {}

    /**
     * Each argument is the absolute path of a mod jar to rename to
     * {@code <name>.disabled}. Retries until all renames succeed or the 30-second
     * deadline is reached.
     */
    public static void main(String[] args) {
        List<Path> sources = new ArrayList<>();
        for (String a : args) {
            sources.add(Path.of(a));
        }
        long deadlineMs = System.currentTimeMillis() + 30_000L; // retry up to ~30s
        while (true) {
            boolean stillLocked = false;
            for (Path src : sources) {
                if (Files.exists(src)) {
                    Path dst = src.resolveSibling(src.getFileName().toString() + ".disabled");
                    try {
                        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        // The game probably hasn't fully exited yet — the JVM still holds the handle.
                        stillLocked = true;
                    }
                }
            }
            if (!stillLocked || System.currentTimeMillis() > deadlineMs) {
                break;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
