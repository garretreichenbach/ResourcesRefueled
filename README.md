# Resources Reorganized

**Resources Reorganized** is a mod aimed at improving general item and fluid logistics across the game (bulk storage,
portable containers, pipe networks, and fluid-aware systems). The mod remains fully compatible with Resources
ReSourced (RRS) and extends it with a generic fluid system, portable canisters, and a small Heliogen fuel chain as a
demonstration of the system.

---

## Requirements

- [Resources ReSourced (RRS)](https://starmadedock.net/content/resources-resourced.8292/) v0.9.8+
- StarMade 0.303.0+

---

## Overview

This project evolved from a single-purpose Heliogen fuel addon into a reusable, generic fluids-and-logistics toolkit:

- Generic fluid storage and pipe primitives (tanks, pipes, pumps, valves, filters)
- Unified portable canisters that carry any fluid via per-slot JSON metadata (no separate item ids per fluid)
- Networked tank components with structural capacity, per-network fluid type and level
- Fluid ports to bridge inventories and networks (manual fill/drain via inventory slots)
- Volatility flags on fluids so some fluids (e.g. Heliogen) are explosive when stored in tanks

The Heliogen chain (condenser → plasma → refinery → filled canisters) demonstrates the system and integrates with
extractors and FTL as fuel, but the core plumbing is generic and can host additional fluids and use-cases.

---

## Key Features Implemented

- Generic `FluidTank` block and structural pipe primitives (`FluidPipe`, `PipePump`, `PipeValve`, `PipeFilter`)
- Unified `FluidCanister` item: a single item type whose contents (fluid id, amount, capacity) are stored in per-slot
  JSON metadata. The GUI shows the fluid and amount in the item tooltip.
- `FluidMeta` central utility: stores fluid-type properties (e.g. `isVolatile`) and provides canister metadata
  writers/readers. The `HeliogenPlasma` element registers itself as volatile.
- `FluidSystemModule`: module that partitions placed tank/pipe blocks into connected `FluidNetwork`s, stores per-network
  `fluidId` and `fluidLevel`, and handles placement/removal topologically.
- `FluidPort` block & GUI: two-slot inventory for manual canister in/out; server-side tick logic transfers exactly one
  canister worth of fluid between the inventory and the adjacent network when active.
- `ExtractorFuelListener` and `EntityFuelManager`: extractors and FTL jumps can draw fuel from tank networks and
  fallback to filled canisters in inventory; a virtualised per-entity cache persists fuel state across unloads and
  saves.
- Explosion safety: tanks only trigger explosions on destruction when the network's fluid is marked volatile.
- HUD improvements: tank HUD now shows fluid name, capacity, current volume, and a volatile indicator if applicable;
  pumps show per-block flow rate and network aggregate flow.

---

## Crafting

Most blocks are assembled using RRS components as before; the refinery compresses raw plasma into filled canisters. The
refinery recipe now consumes a plain `FluidCanister` (empty, metadata-less) and produces a filled `FluidCanister` (
metadata stamped by the refinery logic).

---

## Configuration

All values live in the mod config (server-side). Notable keys:

- `fuel_cost_per_strength_unit` — fuel units per extractor strength unit
- `fueled_extraction_bonus` — bonus output fraction applied when fuel is consumed
- `condenser_base_output` — condenser bonus at proximity 1.0
- `ftl_fuel_per_sector` — canisters per sector for FTL (0 by default)
- `capacity_per_canister` — fluid units represented by one filled canister
- `capacity_per_tank` — fluid units contributed by each placed tank block
- `fluid_level_per_explosion`, `max_fluid_explosion_radius`, `fluid_explosion_damage` — tank explosion tuning

(See `config/ResourcesRefueled/config.yml` on a server for full keys and documentation.)

---

## Developer Notes

- The core of the fluid system is generic. Adding new fluids is a two-step process:
  1. Register a new item element for the raw fluid (e.g. `MyCoolant`)
  2. If the fluid is volatile, call `FluidMeta.registerVolatile(itemInfo.id)` in the element's `postInitData()`

- Portable canisters are a single item type whose contents are stored per-slot; `FluidMeta` exposes helpers to stamp and
  read that metadata.

- `FluidSystemModule` replaces the old inherited `blocks` array with explicit `tankSegments` and `pipeSegments` maps;
  networks are recalculated on place/remove and persist their `fluidId` and `fluidLevel`.

- The mod was renamed to "Resources Reorganized" to reflect a broader goal of improving item and logistics systems. The
  codebase retains historical names in some places for compatibility with saved worlds; new code and documentation use
  the Resources Reorganized name.

---

## Roadmap (short)

- Pipe transport behaviour: directional pump flow, valve gating, filter whitelists
- Client-side tank fill visualisation (in-world fluid overlays)
- Connected-texture support for tank faces (CTM)
- UI polish and localization for canister/tank tooltips

---