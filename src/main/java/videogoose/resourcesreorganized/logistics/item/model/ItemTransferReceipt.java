package videogoose.resourcesreorganized.logistics.item.model;

import java.util.UUID;

public record ItemTransferReceipt(UUID requestId, ItemTransferOutcome outcome, int movedCount, String message) {

	public static ItemTransferReceipt of(ItemTransferRequest request, ItemTransferOutcome outcome, int movedCount, String message) {
		return new ItemTransferReceipt(request.requestId(), outcome, movedCount, message);
	}
}

