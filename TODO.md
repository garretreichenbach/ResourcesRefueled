# ResourcesRefueled — Implementation Plan

## Checklist

### 1. 🟡 Register Heliogen items & blocks
Register all new elements via `BlockConfig` in `ElementRegistry`, called from `ResourcesRefueled.onBlockConfigLoad`.

- ✅ `HeliogenPlasma` — raw unplaceable item
- ✅ `HeliogenCanisterEmpty` / `HeliogenCanisterFilled` — portable fuel items
- ✅ `HeliogenCondenser` — factory-type station block (`BlockConfig.newFactory`)
- ✅ `HeliogenRefinery` — refinery block (`BlockConfig.newRefinery`), Plasma → Canister recipe
- ✅ `HeliogenRefineryController` — computer block, wired to refinery modules
- ✅ `FluidTank` — generic pressurised fluid storage block (replaces `HeliogenTank`); Heliogen instance registered as `HELIOGEN_TANK` in `ElementRegistry`
- ✅ `FluidPipe`, `FluidPump`, `FluidValve`, `FluidFilter` — pipe network blocks registered
- ✅ Block assembly recipes — `RecipeManager` listens for `RRSRecipeAddEvent` and calls `RRSRecipeManager.addBlock` for all blocks
- ⬜ Custom textures / icons for all elements (placeholder vanilla textures in use)
- ✅ `ElementRegistry.isFluidTank()` helper — implemented

---

### 2. ✅ StellarFuelSupplier + StellarFuelSourcesContainer
Passive Heliogen supply tied to star proximity, no zone maps required.

- ✅ `StellarFuelSupplier` — per-system passive pool, regen rate derived from `SystemClass`, lazy `updatePassivePool` with timestamp tracking
- ✅ `StellarFuelSourcesContainer` — `HashMap<Vector3i, StellarFuelSupplier>` keyed by system pos, lazy `getOrCreate` via `ResourcesReSourced.getSystemSheet`
- ✅ `StellarFuelManager` — owns static container, `loadFuelData` / `saveFuelData` via `PersistentObjectUtil`, mirrors RRS persistence pattern exactly
- ✅ `StellarFuelSupplier.getBaseRegenForClass` — yield table covering all `SystemClass` values; void systems return 0
- ✅ `StellarFuelSourcesContainer.systemPosFromSector` — `VoidSystem.getPosFromSector` wrapper

---

### 3. ✅ Extractor fuel boost (mixin + enhancer override)
Intercepts RRS's `ExtractorTickFastListener.onProduceItem` and `HarvesterStrengthUpdateEvent`.

- ✅ `MixinExtractorTickListener` — `onPreManufacture` resolves combined fuel from `FluidTankSystemModule` + inventory canisters into `FuelTickState.availableFuelUnits`; `onProduceItem` drains tanks first then canisters, returns empties, adds bonus output proportional to fuel spent
- ✅ `HarvesterEnhancerOverrideListener` — listens for `HarvesterStrengthUpdateEvent`; strips vanilla enhancer bonus, replaces it with a fuel-fraction interpolation between base rate and the enhancer ceiling. Zero inventory access — reads only from `FuelTickState`. Records spent fuel in `FuelTickState.spentFuelUnits` to prevent double-consumption
- ✅ `FuelTickState` — holds `availableFuelUnits` (Double, combined tank+canister) and `spentFuelUnits` (Double, reserved by strength listener) per entity UID
- ✅ `ElementRegistry.doOverwrites()` — called from `onBlockConfigLoad`; removes `FACTORY_ENHANCER` from `controlling`/`controlledBy` on Vapor Siphon and Magmatic Extractor so enhancers can no longer be physically connected to extractors
- ✅ Registered in `resourcesrefueled.mixins.json`

---

### 4. ✅ FTL fuel consumption
Hooks `ShipJumpEngageEvent`, cancels the vanilla jump, executes a custom `SectorSwitch`.

- ✅ `ShipJumpFuelListener` — standalone `Listener` class, registered in `EventManager`
- ✅ Tank-first, inventory-canister fallback priority order
- ✅ Partial tank drain + inventory top-up logic
- ✅ `calcShortJumpTarget` — proportional redirect toward target when underfueled; guarantees ≥1 sector of progress, never overshoots
- ✅ `countInventoryCanisters` — counts across all ship inventories
- ✅ `handleJump` — cancels event, queues `SectorSwitch` with graphics effect
- ⬜ FTL cooldown penalty for underfueled jumps (TODO in code — `ShipJumpEngageEvent` cooldown API method not yet confirmed)

---

### 5. ✅ Solar Condenser production
Star-proximity-boosted Anbaric + Parsyne → Heliogen Plasma conversion.

