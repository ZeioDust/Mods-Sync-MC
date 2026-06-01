# ModsSync — Design Spec

**Date:** 2026-06-01
**Status:** Approved design, pending implementation plan
**Target:** NeoForge, Minecraft 26.1.2 (`multi/neoforge/26-1-2/`)

## 1. Purpose

ModsSync is a **client-side mod** that makes a player's *active* mod set exactly
mirror the set of whatever server they join. On connect, it computes the
difference between the client's installed mods and the server's mods, then
rewrites the local `mods/` folder so the two match. Because Minecraft loads mods
only once at launch, any change requires a restart — so ModsSync gates entry
with an explicit "restart required" screen rather than changing anything
silently.

### Mirror rules

| Situation | Action |
|---|---|
| Mod on client, **not** on server | Disable it on the client |
| Mod on **both**, same version | Keep enabled |
| Mod on **both**, different version | Disable client version, download server's exact version |
| Mod on server, **not** on client | Download ("clone") it into the client `mods/` folder |

**Strict mirror:** there is no user "keep" list. Only a hardcoded *protected
core* is exempt (see §4). All other mods — including client-only convenience
mods (shaders, minimap, etc.) — are mirrored to the server and disabled if the
server lacks them.

## 2. End-to-end flow

```
Player clicks "Join Server"
        │
        ▼
Connecting screen (configuration phase)
        │
        ├─ Obtain server mod set (§3)
        │     • ModsSync-aware server → custom payload manifest (exact files/hashes/URLs)
        │     • Other server          → read handshake mod list (modid + version)
        │
        ▼
Diff engine compares server set vs local mods/ (§5)
        │
        ├─ No changes needed ───────────────► proceed to join (normal)
        │
        └─ Changes needed:
              1. Acquire missing / version-mismatched jars (§6)
              2. Rewrite mods/ folder: disable extras, place downloads (§7)
              3. Abort the connection
              4. Show "Mods Matched — Restart Required" screen (§8)
                   [ Exit The Game ]   [ Exit The Server ]
```

After restart the client's active mods match the server, so the next join
proceeds with no changes.

## 3. Server mod-set acquisition (Hybrid — Approach 3)

Two sources, tried in priority order:

### 3a. ModsSync-aware server (primary, full fidelity)
- ModsSync registers a NeoForge **custom payload** on a versioned channel.
- During the configuration phase, a server also running ModsSync sends a
  **manifest**: for each server mod — `modId`, `version`, `fileName`,
  `sha1` hash, and a `downloadUrl` (the server serving its own jar) plus
  optional `modrinthId` / `curseforgeId`.
- Pure NeoForge networking API — no mixins. This is the reliable path:
  exact files and hashes mean deterministic, verifiable downloads.

### 3b. Non-ModsSync server (fallback, best-effort)
- A **mixin** into NeoForge's client connection listener captures the mod list
  NeoForge already exchanges during the handshake: `modId` + `version` only.
- Files are then resolved from public repositories (§6b).
- **Known limitation (surfaced in UI):** a NeoForge `modId` is not guaranteed to
  equal a Modrinth/CurseForge slug, and private/custom jars may not be published
  anywhere. Resolution is best-effort; unresolved mods are reported, not hidden.

The mixin is a *fallback* path only; aware-server syncing never depends on it.

## 4. Protected core (never disabled/deleted)

A hardcoded allowlist, identified by `modId`:
- `modssync` itself
- The loader: `neoforge`, `minecraft`, `fml`
- ModsSync's own runtime library dependencies

These are filtered out of every "disable" and "delete" decision. If a protected
mod is genuinely absent on the server, that is reported but never acted on.

## 5. Local inventory + diff engine

### Local inventory
- Scan the `mods/` folder for `*.jar` (and `*.jar.disabled` to know what is
  already disabled / re-enableable).
- For each jar, read `META-INF/neoforge.mods.toml` to extract `modId` +
  `version`. Cache by file path + last-modified to avoid rescanning every join.

### Diff
Given `server = {modId → version}` and `local = {modId → {version, file, enabled}}`,
produce a `SyncPlan`:
- `toDisable`  — local mods not in server (minus protected core)
- `toDownload` — server mods missing locally, **or** present with a different version
- `toEnable`   — already-present-but-disabled mods that the server has at the matching version
- `unchanged`  — matching mods, already enabled

`SyncPlan.isEmpty()` ⇒ no changes ⇒ join proceeds normally.

## 6. Acquisition (downloading missing / mismatched jars)

For each `toDownload` entry, resolve a source in priority order:

### 6a. Server-provided (aware servers)
- Download from the manifest's `downloadUrl`, verify against the manifest `sha1`.

