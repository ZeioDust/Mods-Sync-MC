# ModsSync — CurseForge listing copy

## Short summary (the one-line field)
Server-enforced mod & resource-pack sync — block cheat mods/packs (minimap, x-ray) and auto-match clients to the server's set.

---

## Full description (paste into the project page)

**ModsSync** lets a server define exactly which mods and resource packs its players may use, and makes every client match — automatically.

When you join a ModsSync server:

- ✅ **Mods the server has, you don't** → cloned to your client.
- ✅ **Resource packs the server offers** → cloned to your client.
- 🚫 **Unauthorized extra mods/packs** (minimaps, x-ray, radars, etc.) → **you can't join with them active**; they're disabled so you rejoin clean.
- 🔒 **Server-only and core/platform mods** (the loader, Fabric API, Minecraft itself, ModsSync) are never touched or sent.
- 🧩 The server can also **require ModsSync** — clients without it are turned away.

The result: a consistent, fair-play modpack across everyone on the server, with no manual setup for players.

### How it works
- On connect, the server advertises its allowed mod/pack set — all **while you're still on the loading screen**, before the world loads.
- Your client compares it to your folders. If everything matches, you drop straight into the world.
- If not, ModsSync clones what's missing (with a **live progress bar** so a big modpack doesn't look frozen) and schedules unauthorized files for removal — **all in a single connection** — then asks you to **restart once**. After the restart you're perfectly in sync and can play.
- A **mid-session guard** keeps watching: try to enable a cheat resource pack (e.g. X-ray) after joining and you're kicked, with the pack scheduled for removal.
- Disabling happens cleanly between launches (a small headless helper, no console window; works on Windows/macOS/Linux).

### For server admins
Put the mods you allow in the server's `mods/` folder and any packs in `resourcepacks/`. Install ModsSync (+ Fabric API) on the server. That's it — joining clients are synced and enforced. Mark a mod `"environment": "server"` to keep it server-side and never send it to clients.

### Requirements
- Minecraft **26.1.2**, **Fabric Loader 0.19+**, **Fabric API**
- Required on **both** the server and clients.

---

## ⚠️ Security notice (KEEP THIS — it's what makes the mod policy-compliant)

> **ModsSync downloads mod and resource-pack files from the servers you join and installs them into your game.** That means a server can place files on your computer. **Only use ModsSync on servers you trust.** Treat joining a ModsSync server like you treat installing that server's modpack.

---

## Reviewer note (optional — paste in the submission notes, not the public page)
ModsSync is a server-side fair-play / modpack-consistency tool. File transfer only occurs between a server the user has chosen to join and that user's client, and only within the `mods/` and `resourcepacks/` folders. It transfers the files the server administrator placed on their own server. A prominent security notice is shown on the page (above). This is the same model as the existing, approved mod "AutoModpack."

---

## Upload settings (current build)
- **File:** `jars/modssync_6.17.10-fabric.26.1.2.jar`
- **Game version:** 26.1.2  ·  **Modloader:** Fabric  ·  **Java:** 25
- **Release type:** Release
- **Relations:** add **Fabric API** as a *Required dependency*.
- **License:** **MIT** (the jar already declares `MIT`; `LICENSE` file is in the repo root).
- Required on **both** the server and the client.

## Changelog (6.17.10)
- Sync now runs entirely during the connection/loading phase — the download **progress bar shows on the loading screen**, and matching/downloading/disabling all complete in **one connection** (no more connect-twice).
- Added a **mid-session resource-pack guard**: enabling a pack the server didn't offer kicks you and queues it for removal.
- Finishing the transfer now shows a clear "Mods Updated — please restart" screen instead of leaving you on the progress bar.

## TODO before submitting
- Add a **128×128 `icon.png`** at `src/main/resources/assets/modssync/icon.png` (referenced by `fabric.mod.json`; optional — without it Fabric just logs a warning).
