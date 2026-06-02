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
- On connect, the server advertises its allowed mod/pack set.
- Your client compares it to your folders. If everything matches, you join normally.
- If not, ModsSync clones what's missing and schedules unauthorized files for removal, then asks you to **restart once**. After the restart you're perfectly in sync and can play.
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

## TODO before submitting
- Add a **128×128 `icon.png`** at `src/main/resources/assets/modssync/icon.png` (referenced by `fabric.mod.json`; optional but recommended — without it Fabric just logs a warning).
- Set the **license** in the CurseForge UI to **MIT** (the jar already declares `MIT`, and a `LICENSE` file is in the repo root).
- Upload `jars/modssync_4.1.2-fabric.26.1.2.jar`, set game version **26.1.2** + loader **Fabric**, and add **Fabric API** as a required dependency/relation.
