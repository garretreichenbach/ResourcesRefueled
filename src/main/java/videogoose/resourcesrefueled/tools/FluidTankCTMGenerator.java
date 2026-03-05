package videogoose.resourcesrefueled.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Offline texture-sheet generator for the Fluid Tank CTM sprite sheet.
 * <p>
 * Currently generates the <b>SIMPLE_16</b> variant (single row, 16 tiles) which
 * uses cardinal connections only — no corner sub-variants.  Once this is confirmed
 * working in-game the generator can be extended to the full FULL_47 layout.
 *
 * <h2>Required source images (256 × 256 RGBA PNG)</h2>
 * <pre>
 *   base.png         — full interior face drawn on every tile
 *   edge_top.png     — border strip along the full top side
 *   edge_right.png   — border strip along the full right side
 *   edge_bottom.png  — border strip along the full bottom side
 *   edge_left.png    — border strip along the full left side
 * </pre>
 * Place all five files in {@code src/main/resources/textures/fluid_tank/src/}.
 *
 * <h2>Sheet layout (SIMPLE_16)</h2>
 * 16 tiles wide × 1 row = 4096 × 256 px at 256 px/tile.
 *
 * <h2>Slot table</h2>
 * <pre>
 *  Slot  T  R  B  L   Description
 *   0    -  -  -  -   Isolated
 *   1    ✓  -  -  -   Top only
 *   2    -  ✓  -  -   Right only
 *   3    -  -  ✓  -   Bottom only
 *   4    -  -  -  ✓   Left only
 *   5    ✓  ✓  -  -   Top-right outer corner
 *   6    -  ✓  ✓  -   Bottom-right outer corner
 *   7    -  -  ✓  ✓   Bottom-left outer corner
 *   8    ✓  -  -  ✓   Top-left outer corner
 *   9    ✓  -  ✓  -   Vertical bar
 *  10    -  ✓  -  ✓   Horizontal bar
 *  11    ✓  ✓  ✓  -   T-junction, open left
 *  12    -  ✓  ✓  ✓   T-junction, open top
 *  13    ✓  -  ✓  ✓   T-junction, open right
 *  14    ✓  ✓  -  ✓   T-junction, open bottom
 *  15    ✓  ✓  ✓  ✓   Cross (fully surrounded)
 * </pre>
 */
public class FluidTankCTMGenerator {

	private static final int TILE  = 256;
	private static final int SLOTS = 16;

	private static final String SRC_DIR = "src/main/resources/textures/fluid_tank/src/";
	private static final String OUT     = "src/main/resources/textures/fluid_tank_ctm16.png";

	// Each row is { connT, connR, connB, connL } for the corresponding slot index.
	private static final boolean[][] SLOT_FLAGS = {
		// T      R      B      L
		{false, false, false, false}, //  0 — isolated
		{true,  false, false, false}, //  1 — top
		{false, true,  false, false}, //  2 — right
		{false, false, true,  false}, //  3 — bottom
		{false, false, false, true }, //  4 — left
		{true,  true,  false, false}, //  5 — top-right corner
		{false, true,  true,  false}, //  6 — bottom-right corner
		{false, false, true,  true }, //  7 — bottom-left corner
		{true,  false, false, true }, //  8 — top-left corner
		{true,  false, true,  false}, //  9 — vertical bar
		{false, true,  false, true }, // 10 — horizontal bar
		{true,  true,  true,  false}, // 11 — T open-left
		{false, true,  true,  true }, // 12 — T open-top
		{true,  false, true,  true }, // 13 — T open-right
		{true,  true,  false, true }, // 14 — T open-bottom
		{true,  true,  true,  true }, // 15 — cross
	};

	public static void main(String[] args) throws IOException {
		System.out.println("[CTMGen] Loading source images from: " + SRC_DIR);

		BufferedImage base  = load("base.png");
		BufferedImage edgeT = load("edge_top.png");
		BufferedImage edgeR = load("edge_right.png");
		BufferedImage edgeB = load("edge_bottom.png");
		BufferedImage edgeL = load("edge_left.png");

		BufferedImage sheet = new BufferedImage(SLOTS * TILE, TILE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

		for(int slot = 0; slot < SLOTS; slot++) {
			boolean[] flags = SLOT_FLAGS[slot];
			boolean connT = flags[0], connR = flags[1], connB = flags[2], connL = flags[3];

			BufferedImage tile = copyOf(base);
			Graphics2D tg = tile.createGraphics();
			tg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

			if(!connT) tg.drawImage(edgeT, 0, 0, null);
			if(!connR) tg.drawImage(edgeR, 0, 0, null);
			if(!connB) tg.drawImage(edgeB, 0, 0, null);
			if(!connL) tg.drawImage(edgeL, 0, 0, null);

			tg.dispose();
			g.drawImage(tile, slot * TILE, 0, null);
			System.out.printf("[CTMGen] Slot %2d  T=%b R=%b B=%b L=%b%n", slot, connT, connR, connB, connL);
		}
		g.dispose();

		Path outPath = Paths.get(OUT);
		Files.createDirectories(outPath.getParent());
		ImageIO.write(sheet, "PNG", outPath.toFile());
		System.out.println("[CTMGen] Done — wrote " + outPath.toAbsolutePath());
	}

	private static BufferedImage load(String filename) throws IOException {
		Path p = Paths.get(SRC_DIR + filename);
		if(!Files.exists(p)) {
			throw new IOException("Missing source image: " + p.toAbsolutePath()
				+ "\nPlace a 256x256 RGBA PNG there before running the generator.");
		}
		BufferedImage img = ImageIO.read(p.toFile());
		if(img.getWidth() != TILE || img.getHeight() != TILE) {
			throw new IOException("Source image must be " + TILE + "x" + TILE + " px: " + p);
		}
		return ensureARGB(img);
	}

	private static BufferedImage copyOf(BufferedImage src) {
		BufferedImage copy = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return copy;
	}

	private static BufferedImage ensureARGB(BufferedImage src) {
		if(src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}
}
