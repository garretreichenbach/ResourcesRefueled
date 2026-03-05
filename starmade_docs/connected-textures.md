# Connected Textures (CTM)

Connected Texture Method (CTM) lets a block automatically choose which texture
variant to display on each face based on which neighbouring blocks are the same
type.  The result is a seamless visual join between adjacent blocks — exactly
what you need for fluid tanks, glass panels, pipes, circuit boards, or any other
block that should look continuous when placed next to its own kind.

## How it works

Every time a block face is rendered, the engine checks up to 8 **in-plane
neighbours** — the 4 cardinal edge neighbours (top, right, bottom, left relative
to that face) and the 4 corner neighbours — and encodes the result as an 8-bit
**connection mask**.  The mask is then mapped to one of several standard tile
**variants** that are stored contiguously in the block texture atlas.

```
Looking straight at a face:

   TL | T | TR          Bit layout (LSB first):
   ---+---+---            Bit 0 = T  (top)
    L | * |  R            Bit 1 = R  (right)
   ---+---+---            Bit 2 = B  (bottom)
   BL | B | BR            Bit 3 = L  (left)
                          Bit 4 = TR (corner — only if T AND R are set)
                          Bit 5 = BR (corner — only if B AND R are set)
                          Bit 6 = BL (corner — only if B AND L are set)
                          Bit 7 = TL (corner — only if T AND L are set)
```

Corner bits follow the standard CTM convention: a corner is only counted when
**both** adjacent cardinals are also present.  This prevents visual artifacts on
lone diagonal neighbours.

## Two layout sizes

| Mode | Variants | Tile region | Use when |
|---|---|---|---|
| `SIMPLE_16` | 16 | 1 row × 16 tiles | Simple blocks, pipes, wires — cardinals only |
| `FULL_47` | 47 | 3 rows × 16 tiles (47 used, 1 padding) | Glass, fluid tanks — full corner detail |

---

## Key classes

| Class | Package | Purpose |
|---|---|---|
| `ConnectedTextureUtils` | `api.utils.texture` | Low-level mask computation and variant lookup |
| `ConnectedTextureUnit` | `api.utils.texture` | High-level per-block update helper |
| `CTMTextureRegion` | `api.utils.texture` | Describes a contiguous tile region in the atlas |
| `StarLoaderTexture` | `api.utils.textures` | Registers tile images into the atlas |

---

## Preparing your texture sheet

Your CTM texture sheet must be a **16-tile-wide sprite sheet** where each tile
is a square power-of-two (16 px, 32 px, 64 px, …).  The variants must be laid
out left-to-right, top-to-bottom in the order shown below.

### 16-variant sheet layout (1 row)

```
[0][1][2][3][4][5][6][7][8][9][10][11][12][13][14][15]
```

| Slot | Connected sides (T R B L) | Description |
|---|---|---|
| 0 | none | Isolated — no connections |
| 1 | T | Top edge only |
| 2 | R | Right edge only |
| 3 | B | Bottom edge only |
| 4 | L | Left edge only |
| 5 | T R | Top-right outer corner |
| 6 | R B | Bottom-right outer corner |
| 7 | B L | Bottom-left outer corner |
| 8 | T L | Top-left outer corner |
| 9 | T B | Vertical bar |
| 10 | R L | Horizontal bar |
| 11 | T R B | T-junction (open left) |
| 12 | R B L | T-junction (open top) |
| 13 | T B L | T-junction (open right) |
| 14 | T R L | T-junction (open bottom) |
| 15 | T R B L | Cross |

A sheet for a 64 px/tile resolution would be **1024 × 64 pixels**.

### 47-variant sheet layout (3 rows)

Rows 0 and 1 follow the same cardinal-only structure (slots 0–15 and 16–31 hold
corner sub-variants).  Row 2 uses slots 32–46 for the fully-surrounded cross
corner combinations, with slot 47 left as transparent padding.

A full 47-tile sheet at 64 px/tile is **1024 × 192 pixels** (3 rows × 64 px).

