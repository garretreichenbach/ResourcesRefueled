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

- ✅ `ExtractorFuelListener` — plain `FactoryManufactureListener` (no mixin); `onPreManufacture` resolves combined fuel from `FluidTankSystemModule` + inventory canisters into `FuelTickState.availableFuelUnits`; `onProduceItem` drains tanks first then canisters, returns empties, adds bonus output proportional to fuel spent
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

### 9. ⬜ Fluid fill visualisation (`FluidTankWorldDrawer`)

**Goal:** Render a semi-transparent fluid fill overlay inside each Fluid Tank block in world space, scaled to the
network's current fill fraction. The drawer is client-only and reads fill data from a lightweight client-side mirror of
`FluidTankSystemModule` state synced via the existing `onTagSerialize`/`onTagDeserialize` path.

---

#### 9.1 — Hook point

- ⬜ Identify the correct StarMade draw-loop hook for per-entity world-space overlays.
  Candidates: `GameClientState.getWorldDrawer()`, `DrawableScene`, or a registered `StarRunnable` that fires on the
  render thread. Check RRS's `GasPlanetMapDrawer` for how it hooks into the client render pass — but note that
  `MapDrawer` is galaxy-map only; we need the **in-world** equivalent.
- ⬜ Register a `FluidTankWorldDrawer` on `ClientInitializeEvent` (already stubbed in `ResourcesRefueled.onClientCreated`).
  Gate registration on `!GameCommon.isDedicatedServer()`.

#### 9.2 — Per-block fill quad

- ⬜ For each block index in `tankModule.tankSegments`, decode the world position via
  `ElementCollection.getPosFromIndex` + the entity's world transform.
- ⬜ Render a single axis-aligned quad (or six face quads forming a box) at the block's world position, scaled on the
  Y axis by `network.fluidLevel / network.tankCapacity` (0 = empty, 1 = full block height).
- ⬜ Use an additive or alpha-blended material coloured by `fluidId` (e.g. deep orange-gold for Heliogen). Colour table
  lives in a `FluidVisualRegistry` keyed by `short fluidId`.
- ⬜ Clip the fill quad to the block's bounding cube so it never visually overflows into adjacent blocks.

#### 9.3 — Shader

- ⬜ Write `fluid_fill.vert` / `fluid_fill.frag` shaders (follow RRS's `gasgiant_interior.vert/frag` as a template for
  how to load and bind custom shaders via `ResourceLoader`).
- ⬜ Uniforms: `fillFraction` (float), `fluidColor` (vec4), `time` (float for animated surface shimmer).
- ⬜ Fragment shader: flat fill below `fillFraction`, transparent above; optional animated sine-wave surface line for a
  sloshing effect.
- ⬜ Load shaders in `ResourceManager.loadResources()` (called from `onResourceLoad`), guard on
  `!GameCommon.isDedicatedServer()`.

#### 9.4 — Data sync

- ⬜ `FluidTankSystemModule.onTagSerialize` already writes network fluid levels to the packet buffer and is synced
  to the client by the engine whenever `flagUpdatedData()` is called. No additional network code is needed — the
  client-side module instance already has current fill data.
- ⬜ The drawer reads `tankModule.getNetworks()` directly on the render thread. Since `onTagDeserialize` is called on
  the client game thread (not the render thread), add a `volatile double[] fillFractions` snapshot field to
  `FluidTankSystemModule` that is written on deserialise and read by the drawer without locking.

#### 9.5 — `FluidVisualRegistry`

- ⬜ Static `HashMap<Short, FluidVisual>` keyed by `fluidId`. `FluidVisual` holds: `Vector4f color`,
  `float emissivity`, `String shaderVariant` (allows future fluids to use different shaders — e.g. a cryo fluid
  could use a frosted-glass variant).
- ⬜ Register Heliogen entry: warm amber `(1.0, 0.6, 0.1, 0.65)`, medium emissivity, default variant.
- ⬜ Populated in `ResourceManager.loadResources()`.

---

### 10. ⬜ Fluid Tank connected textures (CTM via native StarMade API)

**Goal:** Fluid Tank blocks automatically pick the correct face texture based on which adjacent faces are connected to
another Fluid Tank (or pipe inlet). Uses StarMade's built-in **Connected Texture Method** API
(`ConnectedTextureUnit` / `CTMTextureRegion` / `StarLoaderTexture`) — no custom rendering engine needed.

Pipe blocks (`FluidPipe`, `FluidPump`, `FluidValve`, `FluidFilter`) are **3D mesh blocks** and get their visual
connectivity from model variants, not CTM. This section covers the tank block only.

---

#### 10.1 — Sprite sheet (generated, not hand-painted)

The tile sheet is produced programmatically by `FluidTankCTMGenerator` (run via `./gradlew generateCTM`).

