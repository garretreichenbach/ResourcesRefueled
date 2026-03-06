# Inventory Slot Metadata

## Overview

Every `InventorySlot` can carry an optional `JSONObject` of arbitrary mod-defined metadata. Unlike
`ElementInformation` — which is static and shared across **all** instances of the same block type —
slot metadata is **per-instance**: two stacks of the same block in two different slots can each hold
entirely different data.

This system was designed to give mods a lightweight, forward-compatible way to attach custom
properties to individual item stacks without requiring a new block type or a `MetaObject`.

---

## API

All methods live on `org.schema.game.common.data.player.inventory.InventorySlot`.

| Method | Description |
|---|---|
| `getCustomData()` | Returns the `JSONObject`, or `null` if none is set. |
| `setCustomData(JSONObject data)` | Sets (or replaces) the custom data. Pass `null` to remove it. |
| `hasCustomData()` | Returns `true` when a non-null, non-empty object is present. |
| `getOrCreateCustomData()` | Returns the existing object, or lazily creates and assigns an empty one. Useful for incremental writes. |
| `clearCustomData()` | Explicitly removes all custom data (equivalent to `setCustomData(null)`). |

### Basic usage

```java
InventorySlot slot = inventory.getSlot(slotIndex);

// Write
slot.getOrCreateCustomData().put("charges", 3);

// Read
if (slot.hasCustomData() && slot.getCustomData().has("charges")) {
    int charges = slot.getCustomData().getInt("charges");
}

// Remove
slot.clearCustomData();
```

---

## Serialisation

Metadata is fully persisted and transmitted across every serialisation path used by the engine:

| Path | Mechanism |
|---|---|
| Save files (Tag-based `inv1`) | Plain slots emit a `{INT count, STRING json, FinishTag}` struct; multi-slots append a named `"customData"` STRING tag before `FinishTag`. Old saves without these extra fields load cleanly. |
| Network sync (`DataOutput`) | A `boolean` flag followed by a UTF string is appended after the existing slot payload. Old clients that do not read the flag will desync — ensure both sides are on the same build. |
| Object serialisation (`ObjectOutputStream`) | Same `boolean + UTF` pattern appended at the end of the slot stream. |

---

## Behaviour guarantees

### Type changes clear metadata

Calling `setType(newType)` on a slot where `newType != currentType` **automatically clears**
`customData`. This is enforced inside `setType()` itself, so it applies to all code paths
regardless of how the type change happens (`put`, `inc` reaching zero, `mergeMulti` collapse, etc.).

This means metadata always belongs to the item type that is currently in the slot — there is no
risk of one block's data leaking onto a different block that later occupies the same slot.

### Moves and swaps carry metadata

| Operation | Outcome |
|---|---|
| Slot swap (drag two different items onto each other) | Slot objects are swapped wholesale; metadata travels with each item. |
| Move to empty slot (partial or full drag) | A deep copy of the source slot is made via the copy constructor; metadata is deep-copied (`new JSONObject(src.toString())`). |
| `copyTo(InventorySlot)` | Explicit deep-copy; metadata is preserved. |

### Merging is blocked on mismatch

Two stacks **cannot be merged** if their metadata states differ:

- One slot has data and the other does not → merge blocked.
- Both have data but the JSON strings are not equal → merge blocked.
- Both have identical data (or neither has any) → merge proceeds normally.

This ensures that merging never silently discards or overwrites metadata. The player will simply
be unable to combine the stacks until a mod (or the player) reconciles the data difference.

---

## Built-in display overrides

The following reserved keys in `customData` are recognised by the client-side GUI without any
additional mod code:

### `"name"` — Custom display name

```json
{ "name": "Overcharged Thruster" }
```

Replaces the block's default `ElementInformation` name everywhere the slot is displayed:
- Inventory grid tooltip (hover).
- Hotbar fixed-delay tooltip.
- The `InventorySlotOverlayElement.getName()` return value (used by search/filter).

### `"description"` — Custom description line

```json
{ "description": "Produces 150% thrust but runs hot." }
```

Appended as a second line in the tooltip, immediately after the name and before the count/volume
lines. If `"name"` is also set, the custom name is shown first.

### `"icon"` — Custom sprite-sheet index

```json
{ "icon": 42 }
```

Overrides the inventory grid icon with a different entry from the block sprite sheet. The value
is the same integer space as `ElementInformation.getBuildIconNum()`. This is applied in
`InventoryIconsNew.drawSlot()` after the default icon is resolved, so it works for both normal
blocks and meta-item types.

```{note}
The icon override uses the existing block sprite sheet. It is not currently possible to supply a
completely custom texture through this field. That capability may be added in a future update.
```

---

## Combined example

```java
// Give a specific thruster stack a custom name, description, and icon
InventorySlot slot = inventory.getSlot(thrusterSlotIndex);
JSONObject meta = slot.getOrCreateCustomData();
meta.put("name",        "Overcharged Thruster");
meta.put("description", "Produces 150% thrust but runs hot.");
meta.put("icon",        ElementKeyMap.getInfo(ElementKeyMap.THRUSTER_MODULE).getBuildIconNum() + 1);
```

---

## Future extensions

The `customData` object is intentionally open-ended. Planned or likely future extensions include
overriding additional `ElementInformation` fields on a per-slot basis, such as:

- `"color"` — tint the inventory icon.
- `"maxStack"` — per-instance stack size cap.
- `"lore"` — additional flavour-text lines below the description.

Until those fields are formally implemented, mods are free to store any keys they need; the engine
will simply ignore unrecognised keys.

