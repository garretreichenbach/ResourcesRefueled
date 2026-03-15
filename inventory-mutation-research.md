# Inventory Mutation Research — Logistics Mod

Date: 2026-03-07

This document summarizes the research and prioritized targets for intercepting every code path that adds/removes items to/from inventories in the StarMade codebase. Use this as a checklist when writing mixins to replace the vanilla teleportation system with a logistics/tube/belt system.

---

## TL;DR

Intercept the core Inventory mutation primitives first (`Inventory.inc`, `Inventory.put`, `Inventory.handleReceived`, `Inventory.deserialize`, `Inventory.doSwitchSlotsOrCombine`) then cover network application points (`RemoteInventoryMultMod` / `InventoryMultMod` handling), world spawn/pickup (`RemoteSector.addItem`, `PlayerState` drop/pickup), and direct `inventoryMap.put/remove` spots (deserialization, save/load, server apply). Add temporary logging mixins to validate coverage, then replace logs with logistics dispatch/cancel logic.

Current status: pre-mixin runtime scaffolding is implemented in `src/main/java/videogoose/resourcesreorganized/logistics/item/` and can now receive transfer intents once hooks are switched from probe logging to ingress.

---

## Quick checklist (progress-driven)

- [~] Add lightweight logging mixins for the high-priority methods (Inventory.inc, Inventory.put, Inventory.handleReceived, Inventory.doSwitchSlotsOrCombine, RemoteSector.addItem, Inventory.deserialize).
  - Implemented in `src/main/java/videogoose/resourcesreorganized/mixin/inventory/InventoryMutationMixin.java`: `Inventory.inc`, `Inventory.put(int, short, int, int)`, `Inventory.handleReceived`, `Inventory.deserialize`, `Inventory.deserializeSlot`, `Inventory.doSwitchSlotsOrCombine`
  - Implemented in `src/main/java/videogoose/resourcesreorganized/mixin/inventory/RemoteSectorMutationMixin.java`: `RemoteSector.addItem(Vector3f, short, int, int)`
  - Pending: `Inventory.put(int, MetaObject)`, `Inventory.removeSlot`, `Inventory.removeMetaItem`, `InventorySlot.inc`
- [ ] Run scenarios (pickup, drag/drop, shop buy, craft, admin give, save/load) and verify logs cover each.
- [ ] Add mixins for any missed paths uncovered by the logs.
- [ ] Implement cancel-and-emit logistics event in the authoritative server-side hooks.
- [ ] Add tests/instrumentation and remove temporary logs after confidence is achieved.

---

## Exact search tokens (copy-paste into your grep/IDE)

- Types / classes:
  - `\bInventory\b`
  - `\bInventorySlot\b`
  - `\bInventoryMultMod\b`
  - `\bRemoteInventoryMultMod\b`
  - `\bInventoryHolder\b`
  - `InventoryUtils`
  - `MetaObject`

- Methods / call tokens (literal):
  - `inc(`
  - `put(`
  - `handleReceived(`
  - `deserialize(`
  - `deserializeSlot`
  - `serializeSlot`
  - `sendInventoryModification(`
  - `sendInventorySlotRemove(`
  - `decreaseBatch(`
  - `doSwitchSlotsOrCombine(`
  - `switchSlotsOrCombineClient(`
  - `incExistingOrNextFreeSlot`
  - `putNextFreeSlot`
  - `removeSlot(`
  - `removeMetaItem(`
  - `spawnInSpace(`
  - `spawnVolumeInSpace(`
  - `getRemoteSector().addItem(`

- Patterns to catch direct map changes:
  - `inventoryMap\.(put|remove)\(`

---

## Prioritized file list & why (start here)

1. `src/main/java/org/schema/game/common/data/player/inventory/Inventory.java` (highest priority)
   - Key methods: `inc(...)`, `put(int, short, int, int)`, `put(int, MetaObject)`, `handleReceived(...)`, `doSwitchSlotsOrCombine(...)`, `deserialize(...)`, `spawnInSpace(...)`, `sendInventoryModification(...)`, `removeSlot(...)`, `removeMetaItem(...)`.
   - Why: central authority for most inventory mutations; intercepting here covers the majority of add/remove flows.

2. `src/main/java/org/schema/game/common/data/player/inventory/InventorySlot.java`
   - Key methods: `inc(int)`, `mergeMulti(...)`, `setMulti(...)`, `splitMulti(...)`, `setCount(...)`.
   - Why: low-level per-slot changes (multi-slot flows) may bypass Inventory-level helpers.

