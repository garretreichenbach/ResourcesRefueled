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
- ⬜ Block assembly recipes for all blocks (need adding in `postInitData` / a recipe manager)
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

### 3. ✅ Extractor fuel consumption mixin
Intercepts RRS's `ExtractorTickFastListener.onPreManufacture`.

- ✅ `MixinExtractorTickListener` — `@Inject` at `HEAD`, checks inventory for filled canisters, consumes them and returns empties as the "recipe" side of the fuel loop
- ✅ `FuelTickState` — external holder for the per-tick unfueled flag (required because Mixin disallows non-private static members)
- ✅ `HarvesterFuelEfficiencyListener` — reads `FuelTickState`, scales extraction power by `unfueled_extraction_efficiency` via `HarvesterStrengthUpdateEvent`
- ✅ Registered in `resourcesrefueled.mixins.json`
- ⬜ Void-system short-circuit (currently marks unfueled but does not cancel extraction outright — consider whether void systems should block entirely)

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

### 5. 🟡 Solar Condenser production
Star-proximity-boosted Anbaric + Parsyne → Heliogen Plasma conversion.

- ✅ `HeliogenCondenser` block registered with `BlockConfig.newFactory`
- ✅ Recipe stub in `HeliogenCondenser.postInitData` (Anbaric Vapor + Parsyne Plasma → Heliogen Plasma)
- ⬜ `SolarCondenserTickListener` — `FactoryManufactureListener` that multiplies output by `SystemSheet.getTemperature(factoryEntity.getSector(...))` at runtime
- ⬜ Register listener in `EventManager` via `FastListenerCommon`

---

### 6. 🟡 Tank explosion on destruction
- ✅ `SegmentPieceKillEvent` — checks `ElementRegistry.isFluidTank`, reads `FluidTankSystemModule` fuel level, spawns `ModuleExplosion` list scaled to fluid level and tank capacity
- ✅ `FluidTankSystemModule` — `SystemModule` subclass with `TankSystemData` (fluid type, capacity, level), full serialization
- ✅ `FluidTankSystemModule.getBlockIndices()` / `getBoundingBox()` — used for explosion origin and radius calculations

---

### 7. 🟡 Config keys
- ✅ `fuel_cost_per_strength_unit` (0.5)
- ✅ `unfueled_extraction_efficiency` (0.3)
- ✅ `ftl_fuel_per_sector` (1.0)
- ✅ `fuel_per_canister` (100.0)
- ✅ `ftl_unfueled_cooldown_multiplier` (3.0) — wired in config, not yet applied in code
- ✅ `tank_explosion_yield_per_unit` — superseded by `fluid_level_per_explosion` / `max_fluid_explosion_radius` / `fluid_explosion_damage` used in kill listener
- ✅ `fluid_level_per_explosion`, `max_fluid_explosion_radius`, `fluid_explosion_damage` — added to config defaults

---

## Architecture Notes

- **Fluid tank system** — `FluidTank` block is generic; future fluids (e.g. coolant, fuel oil) just need a new `ElementRegistry` entry with a different `fluidIdSupplier`. Texture system planned for later (supplier pattern already supports per-fluid dynamic textures).
- **Pipe network** — `FluidPipe`, `FluidPump`, `FluidValve`, `FluidFilter` registered as blocks; actual fluid transport logic is a future milestone.
- **Persistence** — `StellarFuelManager` mirrors RRS's `loadExtractorData`/`saveExtractorData` exactly. Load on `ServerInitializeEvent`, save on `WorldSaveEvent` and `onDisable`. Lazy supplier init means no galaxy-gen hook needed.
- **`onDisable` save** — `StellarFuelManager.saveFuelData()` needs hooking into `ResourcesRefueled.onDisable()`.