- ✅ `HeliogenCondenser` block registered with `BlockConfig.newFactory`
- ✅ Recipe stub in `HeliogenCondenser.postInitData` (Anbaric Vapor + Parsyne Plasma → Heliogen Plasma)
- ✅ `SolarCondenserTickListener` — `FactoryManufactureListener`; `onPreManufacture` blocks void systems entirely, `onProduceItem` adds bonus plasma scaled by `SystemSheet.getTemperature` × star class multiplier × `condenser_base_output` config
- ✅ Register listener in `EventManager` via `FastListenerCommon.factoryManufactureListeners`

---

### 6. 🟡 Tank explosion on destruction
- ✅ `SegmentPieceKillEvent` — checks `ElementRegistry.isFluidTank`, reads `FluidTankSystemModule` fuel level, spawns `ModuleExplosion` list scaled to fluid level and tank capacity
- ✅ `FluidTankSystemModule` — `SystemModule` subclass with fluid type, capacity, level fields; full serialisation
- ✅ `FluidTankSystemModule.getBlockIndices()` / `getBoundingBox()` — used for explosion origin and radius calculations
- ⬜ Update `getBlockIndices()` / `getBoundingBox()` to read from the new `tankSegments` map (see Section 8) rather than
  the inherited `blocks` array once that refactor lands

---

### 7. 🟡 Config keys
- ✅ `fuel_cost_per_strength_unit` (0.5)
- ✅ `fueled_extraction_bonus` (0.5) — bonus output fraction per canister consumed
- ✅ `condenser_base_output` (4) — bonus plasma per cycle at proximity 1.0 next to a normal star
- ✅ `condenser_proximity_scale` (true) — if false, proximity is treated as 1.0 (star class bonus only)
- ✅ `ftl_fuel_per_sector` (1.0)
- ✅ `fuel_per_canister` (100.0)
- ✅ `ftl_unfueled_cooldown_multiplier` (3.0) — wired in config, not yet applied in code
- ✅ `fluid_level_per_explosion`, `max_fluid_explosion_radius`, `fluid_explosion_damage` — added to config defaults
- ⬜ `fluid_tank_capacity_per_block` — capacity contributed by each placed tank block (needed for Section 8)

---

### 8. ⬜ Fluid pipe-network refactor (`FluidTankSystemModule`)

**Goal:** Replace the inherited `blocks` array in `FluidTankSystemModule` with two explicit custom maps — one for tank
multiblock segments and one for pipe-chain segments — so that the module owns the full network topology, capacity is
computed structurally from the tank-block count, and `onBlockPlaced`/`onBlockRemoved` drive all topology updates. The
inherited `blocks` map is neither read nor written by this module after the refactor.

---

#### 8.1 — Segment value types

- ⬜ Define `FluidTankSegment` (inner class or package-private class in `systems/`) — holds `long blockIndex` and
  `short blockType`; represents one placed `FLUID_TANK` block that contributes capacity to the network.
- ⬜ Define `FluidPipeSegment` (inner class or package-private class in `systems/`) — holds `long blockIndex` and
  `short blockType`; represents one placed pipe-network block (`FLUID_PIPE`, `FLUID_PUMP`, `FLUID_VALVE`,
  `FLUID_FILTER`).

#### 8.2 — Module map fields

- ⬜ Add `HashMap<Long, FluidTankSegment> tankSegments` to `FluidTankSystemModule`, keyed by block index. One entry per
  placed tank block on the entity.
- ⬜ Add `HashMap<Long, FluidPipeSegment> pipeSegments` to `FluidTankSystemModule`, keyed by block index. One entry per
  placed pipe-network block.
- ⬜ Stop populating or reading the inherited `blocks` map for any fluid-network block type; replace all existing
  `blocks.keySet()` calls with reads from `tankSegments` or `pipeSegments`.

#### 8.3 — Structural capacity

- ⬜ Add `float capacityPerTankBlock` field, initialised from `ConfigManager.getFluidTankCapacityPerBlock()`.
- ⬜ Add `recalculateCapacity()` — sets `tankCapacity = tankSegments.size() * capacityPerTankBlock`; clamps
  `currentFluidLevel` to the new capacity so level never exceeds capacity after tank blocks are removed; calls
  `flagUpdatedData()`.
- ⬜ Remove `setTankCapacity(double)` — capacity is now structural and read-only from the outside. Keep the
  `getTankCapacity()` getter unchanged.

#### 8.4 — Placement and removal hooks