You do not need to memorise the exact slot meanings — use `CTMTextureRegion.printLayout()`
at startup to dump the complete row/column mapping to the console.

---

## Registering tiles from a sprite sheet

All registration must happen inside your mod's `onEnable()`.

```java
import api.utils.texture.CTMTextureRegion;
import api.utils.texture.ConnectedTextureUnit;
import api.utils.textures.StarLoaderTexture;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class MyMod extends StarMod {

    private ConnectedTextureUnit tankHelper;

    @Override
    public void onEnable() {
        try {
            // Load the sprite sheet from your mod's resources
            BufferedImage sheet = ImageIO.read(
                MyMod.class.getResourceAsStream("res/fluid_tank_ctm47.png")
            );

            // Register all 47 variants and get back a helper pre-wired to your block
            // (the block ID is filled in after onBlockConfigLoad, see below)
            CTMTextureRegion region = StarLoaderTexture.newCTMRegionFromSheet(
                sheet,
                CTMTextureRegion.VARIANTS_47,
                "FluidTankCTM"
            );

            // Optionally print the layout for texture artist reference
            region.printLayout();

            // Store the region so you can access baseIndex in onBlockConfigLoad
            this.tankRegion = region;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private CTMTextureRegion tankRegion;
    private short fluidTankId;

    @Override
    public void onBlockConfigLoad(BlockConfig config) {
        // Register the block using the base (isolated) tile as its default texture
        ElementInformation tank = BlockConfig.newElement(
            this, "Fluid Tank",
            new short[]{ tankRegion.baseIndex }
        );
        tank.extendedTexture = true;     // enable world-space UV tiling in the shader
        tank.setMaxHitPointsE(500);
        tank.setCanActivate(false);
        BlockConfig.add(tank);

        fluidTankId = tank.getId();

        // Now build the helper with the actual block ID
        tankHelper = tankRegion.buildHelper(fluidTankId);
    }
}
```

### One-liner convenience methods

If you don't need to inspect the `CTMTextureRegion` directly, use the shorthand
methods on `StarLoaderTexture`:

```java
// 16-variant — returns a ConnectedTextureUnit ready to use
tankHelper = StarLoaderTexture.newCTMHelper16(sheet16, fluidTankId, "MyTank16");

// 47-variant
tankHelper = StarLoaderTexture.newCTMHelper47(sheet47, fluidTankId, "MyTank47");
```

---

## Registering from individual tile images

If you prefer to ship each variant as a separate image file:

```java
List<BufferedImage> tiles = new ArrayList<>();
for (int v = 0; v < CTMTextureRegion.VARIANTS_16; v++) {
    tiles.add(ImageIO.read(
        MyMod.class.getResourceAsStream("res/tank/variant_" + v + ".png")
    ));
}

// Optional: matching normal-map tiles
List<BufferedImage> normals = new ArrayList<>();
for (int v = 0; v < CTMTextureRegion.VARIANTS_16; v++) {
    normals.add(ImageIO.read(
        MyMod.class.getResourceAsStream("res/tank/variant_" + v + "_nrm.png")
    ));
}

CTMTextureRegion region = StarLoaderTexture.newCTMRegionFromTiles(tiles, normals, "MyTankTiles");
```

---

## Updating CTM on block placement and removal

The CTM variant of a block must be recalculated whenever a block is placed or
removed nearby.  Register listeners for `SegmentPieceAddEvent` and
`SegmentPieceRemoveEvent` and call `updatePieceAndNeighbors`, which refreshes
the block and all 6 face-adjacent neighbours of the same type in one call.

