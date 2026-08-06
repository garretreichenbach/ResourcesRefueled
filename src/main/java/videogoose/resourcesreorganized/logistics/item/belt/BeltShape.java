package videogoose.resourcesreorganized.logistics.item.belt;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.element.ElementCollection;

/**
 * The routing shapes a conveyor belt can take.
 * <p>
 * Each shape is <b>its own block id</b> with its own fixed model, so the player chooses the routing
 * when placing rather than the mod inferring it from neighbours. That keeps the engine's stock draw
 * path (which resolves a block's model with no positional context) correct with no game patch, and
 * it makes the flow through a cell explicit: every shape declares which face items enter through and
 * which they leave through.
 * <p>
 * Directions are derived from the block's NORMAL24 orientation via {@link BeltDirection}: {@code F} =
 * forward (secondary side) and {@code U} = up (primary side). Models are authored in the same
 * convention the engine rotates them by &mdash; model {@code +Z} lands on {@code F}, model {@code +Y}
 * on {@code U} &mdash; so a shape's entry/exit legs are fixed offsets in that frame:
 * <ul>
 *     <li>{@link #STRAIGHT}: in along {@code F}, out along {@code F}.</li>
 *     <li>{@link #TURN_LEFT}: in along {@code U × F}, out along {@code F} (90&deg; turn in the belt surface).</li>
 *     <li>{@link #TURN_RIGHT}: in along {@code F × U}, out along {@code F} (the mirror of TURN_LEFT).</li>
 *     <li>{@link #TURN_UP}: in along {@code F}, out along {@code U} (90&deg; turn out through the top face).</li>
 * </ul>
 * A downward turn needs its own mesh (mirrored through the belt plane) and can be added here as a
 * fifth constant plus a block class; {@code TURN_UP} placed upside down would carry the stack on the
 * underside of the entry leg, so it is not a substitute.
 */
public enum BeltShape {

	STRAIGHT("conveyor/ConveyorStraight", 3, 0),
	// NOTE THE MODEL NAMES: the two corner meshes are misnamed by the author. Read straight out of the
	// OBJ vertex data, ConveyorCornerLeft's open ports are -X/+Z — entering through -X means travelling
	// +X and leaving +Z, and since left = up x direction, that is a RIGHT turn. ConveyorCornerRight
	// (once its half turn is applied) opens +X/+Z, i.e. a LEFT turn. So each constant loads the mesh
	// whose name is the opposite of its behaviour. Swapping the file names in the resources folder would
	// be undone by the next re-export from the author, so the correction lives here.
	// Authored entering through -X and leaving through -Z; a half turn puts those on +X / +Z.
	TURN_LEFT("conveyor/ConveyorCornerRight", 3, 2),
	TURN_RIGHT("conveyor/ConveyorCornerLeft", 3, 0),
	// The vertical bend fills its cell (its mesh spans the full block height), so it collides as a cube.
	// Authored entering through -X, so its column sits at the +X end; 270 degrees swings that to +Z.
	TURN_UP("conveyor/ConveyorUp", 0, 3);

	/**
	 * Block type -&gt; shape. Written once during block registration (each belt block registers itself
	 * in {@code postInitData}, after ids are assigned) and read-only from then on.
	 */
	private static final Short2ObjectOpenHashMap<BeltShape> BY_BLOCK_TYPE = new Short2ObjectOpenHashMap<>();

	private final String modelName;
	private final int collisionSlab;
	private final int modelYawSteps;

	/**
	 * Entry and exit directions per orientation, indexed {@code [orientation][face]}. Precomputed once
	 * because these are read per item per tick by the simulator and per item per frame by the renderer;
	 * deriving them on each call allocated a vector every time. Only
	 * {@code BeltDirection.orientationCount() * 4} entries exist, so the whole table is trivial.
	 * <p>
	 * Every vector in here is one of {@link BeltDirection}'s interned instances, so the table is shared
	 * and <b>read-only</b>.
	 */
	private Vector3i[][] entryFlows;
	private Vector3i[][] exitFlows;

	BeltShape(String modelName, int collisionSlab, int modelYawSteps) {
		this.modelName = modelName;
		this.collisionSlab = collisionSlab;
		this.modelYawSteps = modelYawSteps;
	}

	static {
		// Runs after every constant is constructed, so buildFlowTables can switch on the constant.
		for(BeltShape shape : values()) {
			shape.buildFlowTables();
		}
	}

