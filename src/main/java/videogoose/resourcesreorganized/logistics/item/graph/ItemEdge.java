package videogoose.resourcesreorganized.logistics.item.graph;

import java.util.Objects;

public final class ItemEdge {

	private final String fromNodeId;
	private final String toNodeId;
	private final int capacityPerTick;
	private final TransportFamily transportFamily;
	private final boolean vertical;
	private final int channelMask;

	public ItemEdge(String fromNodeId, String toNodeId, int capacityPerTick) {
		this(fromNodeId, toNodeId, capacityPerTick, TransportFamily.NEUTRAL, false, -1);
	}

	public ItemEdge(String fromNodeId, String toNodeId, int capacityPerTick, TransportFamily transportFamily, boolean vertical, int channelMask) {
		this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId");
		this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId");
		this.capacityPerTick = Math.max(1, capacityPerTick);
		this.transportFamily = Objects.requireNonNull(transportFamily, "transportFamily");
		this.vertical = vertical;
		this.channelMask = channelMask;
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

	public TransportFamily getTransportFamily() {
		return transportFamily;
	}

	public boolean isVertical() {
		return vertical;
	}

	public boolean supportsChannel(int channel) {
		if(channel < 0 || channelMask == -1) {
			return true;
		}
		return (channelMask & (1 << channel)) != 0;
	}
}

