# ResourcesRefueled

A StarMade addon for **[Resources ReSourced](https://starmadedock.net/content/resources-resourced.8292/)** by VideoGoose that adds a stellar fuel economy to the game. Resource extraction and FTL travel now require **Heliogen** — a volatile plasma fuel distilled directly from starlight — tying your industrial output and exploration range to your proximity to, and relationship with, the stars around you.

---

## Requirements

- [Resources ReSourced (RRS)](https://starmadedock.net/content/resources-resourced.8292/) v0.9.7+
- StarMade 0.205.1+

---

## Overview

RRS already makes resources scarce and zone-dependent. ResourcesRefueled adds a second layer: **energy**. Stars are now active participants in the economy. The closer you are willing to operate to a star — and the more exotic that star is — the more Heliogen you can produce. But stars are dangerous, and compressed plasma is volatile, so the logistics chain matters.

---

## Heliogen Production Chain

```
Star radiation
      │
      ▼
[Heliogen Condenser]  ←  Anbaric Vapor + Parsyne Plasma
  (station block,          (from RRS extraction)
   yield scales with
   star proximity)
      │
      ▼
 Heliogen Plasma
  (raw item)
      │
      ▼
[Heliogen Refinery]
      │
      ▼
 Heliogen Canister (Filled)   ──►  FTL drives
                               └►  Resource extractors
```

**Heliogen Plasma** is produced by the **Heliogen Condenser**, a station block that catalyses a reaction between Anbaric Vapor and Parsyne Plasma using ambient stellar radiation. The yield multiplier scales linearly with the station's proximity to the local star — a condenser placed three sectors from a supergiant produces far more than one orbiting a dim dwarf. Stars vary in output by type, so scouting for energetic or exotic systems is rewarded.

**Heliogen Plasma** is then refined in the **Heliogen Refinery** into portable **Heliogen Canisters**, or pumped directly into **Heliogen Tanks** for bulk storage.

---

## Fuel Consumption

### Resource Extractors
RRS's Vapor Siphons and Magmatic Extractors always run at full base efficiency — Heliogen is not required to operate them. However, if filled Heliogen Canisters are present in the extractor's linked inventory, each completed extraction cycle will consume some and produce bonus resources on top of the base output (configurable, default +50% per canister consumed). Spent canisters are returned as empties in the same inventory update, ready to be transported back to a refinery.

This makes Heliogen a worthwhile investment for established operations without punishing players who haven't built a fuel supply chain yet.

### FTL Jumps
FTL drives consume Heliogen proportional to jump distance. The fuel priority order is:
1. **Heliogen Tanks** onboard (preferred — silent, bulk)
2. **Heliogen Canisters** in ship inventory (fallback — with warning)
3. **Neither** — jump still executes, but only reaches as far as available fuel allows. The drive calculates the furthest reachable sector along the intended heading and jumps there instead.

---

## Fluid Storage & Pipe Network

Heliogen can be stored in bulk using **Heliogen Tanks** — pressurised multiblock storage units. Each tank block contributes capacity to the system. Tank blocks are volatile: destroying a loaded tank while it contains fuel triggers an explosion proportional to the stored volume. Ship builders are advised to treat tank placement with care, especially on smaller vessels.

A basic **pipe network** (Fluid Pipes, Pumps, Valves, and Filters) allows Heliogen to be routed between tanks, refineries, and consuming systems. Full pipe transport logic is planned for a future release; pipe blocks are registerable and buildable now.

---

## Crafting

All blocks are assembled in RRS's **Block Assembler** using standard RRS components:

| Block | Key Components |
|---|---|
| Heliogen Condenser | Parsyne Amplifying Focus, Anbaric Distortion Coil, Crystal Energy Focus |
| Heliogen Refinery | Thermyn Power Charge, Standard Circuitry |
| Heliogen Refinery Controller | Metal Sheets, Standard Circuitry, Crystal Panel |
| Heliogen Tank | Metal Sheets, Anbaric Distortion Coils |
| Fluid Pipe | Metal Frame, Metal Sheet |
| Fluid Pump | Metal Frame, Metal Sheet, Energy Cell, Standard Circuitry |
| Fluid Valve | Metal Frame, Metal Sheet, Standard Circuitry |
| Fluid Filter | Metal Frame, Standard Circuitry, Crystal Panel |

---

## Configuration

All values are set in `config/ResourcesRefueled/config.yml` on the server.

| Key | Default | Description                                                                        |
|---|---------|------------------------------------------------------------------------------------|
| `fuel_cost_per_strength_unit` | `0.5`   | Fuel units consumed per unit of base output per cycle                              |
| `fueled_extraction_bonus` | `0.5`   | Bonus output fraction added per unit of fuel consumed                              |
| `condenser_base_output` | `4`     | Bonus Heliogen Plasma per cycle at proximity 1.0 next to a normal star             |
| `condenser_proximity_scale` | `true`  | If false, proximity is treated as 1.0 (star class bonus only, no positioning game) |
| `ftl_fuel_per_sector` | `0.0`   | Canisters consumed per sector of jump distance (feature disabled by default)       |
| `fuel_per_canister` | `100.0` | Fluid units represented by one filled canister when drawing from a tank            |
| `ftl_unfueled_cooldown_multiplier` | `3.0`   | FTL cooldown multiplier for underfueled jumps                                      |
| `fluid_level_per_explosion` | —       | Fluid units per explosion event on tank destruction                                |
| `max_fluid_explosion_radius` | —       | Maximum explosion radius on a fully loaded tank                                    |
| `fluid_explosion_damage` | —       | Base damage per explosion event                                                    |

---

## Architecture Notes (for developers)

- **Heliogen supply** is tracked per star system in `StellarFuelSupplier`, keyed by system `Vector3i`. Suppliers are created lazily on first access — no galaxy-gen hook required. Works on pre-existing worlds.
- **Extractor fuel** is resolved once per tick in `MixinExtractorTickListener.onPreManufacture`, combining `FluidTankSystemModule` level and inventory canister count into a single `FuelTickState.availableFuelUnits` value. Both the strength override listener and `onProduceItem` read from this — no inventory access after `onPreManufacture`.
- **Enhancer disconnect** — `ElementRegistry.doOverwrites()` removes `FACTORY_ENHANCER` from the `controlling`/`controlledBy` lists of Vapor Siphon and Magmatic Extractor at block config load time. Enhancers still define the extraction ceiling; Heliogen fuel is what drives output toward it.
- **Fluid tanks** use the `FluidTank` generic block class. Adding a new fluid type requires only a new `ElementRegistry`
  entry — the tank module, explosion logic, and future pipe routing all work generically.
- **Virtualised entity fuel cache** — `EntityFuelManager` keeps a persistent `EntityFuelCache` per entity (keyed by UID)
  so that fuel reads and drains are valid even when the entity is not loaded. `syncFromLive` snapshots the live entity
  into the cache; `writeBackToLive` flushes consumed fuel back to the actual tank and inventory using a canister-count
  delta to avoid unnecessary item scanning.
- **Persistence** mirrors RRS's `PersistentObjectUtil` pattern exactly for both stellar supply (`StellarFuelManager`)
  and entity fuel caches (`EntityFuelManager`): load on `ServerInitializeEvent`, save on `WorldSaveEvent` and
  `onDisable`.
- **Upcoming — pipe-network topology** — `FluidTankSystemModule` will be refactored to replace the inherited `blocks`
  array with two explicit maps: `tankSegments` (one entry per placed `FLUID_TANK` block, drives capacity) and
  `pipeSegments` (one entry per placed pipe-network block). `onBlockPlaced`/`onBlockRemoved` hooks will maintain both
  maps and trigger structural capacity recalculation. See TODO §8 for the full task breakdown.

---

## Planned

- **Fluid pipe-network refactor** — replace `FluidTankSystemModule.blocks` with `tankSegments` / `pipeSegments` maps;
  structural capacity derived from block count; `onBlockPlaced` / `onBlockRemoved` hooks maintain topology (TODO §8.1–8.7)
- **Pipe transport logic** — directional `FluidPump` flow, `FluidValve` gating, `FluidFilter` whitelist enforcement (TODO §8.8)
- **Fluid fill visualisation** — per-block world-space fill overlay rendered inside each tank block, scaled to the network's fill fraction (TODO §9)
- **Tank connected textures** — Fluid Tank faces automatically swap to the correct CTM variant (exposed plate vs. flange/port) based on adjacent tanks and pipe inlets, using StarMade's native `ConnectedTextureUnit` API and a 47-variant sprite sheet (TODO §10). Pipe blocks use 3D mesh models instead.
- Custom textures and icons for all blocks and items