	/**
	 * Derives this shape's entry/exit faces for every orientation. The per-shape geometry lives here
	 * and nowhere else &mdash; a multi-face shape supplies more than one entry or exit and the rest of
	 * the system iterates without needing to know which shape it is looking at.
	 */
	private void buildFlowTables() {
		int orientations = BeltDirection.orientationCount();
		entryFlows = new Vector3i[orientations][];
		exitFlows = new Vector3i[orientations][];
		for(int o = 0; o < orientations; o++) {
			byte orientation = (byte) o;
			Vector3i forward = BeltDirection.offset(orientation);
			Vector3i up = BeltDirection.up(orientation);
			entryFlows[o] = switch(this) {
				// The entry FACE normal is the opposite of the entry flow (the feeding cell sits on that
				// side), so a shape whose mesh opens toward +X must have an entry flow of -X. Getting this
				// sign backwards mirrors the entry onto the block's solid wall while the real opening goes
				// unused — verified against the mesh ports for all 24 orientations, see PortCheck.
				case TURN_LEFT -> new Vector3i[]{cross(forward, up)};
				case TURN_RIGHT -> new Vector3i[]{cross(up, forward)};
				// STRAIGHT and TURN_UP both take their input through the back face.
				default -> new Vector3i[]{forward};
			};
			exitFlows[o] = new Vector3i[]{this == TURN_UP ? up : forward};
		}
	}

	/** Resource name of this shape's mesh, relative to {@code src/main/resources/models}. */
	public String modelName() {
		return modelName;
	}

	/**
	 * Collision box height for this shape, matching how much of the cell its mesh actually fills:
	 * {@code 0} = full cube, {@code 3} = 1/4 block. The physics pipeline reads
	 * {@code ElementInformation.getSlab(orientation)} independently of the rendered mesh, so the flat
	 * shapes get a thin floor slab while the vertical bend keeps a full cube.
	 */
	public int collisionSlab() {
		return collisionSlab;
	}

	/**
	 * Quarter turns about {@code +Y} to apply to the authored mesh at load time to bring it into the
	 * convention this enum assumes: the block's <b>entry port faces {@code -Z}</b> (or {@code -X} for a
	 * turn whose entry leg runs sideways &mdash; see {@link #entryFlow}) and its <b>exit port faces
	 * {@code +Z}</b>, with {@code +Y} up. The engine then rotates the whole thing by the block's
	 * orientation, so keeping the exit on {@code +Z} is what makes the vanilla build orientation arrow
	 * (which points along the block's forward face) point at the belt's actual output.
	 * <p>
	 * These are non-zero only because the supplied models were authored to a different convention: the
	 * turns all enter through {@code -X} while the straight belt enters through {@code -Z}. Correcting it
	 * here rather than rotating the {@code .obj} files keeps the adjustment visible and version
	 * controlled &mdash; a rotation baked into the geometry is invisible and is lost on re-export. If a
	 * model is ever re-authored to the convention above, set its value back to 0.
	 */
	public int modelYawSteps() {
		return modelYawSteps;
	}

	/** Binds a block id to its shape. Called by each conveyor block once its id has been assigned. */
	public static void register(short blockType, BeltShape shape) {
		BY_BLOCK_TYPE.put(blockType, shape);
	}

	/** Shape of a conveyor belt block type, or {@code null} if the type is not a belt. */
	public static BeltShape of(short blockType) {
		return BY_BLOCK_TYPE.get(blockType);
	}

	/** Shape of a belt block type, falling back to {@link #STRAIGHT} for an unknown type. */
	public static BeltShape orStraight(short blockType) {
		BeltShape shape = BY_BLOCK_TYPE.get(blockType);
		return shape != null ? shape : STRAIGHT;
	}

	/** Whether {@code blockType} is one of the conveyor belt shapes. */
	public static boolean isBelt(short blockType) {
		return BY_BLOCK_TYPE.containsKey(blockType);
	}

	/**
	 * How many faces items may <b>enter</b> through. Every shape here is 1-in/1-out; a merger or
	 * splitter reports more, and the simulator and renderer iterate rather than assuming a single face.
	 */
	public int entryCount() {
		return entryFlows[0].length;
	}

	/** How many faces items may <b>leave</b> through. See {@link #entryCount()}. */
	public int exitCount() {
		return exitFlows[0].length;
	}

	/**
	 * Direction items travel while <b>entering</b> through entry face {@code i}.
	 * <p>
	 * The returned vector is interned and <b>must not be mutated</b> &mdash; see {@link BeltDirection}.
	 */
	public Vector3i entryFlow(byte orientation, int i) {
		return entryFlows[BeltDirection.orientationIndex(orientation)][i];
	}

