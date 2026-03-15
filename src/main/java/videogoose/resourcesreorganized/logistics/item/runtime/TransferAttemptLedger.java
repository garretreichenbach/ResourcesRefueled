package videogoose.resourcesreorganized.logistics.item.runtime;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TransferAttemptLedger {

	private final Map<UUID, Integer> attemptsByRequest = new HashMap<UUID, Integer>();

	public int increment(ItemTransferRequest request) {
		UUID key = request.getRequestId();
		int next = attemptsByRequest.getOrDefault(key, 0) + 1;
		attemptsByRequest.put(key, next);
		return next;
	}

	public void clear(ItemTransferRequest request) {
		attemptsByRequest.remove(request.getRequestId());
	}
}

