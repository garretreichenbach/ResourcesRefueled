package videogoose.resourcesreorganized.logistics.item.belt;

import videogoose.resourcesreorganized.manager.ResourceManager;

/**
 * Visual-state keys for the conveyor belt block and the mod models they map to.
 * <p>
 * The belt is one block whose rendered model changes with neighbour connectivity:
 * <ul>
 *     <li>{@link #SINGLE} &mdash; isolated, rounded both ends.</li>
 *     <li>{@link #END} &mdash; rounded one end, the other connects to a belt.</li>
 *     <li>{@link #MID} &mdash; mid-line, rounded on neither end.</li>
 *     <li>{@link #PORT} &mdash; an end touching an inventory it pulls from / pushes to (frame on the inventory face).</li>
 *     <li>{@link #PORT_MID} &mdash; like {@link #PORT} but mid-line (no rounded end).</li>
 * </ul>
 */
public final class ConveyorModelStates {

	public static final String SINGLE = "single";
	public static final String END = "end";
	public static final String MID = "mid";
	public static final String PORT = "port";
	public static final String PORT_MID = "port_mid";

	public static final String MODEL_SINGLE = "ConveyorBeltSingle";
	public static final String MODEL_END = "ConveyorBeltEnd";
	public static final String MODEL_MID = "ConveyorBeltMid";
	public static final String MODEL_PORT = "ConveyorBeltPort";
	public static final String MODEL_PORT_MID = "ConveyorBeltPortMid";

	private ConveyorModelStates() {
	}

	/** Model resource name backing a state key, or the single-belt model for an unknown key. */
	public static String modelName(String stateKey) {
		return switch(stateKey) {
			case END -> MODEL_END;
			case MID -> MODEL_MID;
			case PORT -> MODEL_PORT;
			case PORT_MID -> MODEL_PORT_MID;
			default -> MODEL_SINGLE;
		};
	}

	/**
	 * Returns {@code stateKey} if its model is loaded, otherwise the closest loaded fallback
	 * (port_mid&rarr;port, mid&rarr;end, then single). Keeps rendering crash-safe while some
	 * model zips are still missing.
	 */
	public static String loadedOrFallback(String stateKey) {
		if(ResourceManager.isModelLoaded(modelName(stateKey))) {
			return stateKey;
		}
		String fallback = switch(stateKey) {
			case PORT_MID -> PORT;
			case MID -> END;
			default -> SINGLE;
		};
		if(fallback.equals(stateKey)) {
			return SINGLE;
		}
		return loadedOrFallback(fallback);
	}
}