	/** Direction items travel while <b>leaving</b> through exit face {@code i}. Interned; read-only. */
	public Vector3i exitFlow(byte orientation, int i) {
		return exitFlows[BeltDirection.orientationIndex(orientation)][i];
	}

	/**
	 * Primary entry direction &mdash; entry face 0. Callers that can only express a single direction
	 * (the build orientation arrow, the debug readout) use this; anything that routes items must
	 * iterate all {@link #entryCount()} faces instead.
	 */
	public Vector3i entryFlow(byte orientation) {
		return entryFlow(orientation, 0);
	}

	/** Primary exit direction &mdash; exit face 0. See {@link #entryFlow(byte)}. */
	public Vector3i exitFlow(byte orientation) {
		return exitFlow(orientation, 0);
	}

	/**
	 * Side id (FRONT=0..LEFT=5) of the face items leave through &mdash; the id form of
	 * {@link #exitFlow}. Used to tell the build-mode orientation arrow which way this belt outputs,
	 * which for {@link #TURN_UP} is its top face rather than its forward face.
	 */
	public int exitSide(byte orientation) {
		return this == TURN_UP ? BeltDirection.upSide(orientation) : BeltDirection.forwardSide(orientation);
	}

	/**
	 * Outward normal of the belt surface at {@code progress} (0 = entry face, 1 = exit face). Used to
	 * sit rendered stacks on top of the belt; on {@link #TURN_UP}'s vertical leg the surface faces back
	 * the way the stack arrived.
	 */
	public Vector3i surfaceNormal(byte orientation, float progress) {
		if(this == TURN_UP && progress >= 0.5f) {
			return BeltDirection.side(BeltDirection.opposite(BeltDirection.forwardSide(orientation)));
		}
		return BeltDirection.up(orientation);
	}

	/** Block index of the cell behind entry face {@code i}, i.e. one that can feed this cell. */
	public long inputIndex(long index, byte orientation, int i) {
		return offsetIndex(index, entryFlow(orientation, i), -1);
	}

	/** Block index of the cell past exit face {@code i}, i.e. one this cell can feed. */
	public long outputIndex(long index, byte orientation, int i) {
		return offsetIndex(index, exitFlow(orientation, i), 1);
	}

	/** Block index behind the primary entry face. See {@link #entryFlow(byte)}. */
	public long inputIndex(long index, byte orientation) {
		return inputIndex(index, orientation, 0);
	}

	/** Block index past the primary exit face. See {@link #exitFlow(byte)}. */
	public long outputIndex(long index, byte orientation) {
		return outputIndex(index, orientation, 0);
	}

	/**
	 * Whether a belt of this shape at {@code index}/{@code orientation} takes its input from
	 * {@code fromIndex} through <b>any</b> of its entry faces. Items are only handed over through a
	 * declared entry face, so a mis-aimed belt backs up instead of quietly accepting from the side.
	 */
	public boolean acceptsFrom(long index, byte orientation, long fromIndex) {
		for(int i = 0, n = entryCount(); i < n; i++) {
			if(inputIndex(index, orientation, i) == fromIndex) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Block index one step from {@code index} along {@code direction}. {@code sign} is {@code -1} to
	 * step backwards (an entry face points the way items arrive, so the feeding cell is behind it).
	 */
	private static long offsetIndex(long index, Vector3i direction, int sign) {
		Vector3i pos = SCRATCH_POS.get();
		ElementCollection.getPosFromIndex(index, pos);
		return ElementCollection.getIndex(
				pos.x + direction.x * sign,
				pos.y + direction.y * sign,
				pos.z + direction.z * sign);
	}

	/**
	 * Scratch position for index maths. {@link ElementCollection#getPosFromIndex} needs somewhere to
	 * write, and this runs per item per tick on the sim thread and per item per frame on the render
	 * thread &mdash; hence thread-local rather than a shared field.
	 */
	private static final ThreadLocal<Vector3i> SCRATCH_POS = ThreadLocal.withInitial(Vector3i::new);

	/** Cross product of two axis directions, folded back onto {@link BeltDirection}'s interned vectors. */
	private static Vector3i cross(Vector3i a, Vector3i b) {
		return BeltDirection.intern(
				a.y * b.z - a.z * b.y,
				a.z * b.x - a.x * b.z,
				a.x * b.y - a.y * b.x);
	}
}
