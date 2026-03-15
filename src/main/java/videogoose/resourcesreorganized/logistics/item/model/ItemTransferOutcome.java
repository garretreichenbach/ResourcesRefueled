package videogoose.resourcesreorganized.logistics.item.model;

public enum ItemTransferOutcome {
	SUCCESS,
	PARTIAL,
	NO_ROUTE,
	RETRY_QUEUED,
	FAILED,
	FALLBACK_TO_VANILLA
}

