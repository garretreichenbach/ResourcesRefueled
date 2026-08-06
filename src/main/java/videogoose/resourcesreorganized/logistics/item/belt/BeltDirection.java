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

	/**
	 * The six axis directions, interned. Ordered so that opposite sides are adjacent pairs
	 * (FRONT/BACK, TOP/BOTTOM, RIGHT/LEFT), which is what makes {@link #opposite(int)} a single xor.
	 * <p>
	 * <b>These instances are shared.</b> {@link Vector3i} is mutable, but every direction this class
	 * hands out is one of these six &mdash; callers must treat them as read-only. Interning matters
	 * because direction lookups run per item per tick in {@code ConveyorBeltSimulator} and per item per
	 * frame in {@code ConveyorItemDrawer}; allocating a fresh vector for each was pure garbage.
	 */
	private static final Vector3i[] SIDE = {
			new Vector3i(0, 0, 1),   // FRONT
			new Vector3i(0, 0, -1),  // BACK
			new Vector3i(0, 1, 0),   // TOP
			new Vector3i(0, -1, 0),  // BOTTOM
			new Vector3i(1, 0, 0),   // RIGHT
			new Vector3i(-1, 0, 0)   // LEFT
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
	 * The shared unit vector for a side id (FRONT=0..LEFT=5). Never mutate the result &mdash; see
	 * {@link #SIDE}.
	 */
	public static Vector3i side(int sideId) {
		return SIDE[sideId];
	}

	/** Side id facing opposite {@code sideId}. The table pairs opposites, so this is a xor. */
	public static int opposite(int sideId) {
		return sideId ^ 1;
	}

	/**
	 * The shared unit vector equal to {@code (x, y, z)}, which must be one of the six axis directions.
	 * Used to fold computed directions (cross products of two axes) back onto the interned instances.
	 *
	 * @throws IllegalArgumentException if the vector is not a unit axis direction
	 */
	public static Vector3i intern(int x, int y, int z) {
		for(Vector3i v : SIDE) {
			if(v.x == x && v.y == y && v.z == z) {
				return v;
			}
		}
		throw new IllegalArgumentException("not a unit axis direction: (" + x + ", " + y + ", " + z + ")");
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
		return orientationIndex(orientation);
	}

	/**
	 * Normalises an orientation byte into {@code [0, orientationCount())}, clamping out-of-range values
	 * to 0 the same way the decode does. Exposed so per-orientation lookup tables can be indexed with
	 * exactly the same bounds handling.
	 */
	public static int orientationIndex(byte orientation) {
		int o = orientation & 0xFF;
		return o < ORIENT.length ? o : 0;
	}

	/** Number of distinct orientation values the decode table covers. */
	public static int orientationCount() {
		return ORIENT.length;
	}
}
