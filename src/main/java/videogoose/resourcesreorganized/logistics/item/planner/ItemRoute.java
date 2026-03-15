package videogoose.resourcesreorganized.logistics.item.planner;

import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

import java.util.Collections;
import java.util.List;

public final class ItemRoute {

	private final List<String> nodePath;
	private final int maxItemsPerTick;
	private final TransportFamily transportFamily;
	private final int channel;
	private final boolean usesPump;

	public ItemRoute(List<String> nodePath, int maxItemsPerTick, TransportFamily transportFamily, int channel, boolean usesPump) {
		this.nodePath = Collections.unmodifiableList(nodePath);
		this.maxItemsPerTick = Math.max(1, maxItemsPerTick);
		this.transportFamily = transportFamily;
		this.channel = channel;
		this.usesPump = usesPump;
	}

	public List<String> getNodePath() {
		return nodePath;
	}

	public int getMaxItemsPerTick() {
		return maxItemsPerTick;
	}

	public TransportFamily getTransportFamily() {
		return transportFamily;
	}

	public int getChannel() {
		return channel;
	}

	public boolean isUsesPump() {
		return usesPump;
	}
}

