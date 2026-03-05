# Tutorial: Fluid Tank Block

This tutorial walks through creating a **Fluid Tank** block from scratch — a
block that uses the Connected Texture Method (CTM) to automatically display
seamless edge, corner, and interior textures depending on which adjacent blocks
are also fluid tanks.

Before following this tutorial, read the [Connected Textures](../assets/connected-textures.md)
reference page to understand the concepts.

## What we are building

A placeable **Fluid Tank** block that:

- Uses a 47-variant CTM sprite sheet so every face shows the correct border,
  edge, or fully-interior tile.
- Visually connects to other Fluid Tank blocks of the same type on all six sides.
- Supports an optional normal map for lit shading.
- Updates its own texture and the textures of all six face-adjacent neighbours
  whenever a block is placed or removed.

---

## Step 1 — Project layout

```
MyMod/
├── src/main/java/me/myname/mymod/
│   └── MyMod.java
└── src/main/resources/me/myname/mymod/
    ├── fluid_tank_ctm47.png        ← 47-variant colour sheet (1024 × 192 px at 64 px/tile)
    ├── fluid_tank_ctm47_nrm.tga    ← matching normal-map sheet (optional)
    └── fluid_tank_icon.png         ← 64 × 64 build icon
```

The sheet size depends on your tile resolution.  The formulas are:

```
sheet width  = tileSize × 16
sheet height = tileSize × ceil(variantCount / 16)

For VARIANTS_47 at 64 px/tile:  1024 × 192 px   (rows 0, 1, 2; slot 47 is empty)
For VARIANTS_16 at 64 px/tile:  1024 × 64  px   (single row)
```

---

## Step 2 — Registering the texture in `onEnable`

```java
package me.myname.mymod;

import api.config.BlockConfig;
import api.mod.StarMod;
import api.utils.StarRunnable;
import api.utils.texture.CTMTextureRegion;
import api.utils.texture.ConnectedTextureUnit;
import api.utils.textures.StarLoaderTexture;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class MyMod extends StarMod {

    // Stored between onEnable and onBlockConfigLoad
    private CTMTextureRegion tankRegion;

    // Filled in onBlockConfigLoad, used by event listeners
    private short fluidTankId;
    private ConnectedTextureUnit tankHelper;
    private int tankIconId;

    @Override
    public void onEnable() {
        try {
            // --- colour sheet ---
            BufferedImage sheet = ImageIO.read(
                MyMod.class.getResourceAsStream("fluid_tank_ctm47.png")
            );

            // --- optional normal-map sheet ---
            InputStream nrmStream =
                MyMod.class.getResourceAsStream("fluid_tank_ctm47_nrm.tga");

            // Register 47 tile variants into the atlas and get a region descriptor.
            // Both sheets must be the same size.
            tankRegion = StarLoaderTexture.newCTMRegionFromSheet(
                sheet,
                nrmStream != null
                    ? imageFromTga(nrmStream)  // helper shown below
                    : null,
                CTMTextureRegion.VARIANTS_47,
                "FluidTankCTM"
            );

            // During development, print the full tile layout to the log
            // so you can verify your sprite sheet positions.
            tankRegion.printLayout();

            // --- icon ---
            BufferedImage icon = ImageIO.read(
                MyMod.class.getResourceAsStream("fluid_tank_icon.png")
            );
            tankIconId = StarLoaderTexture.newIconTexture(icon).getTextureId();

        } catch (Exception e) {
            e.printStackTrace();
        }

        registerEvents();
    }

    // Helper: read a TGA normal-map sheet into a BufferedImage
    private BufferedImage imageFromTga(InputStream stream) {
        try {
           TGALoader loader = new TGALoader();
           ByteBuffer buf = TGALoader.loadImage(stream);
            return TGALoader.convertByteBufferToImage(buf, TGALoader.getLastWidth(), TGALoader.getLastHeight(), TGALoader.getLastDepth() == 32);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
```

---

## Step 3 — Creating the block in `onBlockConfigLoad`

```java
    @Override
    public void onBlockConfigLoad(BlockConfig config) {
        // Use the base (isolated / no-neighbour) tile as the default texture.
        ElementInformation tank = BlockConfig.newElement(
            this, "Fluid Tank",
            new short[]{ tankRegion.baseIndex }
        );

        // extendedTexture = true tells the shader to use world-space UV tiling
        // so the texture tiles continuously across multiple blocks.
        tank.extendedTexture = true;

        tank.setMaxHitPointsE(750);
        tank.setBuildIconNum(tankIconId);
        tank.setShoppable(true);
        tank.setPrice(1500);

        // Give it a simple recipe: 10 Crystal Composite
        BlockConfig.addRecipe(tank, BlockConfig.BASIC_FACTORY, 10, new FactoryResource(10, ElementKeyMap.CRYSTAL_COMPOSITE));

        BlockConfig.add(tank);

        fluidTankId = tank.getId();

        // Build the CTM update helper now that we have the real block ID.
        tankHelper = tankRegion.buildHelper(fluidTankId);

        // Optional: also connect visually to a hypothetical "Fluid Tank Frame" block.
        // tankHelper.connectsTo(fluidTankFrameId);
    }
```

