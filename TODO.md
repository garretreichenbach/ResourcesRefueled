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

### 8. 🟡 Fluid pipe-network refactor (`FluidTankSystemModule`)

**Goal:** Replace the inherited `blocks` array in `FluidTankSystemModule` with two explicit custom maps — one for tank
multiblock segments and one for pipe-chain segments — so that the module owns the full network topology, capacity is
computed structurally from the tank-block count, and `onBlockPlaced`/`onBlockRemoved` drive all topology updates. The
inherited `blocks` map is neither read nor written by this module after the refactor.

---

#### 8.1 — Segment value types

- ✅ `FluidTankSegment` — `long blockIndex` + `short blockType`; one per placed `FLUID_TANK` block.
- ✅ `FluidPipeSegment` — `long blockIndex` + `short blockType`; one per placed pipe-network block.

#### 8.2 — Module map fields

- ✅ `HashMap<Long, FluidTankSegment> tankSegments` — all tank blocks on this entity.
- ✅ `HashMap<Long, FluidPipeSegment> pipeSegments` — all pipe-network blocks on this entity.
- ✅ `List<FluidNetwork> networks` — connected components; each owns its own `fluidLevel` and derived `tankCapacity`.
- ✅ Inherited `blocks` map bypassed entirely for fluid-network blocks.

#### 8.3 — Structural capacity per network

- ✅ `FluidNetwork.tankCapacity` derived from `tankCount × ConfigManager.getFluidTankCapacityPerBlock()`.
- ✅ `recalculateNetworkCapacity(FluidNetwork)` recomputes capacity and clamps `fluidLevel`.
- ✅ No public `setTankCapacity` — capacity is read-only from outside.

#### 8.4 — Placement and removal via `SegmentPieceEventHandler`

- ✅ `onPlace(index, blockType)` — public method called by `SegmentPieceEventHandler.onAdd`; adds block to appropriate map, BFS-merges all face-adjacent networks into one, recalculates capacity.
- ✅ `onRemove(index, blockType)` — public method called by `SegmentPieceEventHandler.onBlockRemove`; removes block from its network, BFS-re-partitions remaining members into new components, distributes fluid proportionally by new capacity.
- ✅ `handlePlace(long, byte)` / `handleRemove(long)` — parent `SystemModule` overrides, intentionally empty. Prevents the parent from populating the inherited `blocks` map. The `byte` type in `handlePlace` cannot represent a full `short` block ID without truncation, which is why the event handler delivers the full type via `onPlace`/`onRemove` instead.
- ✅ `SegmentPieceEventHandler.onAdd` / `onBlockRemove` / `onBlockKilled` all delegate to the module with explicit `short` block type.

#### 8.5 — Serialisation

- ✅ `onTagSerialize` — writes `fluidId`, network count, then per-network `fluidLevel` + member list (index + type per block).
- ✅ `onTagDeserialize` — rebuilds `tankSegments`, `pipeSegments`, and `networks`; calls `recalculateNetworkCapacity` after deserialisation.
- ✅ `tankCapacity` not written — always derived structurally on load.

#### 8.6 — Update callers

- ✅ `getBlockIndices()` / `getBoundingBox()` operate on `tankSegments`.
- ✅ `getBlockIndicesForExplosion(blockIndex)` / `getFluidLevelForBlock` / `getCapacityForBlock` / `getBoundingBoxForBlock` — per-network explosion helpers.
- ✅ `SegmentPieceEventHandler.createExplosionList` uses per-network helpers so only the destroyed network's fluid drives the explosion.
- ✅ `EntityFuelManager.syncFromLive` / `writeBackToLive` use `getCurrentFluidLevel()` / `setCurrentFluidLevel()` — no changes needed, they aggregate across networks transparently.

#### 8.7 — Config addition

- ✅ `fluid_tank_capacity_per_block: 500.0` added to `ConfigManager` defaults.
- ✅ `ConfigManager.getFluidTankCapacityPerBlock()` accessor added.

---

#### 8.8 — Pipe transport logic (future sub-milestone, non-blocking)

> Not required for capacity accounting, explosion, or fuel-drain to function. Planned as the next milestone after
> 8.1–8.7 are complete.

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