### 6b. Public repositories (fallback)
- **Modrinth** (no API key): if the manifest provided a `sha1`, use Modrinth's
  hash-based version lookup (exact). Otherwise search by `modId` + game version
  `26.1.2` + loader `neoforge`, then pick the matching `version`.
- **CurseForge** (requires an API key configured by the user): same idea via the
  CurseForge API.
- Downloads land in a temp dir, are hash/loader-validated, then moved into
  `mods/`.

Any entry that cannot be resolved from any source is recorded as
**unresolved** and shown on the restart screen.

## 7. Folder mutation

- **Disable:** rename `mod.jar` → `mod.jar.disabled` in place (NeoForge only
  loads `*.jar`). **Fallback:** if rename fails (locked/permission), delete the
  jar instead. Every action is logged.
- **Enable:** rename `mod.jar.disabled` → `mod.jar`.
- **Add:** move the validated downloaded jar into `mods/`.
- All mutations run against the protected-core filter (§4) as a final safety gate.

## 8. Restart gate UI

When `SyncPlan` was non-empty, ModsSync aborts the connection and replaces the
connect screen with a custom screen:

- Title/message: **"Your Mods Got Matched To The Server Mod, Please Restart Your
  Game"**
- A summary: counts of disabled / downloaded / enabled, and a list of any
  **unresolved** mods (best-effort path failures).
- Two buttons:
  - **Exit The Game** — quits Minecraft entirely.
  - **Exit The Server** — returns to the main/server-list screen (no restart;
    user restarts manually later).

## 9. Error handling

- **Network/payload failure (aware server):** fall back to handshake-list path.
- **Repo resolution failure:** mark mod unresolved, continue with the rest; never
  abort the whole sync for one mod.
- **Download hash mismatch:** discard the file, mark unresolved.
- **File-system lock on disable:** fall back to delete; if that also fails, mark
  the action failed and report it on the screen.
- **No changes + acquisition all succeeded:** normal join.
- ModsSync never modifies anything outside the `mods/` folder.

## 10. Component boundaries

| Component | Responsibility | Depends on |
|---|---|---|
| `NetworkManifest` | Custom-payload channel: send/receive ModsSync manifest | NeoForge networking |
| `HandshakeReader` (mixin) | Capture server modid+version on non-aware servers | NeoForge connection internals |
| `LocalInventory` | Scan `mods/`, parse toml, cache | filesystem |
| `DiffEngine` | Produce `SyncPlan` from server set + local inventory | LocalInventory |
| `Acquirer` | Resolve + download jars (server → Modrinth → CurseForge) | HTTP, ProtectedCore |
| `FolderMutator` | Disable/enable/add jars safely | ProtectedCore |
| `ProtectedCore` | Hardcoded never-touch allowlist | — |
| `SyncOrchestrator` | Drive the flow during configuration phase; decide gate vs join | all above |
| `RestartGateScreen` | The two-button UI | NeoForge client UI |

Each unit is independently testable: `DiffEngine`, `LocalInventory`,
`ProtectedCore`, and `FolderMutator` are pure-ish (filesystem/data) and need no
running game; networking/UI units are integration-tested.

## 11. Testing strategy

- **Unit:** `DiffEngine` (all four mirror rules + protected core), `LocalInventory`
  toml parsing, `FolderMutator` (disable→delete fallback) against a temp dir,
  `ProtectedCore` filtering.
- **Integration / game tests:** NeoForge gametest for the configuration-phase
  manifest exchange between a ModsSync client and ModsSync server.
- **Manual:** join (a) a ModsSync server with a mod diff, (b) a vanilla-NeoForge
  server with a mod diff, (c) an already-matching server (must join with no gate).

## 12. Security note

Auto-downloading and enabling jars from a server is, by nature, remote code
execution the user is opting into. ModsSync will: verify hashes when available,
prefer official repositories on the fallback path, and clearly list everything it
changed on the restart screen. This is documented behavior, not hidden.

## 13. Multi-loader porting

This spec targets `multi/neoforge/26-1-2/`. The loader-agnostic logic
(`LocalInventory`, `DiffEngine`, `ProtectedCore`, `FolderMutator`, `Acquirer`,
manifest data model) is written to minimize loader coupling so that future ports
(Fabric/Forge/Quilt, other versions) re-implement only the thin loader-specific
edges: networking channel, handshake reader, and the restart screen. Ports are
created by cloning this project under `multi/<loader>/<version>/` and translating
those edges.

## 14. Out of scope (v1)

- Re-enabling the player's "previous" mod set after leaving a server (one-way
  mirror only; the player restarts into whatever set was last synced).
- A GUI for configuring the CurseForge API key (config file only for v1).
- Syncing resource packs, config files, or world data.