3. Network & packet flow
   - `src/main/java/org/schema/game/network/objects/remote/RemoteInventoryMultMod.java`
   - `src/main/java/org/schema/game/common/data/player/inventory/InventoryMultMod.java`
   - `src/main/java/org/schema/game/common/data/SendableGameState.java` / `AbstractOwnerState.java` / `PlayerState.java` (buffer enqueue/recv)
   - Why: networked updates arrive as InventoryMultMod / RemoteInventoryMultMod; server application happens through `Inventory.handleReceived` but also ensure outgoing messages don't bypass your mod.

4. World spawn / pickup
   - `src/main/java/org/schema/game/common/data/player/PlayerState.java` (drop/pickup)
   - `RemoteSector.addItem(...)` call sites (e.g. `GameServerController`, `Inventory.spawnVolumeInSpace`)
   - Why: item spawn in world and pickup flows can add inventory items indirectly; intercept to route items into logistics.

5. Convenience & wrappers
   - `src/main/java/api/utils/game/inventory/InventoryUtils.java` (`addItem`, `addElementById`) — used across codebase.
   - Why: small wrappers used by some controllers; easier interception point in some cases.

6. Admin / Shop / Controllers / NPC automation
   - `src/main/java/org/schema/game/server/data/admin/AdminCommandQueueElement.java` (admin give)
   - `src/main/java/org/schema/game/common/controller/ShopSpaceStation.java` (buy/sell flows)
   - NPC processors & server-side automation classes (search for `sendInventoryModification` occurrences in server code)
   - Why: they may call inventory mutation helpers or spawn items directly; watch for bypasses.

7. Persistence / save & load
   - `Inventory.deserialize`, `Inventory.fromTagStructure`, and `SavedInventory.java`
   - Why: saved/load and tag deserialization can re-create inventory slots without going through some high-level helpers.

---

## Suggested Mixin join-points (exact methods to intercept)

Intercept these method signatures (server authoritative where applicable):

- `public void inc(int slot, short type, int count)` — Inventory
- `public InventorySlot put(int slot, short type, int count, int meta)` — Inventory
- `public void put(int slot, MetaObject metaObject)` — Inventory (meta objects)
- `public void handleReceived(InventoryMultMod a, NetworkInventoryInterface inventoryInterface)` — Inventory
- `public void deserialize(DataInput buffer)` / `public InventorySlot deserializeSlot(DataInput buffer)` — Inventory
- `public void doSwitchSlotsOrCombine(...)` — Inventory (moves & merges)
- `public void removeSlot(int slot, boolean send)` and `public void removeMetaItem(MetaObject object)` — Inventory
- `public void inc(int by)` — InventorySlot (slot-level increment)
- `RemoteSector.addItem(Vector3f pos, short type, int meta, int count)` — world item creation (hook spawn)
- `InventoryUtils.addItem(Inventory inventory, short id, int amount)` — wrapper convenience
- `Inventory.sendInventoryModification(...)` & `InventoryHolder.sendInventoryModification(...)` — notification paths (network notify)

Notes on injecting:
- Prefer server-side interception for authoritative changes. The client-side methods often only enqueue requests; server application is what changes game state.
- For safety, intercept both the high-level helper (inc/put) and network application hooks (handleReceived). This avoids missed cases where a network packet constructs InventorySlot objects and inserts them directly.

---

## Temporary instrumentation (logging) hooks — minimal set

Add temporary mixins which log a compact line (include caller, inventory holder, slot, type, count, and a short stack-frame) for each of these methods:

- `Inventory.inc(...)`
- `Inventory.put(..., MetaObject)`
- `Inventory.put(..., short, int, int)`
- `Inventory.handleReceived(...)` (log slot list + parameter)
- `Inventory.deserialize(...)` / `deserializeSlot(...)`
- `Inventory.doSwitchSlotsOrCombine(...)` (source/dest/subslot/count)
- `Inventory.removeSlot(...)` and `removeMetaItem(...)`
- `InventorySlot.inc(...)` (for multi-slot flows)
- `RemoteSector.addItem(...)` (world spawns)

Logging format suggestion:
```
[INV-HOOK] timestamp thread holder method args -> result (stack[1])
```
Keep logs short and unique; run tests to ensure each scenario emits at least one line.

---

## Small targeted tests (run these to validate coverage)

1. Pickup floating item in world (client & server): expect `Inventory.inc` or `Inventory.put` from `PlayerState` pickup handler.
2. Move item inside inventory (drag/drop, combine stacks): expect `doSwitchSlotsOrCombine` and `inc` logs.
3. Buy from a shop (`ShoppingAddOn.sell/buy` flows): expect `decreaseBatch`/`incExistingOrNextFreeSlot` logs and `sendInventoryModification`.
4. Craft a recipe that consumes ingredients: expect `decreaseBatch` and `incExistingOrNextFreeSlot` for output.
5. Admin `give` or creative-mode add: expect `put` or `inc` (watch `CreativeModeInventory` overrides).
6. Save and load or load chunk with inventories: expect `deserialize`/`fromTagStructure` logs.
7. Remote client update: cause another client to change an inventory and ensure server calls `handleReceived`.