---

## Step 4 — Updating CTM on placement and removal

Register these listeners at the end of `onEnable` (via the `registerEvents` call
we added in Step 2).

```java
    private void registerEvents() {
        // --- block placed ---
        StarLoader.registerListener(SegmentPieceAddEvent.class,
            new Listener<SegmentPieceAddEvent>() {
                @Override
                public void onEvent(SegmentPieceAddEvent event) {

                    if (fluidTankId == 0) return; // not yet registered

                    Vector3i pos = new Vector3i(
                        event.getAbsX(), event.getAbsY(), event.getAbsZ());
                    SegmentPiece piece = event.getSegmentController()
                        .getSegmentBuffer()
                        .getPointUnsave(pos);

                    if (piece == null || piece.getType() != fluidTankId) return;

                    // Defer by 1 tick so the segment data is fully committed
                    // before we read neighbours.
                    final SegmentPiece finalPiece = piece;
                    new StarRunnable() {
                        @Override
                        public void run() {
                            tankHelper.updatePieceAndNeighbors(finalPiece);
                        }
                    }.runLater(MyMod.this, 1);
                }
            });

        // --- block removed ---
        StarLoader.registerListener(
            SegmentPieceRemoveEvent.class,
            new Listener<SegmentPieceRemoveEvent>() {
                @Override
                public void onEvent(SegmentPieceRemoveEvent event) {

                    if (fluidTankId == 0) return;

                    // The removed block's position: update whatever is still there
                    // (its neighbours, which may now have an exposed face).
                    Vector3i pos = new Vector3i(
                        event.getAbsX(), event.getAbsY(), event.getAbsZ());
                    SegmentPiece piece = event.getSegmentController()
                        .getSegmentBuffer()
                        .getPointUnsave(pos);

                    // updatePieceAndNeighbors is null-safe and skips wrong types.
                    new StarRunnable() {
                        @Override
                        public void run() {
                            tankHelper.updatePieceAndNeighbors(piece);
                        }
                    }.runLater(MyMod.this, 1);
                }
            });
    }
} // end of MyMod
```

---

## Step 5 — Designing the sprite sheet

Use the table below as a reference when laying out your 47-tile sheet.  All
tiles are written left-to-right, top-to-bottom in the 16-wide grid.

### Row 0 — Cardinal-only variants (slots 0–15)

| Col | Slot | Connected (T R B L) | Description |
|---|---|---|---|
| 0 | 0 | — | Isolated |
| 1 | 1 | T | Top edge |
| 2 | 2 | R | Right edge |
| 3 | 3 | B | Bottom edge |
| 4 | 4 | L | Left edge |
| 5 | 5 | T R | Top-right outer corner (no inner fill) |
| 6 | 6 | R B | Bottom-right outer corner |
| 7 | 7 | B L | Bottom-left outer corner |
| 8 | 8 | T L | Top-left outer corner |
| 9 | 9 | T B | Vertical bar |
| 10 | 10 | R L | Horizontal bar |
| 11 | 11 | T R B | T-junction, open left |
| 12 | 12 | R B L | T-junction, open top |
| 13 | 13 | T B L | T-junction, open right |
| 14 | 14 | T R L | T-junction, open bottom |
| 15 | 15 | T R B L | Cross, no inner corners |

### Row 1 — Two-cardinal corner sub-variants and three-cardinal sub-variants (slots 16–31)

| Col | Slot | Description |
|---|---|---|
| 0 | 16 | T+R with inner TR corner |
| 1 | 17 | R+B with inner BR corner |
| 2 | 18 | B+L with inner BL corner |
| 3 | 19 | T+L with inner TL corner |
| 4–7 | 20–23 | T+R+B T-junction sub-variants (TR corner, BR corner, both, neither) |
| 8–11 | 24–27 | R+B+L T-junction sub-variants |
| 12–15 | 28–31 | T+B+L T-junction sub-variants |

### Row 2 — Three-cardinal sub-variants continued + cross corner variants (slots 32–46)

| Col | Slot | Description |
|---|---|---|
| 0–3 | 32–35 | T+R+L T-junction sub-variants |
| 4–14 | 36–46 | All-four-cardinal cross variants (11 unique corner combos) |
| 15 | 47 | **Unused / transparent padding** |

> **Tip:** Call `region.printLayout()` once at startup.  It prints the exact atlas
> row and column for every slot so you can double-check your art against the layout.

---

## Summary

| Step | Where | What |
|---|---|---|
| 1 | `onEnable()` | Load sprite sheet, call `newCTMRegionFromSheet()`, store `CTMTextureRegion` |
| 2 | `onBlockConfigLoad()` | Create block with `region.baseIndex` as texture ID, set `extendedTexture = true`, call `region.buildHelper(id)` |
| 3 | Event listeners | On place/remove: `tankHelper.updatePieceAndNeighbors(piece)` deferred 1 tick |
| 4 | Art | 16-wide sprite sheet, variants in canonical order |

For the low-level API (custom mask queries, per-face logic, cross-type
connections) see [Connected Textures](../assets/connected-textures.md).

