package videogoose.resourcesreorganized.logistics.item.topology;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

/**
 * One connected component of transport blocks (all of the same {@link TransportFamily}).
 * <p>
 * {@link #memberIndices} are the placed transport blocks themselves (conveyors, tubes, pumps).
 * {@link #portIndices} are face-adjacent blocks that expose an inventory and act as logistics endpoints.
 */
public final class ItemTransportNetwork {

	public final TransportFamily family;
	public final LongOpenHashSet memberIndices = new LongOpenHashSet();
	public final LongOpenHashSet portIndices = new LongOpenHashSet();

	public ItemTransportNetwork(TransportFamily family) {
		this.family = family;
	}
}
