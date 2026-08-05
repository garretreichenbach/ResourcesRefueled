package videogoose.resourcesreorganized.logistics.item.belt;

import org.schema.common.util.linAlg.Vector3i;

/**
 * Decodes a conveyor belt's NORMAL24 (orient-cube) orientation byte into its forward and up
 * directions. How a belt actually routes items through those axes is the block's
 * {@link BeltShape}, not this class.
 * <p>
 * The table mirrors {@code BlockShapeAlgorithm.algorithms[5]} (the orient-cube ordering): each
 * orientation 0..23 maps to a {primary, secondary} side pair, where <b>primary</b> is the block's
 * up/surface face and <b>secondary</b> is its forward face — exactly how the engine rotates the model.
 * Hardcoded so it works server-side too (the orient-cube classes are client-only). Side ids:
 * FRONT=0, BACK=1, TOP=2, BOTTOM=3, RIGHT=4, LEFT=5.
 */
public final class BeltDirection {

	private static final byte[][] ORIENT = {
			{0, 3}, {0, 5}, {0, 2}, {0, 4}, // FRONT primary
			{1, 3}, {1, 5}, {1, 2}, {1, 4}, // BACK primary
			{3, 1}, {3, 5}, {3, 0}, {3, 4}, // BOTTOM primary
			{2, 1}, {2, 5}, {2, 0}, {2, 4}, // TOP primary
			{4, 0}, {4, 2}, {4, 1}, {4, 3}, // RIGHT primary
			{5, 0}, {5, 2}, {5, 1}, {5, 3}  // LEFT primary
	};

	private static final int[][] SIDE = {
			{0, 0, 1},   // FRONT
			{0, 0, -1},  // BACK
			{0, 1, 0},   // TOP
			{0, -1, 0},  // BOTTOM
			{1, 0, 0},   // RIGHT
			{-1, 0, 0}   // LEFT
	};

	private BeltDirection() {
	}

	/** Flow direction (where items exit) = the orient-cube secondary orientation. */
	public static Vector3i offset(byte orientation) {
		return side(forwardSide(orientation));
	}

	/** Surface-up direction (belt normal) = the orient-cube primary orientation. */
	public static Vector3i up(byte orientation) {
		return side(upSide(orientation));
	}

	/**
	 * Side id (FRONT=0..LEFT=5, matching {@code Element.DIRECTIONSf}) of the block's forward face.
	 * Same value {@link #offset} resolves to a vector, for callers that need the id itself.
	 */
	public static int forwardSide(byte orientation) {
		return switchLeftRight(ORIENT[index(orientation)][1]);
	}

	/** Side id of the block's up face &mdash; the id form of {@link #up}. */
	public static int upSide(byte orientation) {
		return switchLeftRight(ORIENT[index(orientation)][0]);
	}

	/**
	 * Mirrors {@code Element.switchLeftRight}: the engine's orient-cube builds its actual rendered
	 * direction vectors as {@code DIRECTIONSf[switchLeftRight(primary/secondary)]}, so the model's true
	 * forward/up are the switched sides. Without this, flow and corner detection are 180&deg; off for
	 * LEFT/RIGHT-facing orientations (FRONT/BACK/TOP/BOTTOM are unaffected). Side ids RIGHT=4, LEFT=5.
	 */
	private static int switchLeftRight(int side) {
		if(side == 4) {
			return 5;
		}
		if(side == 5) {
			return 4;
		}
		return side;
	}

	private static int index(byte orientation) {
		int o = orientation & 0xFF;
		return o < ORIENT.length ? o : 0;
	}

	private static Vector3i side(int s) {
		int[] d = SIDE[s];
		return new Vector3i(d[0], d[1], d[2]);
	}
}
