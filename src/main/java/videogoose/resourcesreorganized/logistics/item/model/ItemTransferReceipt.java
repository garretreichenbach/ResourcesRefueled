package videogoose.resourcesreorganized.logistics.item.model;

import java.util.UUID;

public final class ItemTransferReceipt {

	private final UUID requestId;
	private final ItemTransferOutcome outcome;
	private final int movedCount;
	private final String message;

	public ItemTransferReceipt(UUID requestId, ItemTransferOutcome outcome, int movedCount, String message) {
		this.requestId = requestId;
		this.outcome = outcome;
		this.movedCount = movedCount;
		this.message = message;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public ItemTransferOutcome getOutcome() {
		return outcome;
	}

	public int getMovedCount() {
		return movedCount;
	}

	public String getMessage() {
		return message;
	}

	public static ItemTransferReceipt of(ItemTransferRequest request, ItemTransferOutcome outcome, int movedCount, String message) {
		return new ItemTransferReceipt(request.getRequestId(), outcome, movedCount, message);
	}
}

