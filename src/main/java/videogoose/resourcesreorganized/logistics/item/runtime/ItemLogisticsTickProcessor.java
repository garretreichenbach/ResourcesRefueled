package videogoose.resourcesreorganized.logistics.item.runtime;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferOutcome;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.planner.ItemRoute;
import videogoose.resourcesreorganized.logistics.item.planner.ItemRoutePlanner;
import videogoose.resourcesreorganized.logistics.item.queue.DeferredTransferQueue;
import videogoose.resourcesreorganized.logistics.item.queue.ItemTransferQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ItemLogisticsTickProcessor {

	private final ItemTransferQueue queue;
	private final DeferredTransferQueue deferredQueue;
	private final TransferAttemptLedger attemptLedger;
	private final ItemRoutePlanner routePlanner;
	private final ItemTransferExecutor transferExecutor;
	private final LogisticsFailOpenPolicy failOpenPolicy;
	private final ItemLogisticsDiagnostics diagnostics;
	private final int transfersPerTick;
	private final int retryDelayTicks;
	private final int maxAttempts;

	public ItemLogisticsTickProcessor(ItemTransferQueue queue, DeferredTransferQueue deferredQueue, TransferAttemptLedger attemptLedger, ItemRoutePlanner routePlanner, ItemTransferExecutor transferExecutor, LogisticsFailOpenPolicy failOpenPolicy, ItemLogisticsDiagnostics diagnostics, int transfersPerTick, int retryDelayTicks, int maxAttempts) {
		this.queue = queue;
		this.deferredQueue = deferredQueue;
		this.attemptLedger = attemptLedger;
		this.routePlanner = routePlanner;
		this.transferExecutor = transferExecutor;
		this.failOpenPolicy = failOpenPolicy;
		this.diagnostics = diagnostics;
		this.transfersPerTick = Math.max(1, transfersPerTick);
		this.retryDelayTicks = Math.max(1, retryDelayTicks);
		this.maxAttempts = Math.max(1, maxAttempts);
	}

	public List<ItemTransferReceipt> tick(long currentTick) {
		promoteDeferred(currentTick);
		List<ItemTransferReceipt> receipts = new ArrayList<ItemTransferReceipt>();
		for(int i = 0; i < transfersPerTick; i++) {
			Optional<ItemTransferRequest> requestOpt = queue.poll();
			if(requestOpt.isEmpty()) {
				break;
			}
			receipts.add(processOne(requestOpt.get(), currentTick));
		}
		return receipts;
	}

	private void promoteDeferred(long currentTick) {
		while(true) {
			Optional<ItemTransferRequest> deferred = deferredQueue.pollReady(currentTick);
			if(deferred.isEmpty()) {
				break;
			}
			queue.offer(deferred.get());
		}
	}

	private ItemTransferReceipt processOne(ItemTransferRequest request, long currentTick) {
		Optional<ItemRoute> route = routePlanner.planRoute(request);
		if(route.isEmpty()) {
			diagnostics.recordNoRoute();
			return retryOrFail(request, currentTick, "no-route family=" + request.getTransportFamily() + " channel=" + request.getChannel());
		}

		ItemTransferReceipt result = failOpenPolicy.execute(() -> transferExecutor.execute(request, route.get(), currentTick), () -> ItemTransferReceipt.of(request, ItemTransferOutcome.FALLBACK_TO_VANILLA, 0, "fail-open"));

		if(result.getOutcome() == ItemTransferOutcome.SUCCESS) {
			attemptLedger.clear(request);
			diagnostics.recordCompleted();
			return result;
		}

		if(result.getOutcome() == ItemTransferOutcome.PARTIAL) {
			diagnostics.recordPartial();
			int remaining = Math.max(0, request.getCount() - result.getMovedCount());
			if(remaining > 0) {
				queue.offer(request.withCount(remaining));
			}
			return result;
		}

		if(result.getOutcome() == ItemTransferOutcome.FALLBACK_TO_VANILLA) {
			attemptLedger.clear(request);
			return result;
		}

		diagnostics.recordFailed();
		return retryOrFail(request, currentTick, result.getMessage());
	}

	private ItemTransferReceipt retryOrFail(ItemTransferRequest request, long currentTick, String reason) {
		int attempts = attemptLedger.increment(request);
		if(attempts >= maxAttempts) {
			attemptLedger.clear(request);
			return ItemTransferReceipt.of(request, ItemTransferOutcome.FAILED, 0, reason + " max-attempts");
		}
		deferredQueue.defer(currentTick + retryDelayTicks, request);
		diagnostics.recordDeferred();
		return ItemTransferReceipt.of(request, ItemTransferOutcome.RETRY_QUEUED, 0, reason + " retry=" + attempts);
	}
}