For each scenario, confirm the temporary logs appear; if any scenario produces no log, trace the callers and add more hooks accordingly.

---

## Edge cases & pitfalls (must handle)

- Multi-slot / grouped inventory items (InventorySlot.MULTI_SLOT, `mergeMulti`, `setMulti`, `splitMulti`): these can mutate slot internals without always calling high-level `put`.
- Meta objects (type < 0): inserted via `put(slot, MetaObject)` and deserialization; mod event `InventoryPutMetaItemEvent` is already fired here.
- Client-side prediction vs server authority: client `switchSlotsOrCombineClient` enqueues requests; server `handleReceived` / `doSwitchSlotsOrCombine` applies authoritative changes — intercept both sides carefully.
- Bulk operations: `decreaseBatch` and `decreaseBatchIgnoreAmount` perform batched removals; intercept to avoid missed removals.
- Serialization / save/load: `fromTagStructure`, `deserialize`, and saved inventory code create slots directly.
- Direct `inventoryMap.put/remove` uses in `Inventory` (deserialize, handleReceived, doSwitchSlotsOrCombine) — these bypass some higher-level helpers.
- Infinite / creative inventories: `isInfinite()` flows and `CreativeModeInventory` overrides may bypass normal capacity checks.
- Custom per-slot JSON metadata: `InventorySlot.customData` prevents silent merges; ensure any merging logic preserves or respects it.
- Concurrency: many methods synchronize on `inventoryMap` or use request queues — avoid long-blocking operations in hooks; queue work if necessary.

---

## Suggested implementation approach (concrete)

1. Implement temporary logging mixins for the method list above (server-side for authoritative). Verify logs with the targeted tests.
2. Replace logging mixins by canceling the default mutation (if mixin injection supports cancellation) and emit a logistics event (e.g., `InventoryMutationEvent` with full context).
3. Implement the logistics consumer that listens for `InventoryMutationEvent` and decides whether to accept items into belts/pipes or fall back to original behavior (by re-invoking the original logic or sending a confirmation back to the inventory hook).
4. Add unit/smoke tests for the small targeted scenarios (where possible) and an integration test involving a player picking up and routing items into a conveyor.

---

## Next steps I can do for you

- Create test mixins that log the chosen hooks (I can implement them and run a build). — tell me "Make mixins".
- Produce exact Mixin annotation templates (@Mixin, @Inject, method descriptors) for each target method. — tell me "Give Mixin signatures".
- Produce an enumerated file+line-range list of the top ~20 concrete mutation sites for direct editing. — tell me "List files".

---

## Appendix — exact code locations (quick references found)

- `src/main/java/org/schema/game/common/data/player/inventory/Inventory.java` — core; notable methods appear in this file (inc/put/handleReceived/doSwitchSlotsOrCombine/deserialize/etc.).
  - inc: near the method header `public void inc(int slot, short type, int count)` (around lines ~1030–1070 in the repo copy scanned).
  - put: `public InventorySlot put(int slot, short type, int count, int meta)` (around lines ~1420–1490).
  - handleReceived: `public void handleReceived(InventoryMultMod a, NetworkInventoryInterface inventoryInterface)` (around lines ~930–1010).
  - doSwitchSlotsOrCombine: present around lines ~1600–2100.
  - deserialize / deserializeSlot: around lines ~520–620.
  - spawnVolumeInSpace / spawnInSpace: around lines ~1640–1680.

- `src/main/java/org/schema/game/common/data/player/inventory/InventorySlot.java` — slot-level operations (`inc`, `mergeMulti`, `setMulti`, `splitMulti`), see top-level methods.

- `src/main/java/org/schema/game/network/objects/remote/RemoteInventoryMultMod.java` — serializes/deserializes multiplayer inventory updates and writes InventorySlot structures into the network stream.

- `src/main/java/api/utils/game/inventory/InventoryUtils.java` — small helper wrappers that call `incExistingOrNextFreeSlot` and then `sendInventoryModification`.

- World spawn calls found in:
  - `src/main/java/org/schema/game/server/controller/GameServerController.java` — server-side spawn uses `sector.getRemoteSector().addItem(...)`.
  - `src/main/java/org/schema/game/common/data/player/PlayerState.java` — player pickup/drop references remote sector `addItem`.

- Admin and automation:
  - `src/main/java/org/schema/game/server/data/admin/AdminCommandQueueElement.java` — admin give uses `addItem` in some code paths.
  - NPC/server automation code references `Inventory.sendInventoryModification` in multiple locations (search for occurrences).

---

If you want, I will now create the temporary logging mixins in the workspace and run a quick build to validate they compile and to provide ready-to-run mixin templates. Which action should I take next?

