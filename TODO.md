# Resources Reorganized — Implementation Plan

**Note:** This project was renamed from *ResourcesRefueled* to *Resources Reorganized* to reflect a broader goal of
improving general item and fluid logistics across the game (bulk storage, portable containers, pipe networks, and
fluid-aware systems). The roadmap below has been updated to reflect the current state and remaining work.

## Checklist

### 1. ✅ Register Heliogen items & blocks

Register all new elements via `BlockConfig` in `ElementRegistry`, called from `ResourcesReorganized.onBlockConfigLoad`.

- ✅ `HeliogenPlasma` — raw unplaceable item
- ✅ `FluidCanister` (single generic item with metadata) — portable fluid container
- ✅ `HeliogenCondenser` — factory-type station block (`BlockConfig.newFactory`)
- ✅ `HeliogenRefinery` — refinery block (`BlockConfig.newRefinery`), Plasma → Canister recipe (refinery stamps metadata)
- ✅ `HeliogenRefineryController` — computer block, wired to refinery modules
- ✅ `FluidTank` — generic pressurised fluid storage block
- ✅ `FluidPipe`, `FluidPump`, `FluidValve`, `FluidFilter` — pipe network blocks registered
- ✅ Block assembly recipes — `RecipeManager` listens for `RRSRecipeAddEvent` and calls `RRSRecipeManager.addBlock` for all blocks
- ⬜ Custom textures / icons for all elements (placeholder vanilla textures in use)

---

### 2. ✅ StellarFuelSupplier + StellarFuelSourcesContainer
Passive Heliogen supply tied to star proximity, no zone maps required.

- ✅ `StellarFuelSupplier` — per-system passive pool
- ✅ `StellarFuelSourcesContainer` — keyed by system pos
- ✅ `StellarFuelManager` — persistence + load/save

---

### 3. ✅ Extractor fuel boost

- ✅ `ExtractorFuelListener` — resolves combined fuel (networks + filled canisters)
- ✅ `HarvesterEnhancerOverrideListener` — applies fuel-based strength override
- ✅ `FuelTickState` — per-tick shared state

---

### 4. ✅ FTL fuel consumption

- ✅ `ShipJumpFuelListener` — uses `EntityFuelManager` virtual cache, drains tanks then canisters

---

### 5. ✅ Solar Condenser production

- ✅ `HeliogenCondenser` and `SolarCondenserTickListener` — proximity-scaled bonus production

---

### 6. ✅ Tank explosion on destruction (volatile-only)

- ✅ Explosion logic now gated on fluid volatility (`FluidMeta.isVolatile`).
- ✅ `HeliogenPlasma` registers itself as volatile.

---

### 7. ✅ Config keys

- ✅ All previously planned config keys added; `capacity_per_tank` implemented.

---

### 8. ✅ Fluid pipe-network refactor

- ✅ `tankSegments` / `pipeSegments` maps implemented
- ✅ `FluidNetwork` per-network fluidId, fluidLevel, tankCapacity
- ✅ Placement/removal topology logic implemented
- ✅ Serialisation / deserialisation implemented

---

### 9. ⬜ Fluid fill visualisation (client)

- ⬜ In-world fill overlay and shader — planned

---

### 10. ⬜ CTM for Fluid Tanks

- ⬜ Connected textures integration (client) — planned

---