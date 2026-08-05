package videogoose.resourcesreorganized.logistics.item.graph;

/**
 * Which transport network a node or edge belongs to.
 * <p>
 * Conveyor belts are the only item transport family. A separate TUBE family existed while belts were
 * horizontal-only and tubes were the way to move items vertically; belts carry items vertically
 * themselves now (see {@code BeltShape.TURN_UP}), so tubes and their pumps were removed.
 */
public enum TransportFamily {
	/** Endpoints that belong to no transport network of their own, such as inventory ports. */
	NEUTRAL,
	CONVEYOR
}
