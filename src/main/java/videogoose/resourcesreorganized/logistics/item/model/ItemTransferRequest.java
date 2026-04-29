package videogoose.resourcesreorganized.logistics.item.model;

import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

import java.util.Objects;
import java.util.UUID;

public record ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType,
                                  int metaId, int count, long enqueueTick, boolean allowVanillaFallback,
                                  TransportFamily transportFamily, int channel, boolean allowVertical,
                                  boolean requirePump, boolean allowDirectInventoryAdjacency,
                                  boolean sourceRequiresInventoryPort, boolean destinationRequiresInventoryPort) {

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, TransportFamily.CONVEYOR, -1, false, false, true, false);
	}

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowVertical, boolean requirePump) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowVertical, requirePump, true, false);
	}

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowVertical, boolean requirePump, boolean allowDirectInventoryAdjacency, boolean requireInventoryPort) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowVertical, requirePump, allowDirectInventoryAdjacency, requireInventoryPort, requireInventoryPort);
	}

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowVertical, boolean requirePump, boolean allowDirectInventoryAdjacency, boolean sourceRequiresInventoryPort, boolean destinationRequiresInventoryPort) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowVertical, requirePump, allowDirectInventoryAdjacency, sourceRequiresInventoryPort, destinationRequiresInventoryPort);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback) {
		this(requestId, sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, TransportFamily.CONVEYOR, -1, false, false, true, false);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowVertical, boolean requirePump, boolean allowDirectInventoryAdjacency, boolean requireInventoryPort) {
		this(requestId, sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback, transportFamily, channel, allowVertical, requirePump, allowDirectInventoryAdjacency, requireInventoryPort, requireInventoryPort);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback, TransportFamily transportFamily, int channel, boolean allowVertical, boolean requirePump, boolean allowDirectInventoryAdjacency, boolean sourceRequiresInventoryPort, boolean destinationRequiresInventoryPort) {
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
		this.destinationNodeId = Objects.requireNonNull(destinationNodeId, "destinationNodeId");
		this.itemType = itemType;
		this.metaId = metaId;
		this.count = Math.max(0, count);
		this.enqueueTick = enqueueTick;
		this.allowVanillaFallback = allowVanillaFallback;
		this.transportFamily = Objects.requireNonNull(transportFamily, "transportFamily");
		this.channel = channel;
		this.allowVertical = allowVertical;
		this.requirePump = requirePump;
		this.allowDirectInventoryAdjacency = allowDirectInventoryAdjacency;
		this.sourceRequiresInventoryPort = sourceRequiresInventoryPort;
		this.destinationRequiresInventoryPort = destinationRequiresInventoryPort;
	}


	public boolean isRequireInventoryPort() {
		return sourceRequiresInventoryPort || destinationRequiresInventoryPort;
	}

	public ItemTransferRequest withCount(int newCount) {
		return new ItemTransferRequest(requestId, sourceNodeId, destinationNodeId, itemType, metaId, newCount, enqueueTick, allowVanillaFallback, transportFamily, channel, allowVertical, requirePump, allowDirectInventoryAdjacency, sourceRequiresInventoryPort, destinationRequiresInventoryPort);
	}
}