- ⬜ Override `onBlockPlaced(long blockIndex, short blockType)` (confirm exact `SystemModule` API method signature):
  - `FLUID_TANK` id → put new `FluidTankSegment(blockIndex, blockType)` into `tankSegments`; call
    `recalculateCapacity()`.
  - Any pipe id (`FLUID_PIPE`, `FLUID_PUMP`, `FLUID_VALVE`, `FLUID_FILTER`) → put new
    `FluidPipeSegment(blockIndex, blockType)` into `pipeSegments`; call `flagUpdatedData()`.
  - Do **not** call `super.onBlockPlaced()` for these types to prevent populating the inherited `blocks` map.
- ⬜ Override `onBlockRemoved(long blockIndex, short blockType)`:
  - If `tankSegments.containsKey(blockIndex)` → remove entry; call `recalculateCapacity()`.
  - If `pipeSegments.containsKey(blockIndex)` → remove entry; call `flagUpdatedData()`.
  - Do **not** call `super.onBlockRemoved()` for these types.

#### 8.5 — Serialisation

- ⬜ Extend `onTagSerialize` to write both maps: write count as `int`, then per-entry `writeLong(blockIndex)` +
  `writeShort(blockType)` for `tankSegments`, then repeat for `pipeSegments`.
- ⬜ Extend `onTagDeserialize` to read both maps back and repopulate `tankSegments` and `pipeSegments`; call
  `recalculateCapacity()` at the end of deserialisation.
- ⬜ Remove the old `tankCapacity` read/write from serialisation — capacity is now always derived from
  `tankSegments.size()`.
- ⬜ Keep `fluidId` and `currentFluidLevel` serialisation unchanged.

#### 8.6 — Update callers

- ⬜ `FluidTankSystemModule.getBlockIndices()` — rewrite to return a `LongArrayList` built from `tankSegments.keySet()` (
  tank blocks only; this is what explosion scatter uses).
- ⬜ `FluidTankSystemModule.getBoundingBox()` — rewrite bounding-box computation to iterate over `tankSegments.keySet()`
  via `ElementCollection.getPosFromIndex`.
- ⬜ `SegmentPieceKillEvent.createExplosionList` — no change needed at the call site; `getBlockIndices()` and
  `getBoundingBox()` already provide the right data after 8.6 above.
- ⬜ `EntityFuelManager.syncFromLive` — no change needed; it reads `tankModule.getCurrentFluidLevel()` /
  `getTankCapacity()`, both still valid.

#### 8.7 — Config addition

- ⬜ Add `fluid_tank_capacity_per_block: 500.0` to the `defaultMainConfig` array in `ConfigManager`.
- ⬜ Add `ConfigManager.getFluidTankCapacityPerBlock()` accessor (parse as `double`, default `500.0`).

---

#### 8.8 — Pipe transport logic (future sub-milestone, non-blocking)

> Not required for capacity accounting, explosion, or fuel-drain to function. Planned as the next milestone after
> 8.1–8.7 are complete.

- ⬜ Define connectivity rules — a pipe segment is connected to an adjacent block if they share a face and both block
  types belong to the same fluid-network set (`tankSegments` ∪ `pipeSegments`).
- ⬜ Flood-fill on `onBlockPlaced` / `onBlockRemoved` to keep `pipeSegments` in sync with the live connected subgraph (
  needed if we want to support disconnected sub-networks on the same entity).
- ⬜ `FluidPump` directional flow — pumps have a configured input face and output face; fluid moves from the input-side
  sub-network to the output-side sub-network at a rate per tick in `handle(Timer)`.
- ⬜ `FluidValve` open/close state — toggled by player activation; closed valve breaks connectivity at that block in the
  flood-fill graph.
- ⬜ `FluidFilter` — passes only fluids whose `fluidId` matches a per-block configured whitelist.

---

## Architecture Notes

- **Fluid tank system** — `FluidTank` block is generic; future fluids just need a new `ElementRegistry` entry. After
  section 8, `tankSegments` is the capacity table and `pipeSegments` is the routing table; no separate manager class is
  needed at this stage.
- **Pipe network** — After section 8 lands, placement/removal hooks own all topology changes. The inherited `blocks` map
  is bypassed entirely for fluid-network blocks.
- **Persistence** — `StellarFuelManager` and `EntityFuelManager` both mirror RRS's `PersistentObjectUtil` pattern. Load
  on `ServerInitializeEvent`, save on `WorldSaveEvent` and `onDisable`.
- **Virtualised entity fuel cache** — `EntityFuelManager` / `EntityFuelCache` / `EntityFuelCacheContainer` provide a
  persistent virtual view of each entity's tank + canister state so that fuel reads and drains work even when the entity
  is not loaded. `syncFromLive` snapshots the entity; `writeBackToLive` flushes dirty state back using a canister delta
  derived from `snapshotCanisterCount`.
