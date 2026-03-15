package videogoose.resourcesreorganized.logistics.fluid;

import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;
import videogoose.resourcesreorganized.data.FluidMeta;
import videogoose.resourcesreorganized.systems.FluidSystemModule;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class FluidPortTickProcessor {

	private FluidPortTickProcessor() {
	}

	public static boolean process(SegmentController segmentController, Map<Long, FluidSystemModule.FluidPortSegment> portSegments, List<FluidSystemModule.FluidNetwork> networks, double unitsPerCanister, double bufferCapacity, short canisterId, Consumer<String> debugLogger) {
		boolean anyChange = false;
		if(portSegments.isEmpty()) {
			return false;
		}

		for(FluidSystemModule.FluidPortSegment port : portSegments.values()) {
			SegmentPiece piece = segmentController.getSegmentBuffer().getPointUnsave(port.blockIndex);
			if(piece == null || !piece.isActive()) {
				continue;
			}

			Inventory inv = getInventory(piece);
			if(inv == null) {
				continue;
			}

			InventorySlot inputSlot = inv.getSlot(FluidSystemModule.INPUT_SLOT_INDEX);
			if(inputSlot != null && inputSlot.getType() == canisterId && FluidMeta.isFilled(inputSlot)) {
				short sourceFluidId = FluidMeta.getFluidId(inputSlot);
				double actualAmount = FluidMeta.getFluidAmount(inputSlot);
				double slotCapacity = FluidMeta.getCapacity(inputSlot);
				double spaceInBuffer = bufferCapacity - port.bufferLevel;
				boolean fluidCompatible = port.bufferFluidId == 0 || port.bufferFluidId == sourceFluidId;
				double toDrain = Math.min(actualAmount, spaceInBuffer);
				if(toDrain > 0 && fluidCompatible) {
					double remaining = actualAmount - toDrain;
					if(remaining <= 0) {
						FluidMeta.writeEmpty(inputSlot);
					} else {
						FluidMeta.writeFilledSlot(inputSlot, sourceFluidId, remaining, slotCapacity);
					}
					port.bufferLevel += toDrain;
					if(port.bufferFluidId == 0) {
						port.bufferFluidId = sourceFluidId;
					}
					anyChange = true;
					if(debugLogger != null) {
						debugLogger.accept("[FluidPort] @ " + port.blockIndex + " drained " + toDrain + " units from canister (fluid=" + sourceFluidId + ") into buffer. Buffer now at " + port.bufferLevel + "/" + bufferCapacity);
					}
				}
			}

			int emptySlotId = FluidMeta.findEmptySlot(inv, canisterId, FluidSystemModule.OUTPUT_SLOT_INDEX);
			if(emptySlotId >= 0 && port.bufferLevel >= unitsPerCanister) {
				short bufferFluidId = port.bufferFluidId;
				double toFill = Math.min(port.bufferLevel, unitsPerCanister);
				port.bufferLevel -= toFill;
				if(port.bufferLevel <= 0) {
					port.bufferLevel = 0;
					port.bufferFluidId = 0;
				}
				InventorySlot emptySlot = inv.getSlot(emptySlotId);
				FluidMeta.writeFilledSlot(emptySlot, bufferFluidId, toFill, unitsPerCanister);
				anyChange = true;
				if(debugLogger != null) {
					debugLogger.accept("[FluidPort] @ " + port.blockIndex + " filled canister with " + toFill + " units of fluid=" + bufferFluidId + " from buffer. Buffer now at " + port.bufferLevel + "/" + bufferCapacity);
				}
			}

			for(long nb : FluidTopologyUtils.faceAdjacentIndices(port.blockIndex)) {
				for(FluidSystemModule.FluidNetwork net : networks) {
					if(!net.memberIndices.contains(nb)) {
						continue;
					}

					if(port.bufferLevel > 0 && net.fluidLevel < net.tankCapacity) {
						if(net.fluidId == 0 || net.fluidId == port.bufferFluidId) {
							double transfer = Math.min(port.bufferLevel, net.tankCapacity - net.fluidLevel);
							port.bufferLevel -= transfer;
							net.fluidLevel += transfer;
							if(net.fluidId == 0) {
								net.fluidId = port.bufferFluidId;
							}
							if(port.bufferLevel <= 0) {
								port.bufferLevel = 0;
								port.bufferFluidId = 0;
							}
							anyChange = true;
							if(debugLogger != null) {
								debugLogger.accept("[FluidPort] @ " + port.blockIndex + " passively pushed " + transfer + " units to adjacent network. Network now at " + net.fluidLevel + "/" + net.tankCapacity);
							}
						}
					}

					if(net.fluidLevel > 0 && port.bufferLevel < bufferCapacity) {
						if(port.bufferFluidId == 0 || port.bufferFluidId == net.fluidId) {
							double transfer = Math.min(net.fluidLevel, bufferCapacity - port.bufferLevel);
							net.fluidLevel -= transfer;
							port.bufferLevel += transfer;
							if(port.bufferFluidId == 0) {
								port.bufferFluidId = net.fluidId;
							}
							if(net.fluidLevel <= 0) {
								net.fluidLevel = 0;
								net.fluidId = 0;
							}
							anyChange = true;
							if(debugLogger != null) {
								debugLogger.accept("[FluidPort] @ " + port.blockIndex + " passively pulled " + transfer + " units from adjacent network. Buffer now at " + port.bufferLevel + "/" + bufferCapacity);
							}
						}
					}
					break;
				}
			}
		}

		return anyChange;
	}

	private static Inventory getInventory(SegmentPiece segmentPiece) {
		if(segmentPiece.getSegmentController() instanceof ManagedUsableSegmentController<?>) {
			ManagedUsableSegmentController<?> controller = (ManagedUsableSegmentController<?>) segmentPiece.getSegmentController();
			return controller.getManagerContainer().getInventory(segmentPiece.getAbsoluteIndex());
		}
		return null;
	}
}

