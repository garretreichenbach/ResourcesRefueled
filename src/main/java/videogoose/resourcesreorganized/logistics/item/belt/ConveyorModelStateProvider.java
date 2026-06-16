package videogoose.resourcesreorganized.logistics.item.belt;

import api.utils.game.lodvisualstate.IBlockVisualStateProvider;
import api.utils.game.lodvisualstate.StateResult;
import api.utils.game.lodvisualstate.VisualStateContext;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.SegmentBufferManager;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import videogoose.resourcesreorganized.element.ElementRegistry;

/**
 * Selects which conveyor belt model renders for a placed block based on its neighbours along the
 * flow axis (forward/backward per {@link BeltDirection}). See {@link ConveyorModelStates} for the
 * state &rarr; model mapping. Runs client-side during visual-state resolution; states whose model
 * isn't loaded yet downgrade to a loaded fallback so rendering never references a missing mesh.
 */
public final class ConveyorModelStateProvider implements IBlockVisualStateProvider {

	@Override
	public String getProviderId() {
		return "resourcesreorganized.conveyor_model";
	}

	@Override
	public int getPriority() {
		return 10;
	}

	@Override
	public boolean supports(short blockType) {
		return blockType == ElementRegistry.CONVEYOR_BELT.getId();
	}

	@Override
	public StateResult resolve(VisualStateContext context) {
		if(!(context.controller instanceof SegmentController controller)) {
			return null;
		}
		long forward = BeltDirection.forwardIndex(context.absPos, context.orientation);
		long backward = BeltDirection.backwardIndex(context.absPos, context.orientation);

		boolean forwardBelt = isConveyor(controller, forward);
		boolean backwardBelt = isConveyor(controller, backward);
		boolean inventoryAdjacent = hasInventory(controller, forward) || hasInventory(controller, backward);
		int beltConnections = (forwardBelt ? 1 : 0) + (backwardBelt ? 1 : 0);

		String state;
		if(inventoryAdjacent) {
			state = (beltConnections >= 1) ? ConveyorModelStates.PORT_MID : ConveyorModelStates.PORT;
		} else if(beltConnections == 0) {
			state = ConveyorModelStates.SINGLE;
		} else if(beltConnections == 1) {
			state = ConveyorModelStates.END;
		} else {
			state = ConveyorModelStates.MID;
		}

		return StateResult.local(ConveyorModelStates.loadedOrFallback(state), null, 0);
	}

	private static boolean isConveyor(SegmentController controller, long index) {
		return typeAt(controller, index) == ElementRegistry.CONVEYOR_BELT.getId();
	}

	private static short typeAt(SegmentController controller, long index) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		SegmentPiece piece = ((SegmentBufferManager) controller.getSegmentBuffer()).getPointUnsave(pos);
		return piece == null ? 0 : piece.getType();
	}

	private static boolean hasInventory(SegmentController controller, long index) {
		if(controller instanceof ManagedUsableSegmentController<?> managed) {
			return managed.getManagerContainer().getInventory(index) != null;
		}
		return false;
	}
}