**Phase 1 — SIMPLE_16 (current target):** single row, 16 cardinal-only tiles.
Output: `fluid_tank_ctm16.png` (4096 × 256 px). Only **5 source images** needed:

| File | Purpose |
|---|---|
| `base.png` | Full interior fill drawn on every tile |
| `edge_top.png` | Border strip along the full top side — applied on any **sealed** top edge |
| `edge_right.png` | Border strip along the full right side |
| `edge_bottom.png` | Border strip along the full bottom side |
| `edge_left.png` | Border strip along the full left side |

Place all five in `src/main/resources/textures/fluid_tank/src/`.
Two edge strips crossing at a corner naturally form the outer corner — no extra art needed.

**Phase 2 — FULL_47 (future):** upgrade to 3-row 47-tile sheet once SIMPLE_16 is confirmed
working in-game. Adds 4 inner-corner overlay images for seamless diagonal joins.
Output will be `fluid_tank_ctm47.png` (4096 × 768 px).
- ⬜ (Optional) Matching normal-map sheet `fluid_tank_ctm47_nrm.tga`.

#### 10.2 — Registration in `onEnable` / `onBlockConfigLoad`

- ⬜ In `ResourceManager.loadResources()`, load the sprite sheet and call:
  ```java
  tankRegion = StarLoaderTexture.newCTMRegionFromSheet(sheet, null,
      CTMTextureRegion.VARIANTS_16, "FluidTankCTM");
  ```
  (`VARIANTS_16` for now — swap to `VARIANTS_47` once the full sheet is ready.)
  Store `tankRegion` as a static field; the block ID is not yet available at this point.
- ⬜ In `FluidTank.initData()`, use `tankRegion.baseIndex` as the default texture index instead of `0`:
  ```java
  blockInfo = BlockConfig.newElement(..., new short[]{ ResourceManager.getTankRegion().baseIndex, ... });
  blockInfo.extendedTexture = true;
  ```
- ⬜ After `BlockConfig.add(tank)` resolves the block ID, build the helper:
  ```java
  tankHelper = tankRegion.buildHelper(ElementRegistry.FLUID_TANK.getId());
  ```
  Tank should also visually connect to all pipe block types:
  ```java
  tankHelper.connectsTo(ElementRegistry.FLUID_PIPE.getId());
  tankHelper.connectsTo(ElementRegistry.FLUID_PUMP.getId());
  tankHelper.connectsTo(ElementRegistry.FLUID_VALVE.getId());
  tankHelper.connectsTo(ElementRegistry.FLUID_FILTER.getId());
  ```
  Store `tankHelper` in `ResourceManager` for access by the event handler.

#### 10.3 — CTM update on placement / removal

- ⬜ In `SegmentPieceEventHandler.onAdd`, after `module.onPlace(...)`, call (deferred 1 tick via `StarRunnable`):
  ```java
  ResourceManager.getTankCTMHelper().updatePieceAndNeighbors(piece);
  ```
- ⬜ In `SegmentPieceEventHandler.onBlockRemove`, after `module.onRemove(...)`, call (deferred 1 tick):
  ```java
  ResourceManager.getTankCTMHelper().updatePieceAndNeighbors(piece);
  ```
  `updatePieceAndNeighbors` is null-safe; no extra null check required.
- ⬜ Guard both call sites on `!GameCommon.isDedicatedServer()` — CTM is client-only.

#### 10.4 — `ResourceManager` wiring

- ⬜ Add `private static CTMTextureRegion tankRegion` and `private static ConnectedTextureUnit tankCTMHelper` fields.
- ⬜ `loadResources()` is already called from `onResourceLoad`; move sprite-sheet loading here (it's the correct
  client-only resource hook) rather than `onEnable`.
- ⬜ Expose `getTankCTMHelper()` as a static accessor for use in `SegmentPieceEventHandler`.

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
  is not loaded. `syncFromLive` snapshots the entity; `writeBackToLive` flushes dirty state back using a tank-level
  delta (`snapshotTankFluidLevel − tankFluidLevel`) passed to `tankModule.drain()`, plus a canister delta from
  `snapshotCanisterCount`.
- **Client rendering** — §9 (`FluidTankWorldDrawer`) and §10 (tank CTM) are both client-only.
  §9 reads fill data from the client-side `FluidTankSystemModule` which is kept in sync by the engine's existing
  module serialisation path. §10 uses StarMade's native `ConnectedTextureUnit` API; `SegmentPieceEventHandler` calls
  `tankHelper.updatePieceAndNeighbors(piece)` (deferred 1 tick) on every tank or pipe placement/removal to refresh
  the tile variant. Pipe blocks are 3D mesh blocks and do not use CTM. The two systems are independent — §9 can ship
  without §10.
