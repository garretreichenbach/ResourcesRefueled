# ResourcesRefueled

A StarMade addon for **[Resources ReSourced](https://starmadedock.net/content/resources-resourced.8292/)** by Ithirahad that adds a stellar fuel economy to the game. Resource extraction and FTL travel now require **Heliogen** — a volatile plasma fuel distilled directly from starlight — tying your industrial output and exploration range to your proximity to, and relationship with, the stars around you.

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
RRS's Vapor Siphons and Magmatic Extractors now consume Heliogen fuel each extraction cycle. Fueled extractors operate at full efficiency. Unfueled extractors still work, but at a fraction of normal output (configurable, default 30%). Fuel is consumed once per completed cycle — the stronger or more enhanced the extractor, the more it produces per cycle and the more fuel it burns. Spent canisters are returned as empty canisters in the same inventory update, ready to be transported back to a refinery for refilling.

This creates a deliberate supply chain: Heliogen is produced at condenser stations near stars, refined into canisters, and physically transported to extraction sites. Automating that transport is left to the player.

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

| Key | Default | Description |
|---|---|---|
| `fuel_cost_per_strength_unit` | `0.5` | Canisters consumed per unit of extractor strength per tick |
| `unfueled_extraction_efficiency` | `0.3` | Extraction rate multiplier when running without fuel |
| `ftl_fuel_per_sector` | `1.0` | Canisters consumed per sector of jump distance |
| `fuel_per_canister` | `100.0` | Fluid units represented by one filled canister when drawing from a tank |
| `ftl_unfueled_cooldown_multiplier` | `3.0` | FTL cooldown multiplier for underfueled jumps |
| `fluid_level_per_explosion` | — | Fluid units per explosion event on tank destruction |
| `max_fluid_explosion_radius` | — | Maximum explosion radius on a fully loaded tank |
| `fluid_explosion_damage` | — | Base damage per explosion event |

---

## Architecture Notes (for developers)

- **Heliogen supply** is tracked per star system in `StellarFuelSupplier`, keyed by system `Vector3i`. Suppliers are created lazily on first access — no galaxy-gen hook required. Works on pre-existing worlds.
- **Fuel consumption** in extractors is implemented as a Mixin into RRS's `ExtractorTickFastListener`, injected at `HEAD`. No RRS source modification required.
- **Fluid tanks** use the `FluidTank` generic block class. Adding a new fluid type requires only a new `ElementRegistry` entry with a different `fluidIdSupplier` — the tank block, module, explosion logic, and pipe routing all work generically.
- **Persistence** mirrors RRS's `PersistentObjectUtil` pattern exactly: load on `ServerInitializeEvent`, save on `WorldSaveEvent` and `onDisable`.

---

## Planned

- Pipe network fluid transport logic
- Per-fluid dynamic tank textures
- FTL cooldown penalty application (pending API confirmation)
- Custom textures and icons for all blocks and items

