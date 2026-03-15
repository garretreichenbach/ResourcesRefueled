package videogoose.resourcesreorganized.logistics.item.graph;

import java.util.Objects;

public final class ItemEdge {

	private final String fromNodeId;
	private final String toNodeId;
	private final int capacityPerTick;

	public ItemEdge(String fromNodeId, String toNodeId, int capacityPerTick) {
		this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId");
		this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId");
		this.capacityPerTick = Math.max(1, capacityPerTick);
	}

	public String getFromNodeId() {
		return fromNodeId;
	}

	public String getToNodeId() {
		return toNodeId;
	}

	public int getCapacityPerTick() {
		return capacityPerTick;
	}
}

