package videogoose.resourcesreorganized.logistics.item.model;

import java.util.Objects;
import java.util.UUID;

public final class ItemTransferRequest {

	private final UUID requestId;
	private final String sourceNodeId;
	private final String destinationNodeId;
	private final short itemType;
	private final int metaId;
	private final int count;
	private final long enqueueTick;
	private final boolean allowVanillaFallback;

	public ItemTransferRequest(String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback) {
		this(UUID.randomUUID(), sourceNodeId, destinationNodeId, itemType, metaId, count, enqueueTick, allowVanillaFallback);
	}

	public ItemTransferRequest(UUID requestId, String sourceNodeId, String destinationNodeId, short itemType, int metaId, int count, long enqueueTick, boolean allowVanillaFallback) {
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
		this.destinationNodeId = Objects.requireNonNull(destinationNodeId, "destinationNodeId");
		this.itemType = itemType;
		this.metaId = metaId;
		this.count = Math.max(0, count);
		this.enqueueTick = enqueueTick;
		this.allowVanillaFallback = allowVanillaFallback;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public String getSourceNodeId() {
		return sourceNodeId;
	}

	public String getDestinationNodeId() {
		return destinationNodeId;
	}

	public short getItemType() {
		return itemType;
	}

	public int getMetaId() {
		return metaId;
	}

	public int getCount() {
		return count;
	}

	public long getEnqueueTick() {
		return enqueueTick;
	}

	public boolean isAllowVanillaFallback() {
		return allowVanillaFallback;
	}

	public ItemTransferRequest withCount(int newCount) {
		return new ItemTransferRequest(requestId, sourceNodeId, destinationNodeId, itemType, metaId, newCount, enqueueTick, allowVanillaFallback);
	}
}