```java
StarLoader.registerListener(SegmentPieceAddEvent.class,
    new Listener<SegmentPieceAddEvent>() {
        @Override
        public void onEvent(SegmentPieceAddEvent event) {
            SegmentPiece piece = event.getSegmentController()
                .getSegmentBuffer()
                .getPointUnsave(new Vector3i(
                    event.getAbsX(), event.getAbsY(), event.getAbsZ()));
            if (piece != null && piece.getType() == fluidTankId) {
                // Run on next tick so the segment data is fully committed
                new StarRunnable() {
                    @Override
                    public void run() {
                        tankHelper.updatePieceAndNeighbors(piece);
                    }
                }.runLater(this, 1);
            }
        }
    });

StarLoader.registerListener(SegmentPieceRemoveEvent.class,
    new Listener<SegmentPieceRemoveEvent>() {
        @Override
        public void onEvent(SegmentPieceRemoveEvent event) {
            // On removal the piece itself is gone, but neighbours still need updating.
            // updatePieceAndNeighbors handles null/wrong-type pieces safely.
            SegmentPiece piece = event.getSegmentController()
                .getSegmentBuffer()
                .getPointUnsave(new Vector3i(
                    event.getAbsX(), event.getAbsY(), event.getAbsZ()));
            if (piece != null) {
                tankHelper.updatePieceAndNeighbors(piece);
            }
        }
    });
```

---

## Cross-type connections

A fluid tank might want to visually connect to a "frame" block of a different
type.  Use `connectsTo()` on the helper to declare the additional type:

```java
// Connect to both FluidTank and FluidTankFrame blocks
tankHelper.connectsTo(fluidTankFrameId);

// Or connect to ANY non-air block (useful for generic glass)
tankHelper.connectsTo(Element.TYPE_ALL);
```

---

## Low-level API: ConnectedTextureUtils

`ConnectedTextureUnit` covers most use cases.  For custom rendering logic you
can drop down to `ConnectedTextureUtils` directly.

```java
// 8-bit mask for the TOP face of a block
int mask = ConnectedTextureUtils.getConnectionMask(piece, Element.TOP);

// Resolve to a 47-variant index
int variant = ConnectedTextureUtils.getVariantIndex47(mask);

// Or all six faces at once
int[] masks    = ConnectedTextureUtils.getAllFaceConnectionMasks(piece);
int[] variants = ConnectedTextureUtils.getAllVariants47(piece);

// Quick connectivity check (0 = isolated, 6 = fully buried)
int degree = ConnectedTextureUtils.getConnectivityDegree(piece);

// 6-bit bitmask — bit N is set if face N has a matching neighbour
int faceMask = ConnectedTextureUtils.getFaceConnectivityMask(piece);
```

---

## CTMTextureRegion reference

`CTMTextureRegion` is a lightweight value object that documents where your tile
region lives in the atlas.

```java
CTMTextureRegion region = StarLoaderTexture.newCTMRegionFromSheet(
    sheet, CTMTextureRegion.VARIANTS_47, "MyBlock");

short baseIndex   = region.baseIndex;       // atlas index of variant 0
int   variants    = region.variantCount;    // 16 or 47
short nextFree    = region.nextFreeIndex(); // first atlas slot after this region
int   rows        = region.rowsConsumed();  // rows used in the 16-wide atlas

// Look up a specific variant
short atlasIdx    = region.indexOfVariant(15); // cross tile
int   row         = region.rowOfVariant(15);
int   col         = region.colOfVariant(15);

// Check whether an existing atlas index belongs to this region
boolean mine      = region.contains((short) 1234);

// Build a helper directly from the region
ConnectedTextureUnit helper = region.buildHelper(myBlockId);

// Dump full layout to stderr (useful for texture artists)
region.printLayout();
```

---

## Summary of the full setup checklist

1. **Prepare texture sheet** — 16 tiles wide, variants in canonical order.
2. **`onEnable()`** — call `StarLoaderTexture.newCTMRegionFromSheet()` (or the
   tile-list variant) to register tiles into the atlas. Store the returned
   `CTMTextureRegion`.
3. **`onBlockConfigLoad()`** — create the block using `region.baseIndex` as the
   texture ID, set `extendedTexture = true`, add it, then call
   `region.buildHelper(blockId)` to get a `ConnectedTextureUnit`.
4. **Placement / removal events** — call `tankHelper.updatePieceAndNeighbors(piece)`
   (deferred by 1 tick via `StarRunnable`) so all connected neighbours refresh.
5. **Optional** — call `region.printLayout()` once at startup to validate tile
   coordinates while developing your texture sheet.

