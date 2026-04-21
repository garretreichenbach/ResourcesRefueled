package videogoose.resourcesreorganized.logistics.item.planner;

import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

import java.util.Collections;
import java.util.List;

public record ItemRoute(List<String> nodePath, int maxItemsPerTick, TransportFamily transportFamily, int channel, boolean usesPump) {

	public ItemRoute(List<String> nodePath, int maxItemsPerTick, TransportFamily transportFamily, int channel, boolean usesPump) {
		this.nodePath = Collections.unmodifiableList(nodePath);
		this.maxItemsPerTick = Math.max(1, maxItemsPerTick);
		this.transportFamily = transportFamily;
		this.channel = channel;
		this.usesPump = usesPump;
	}
}

