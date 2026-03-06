package videogoose.resourcesreorganized.gui;

import api.common.GameClient;
import org.schema.game.client.view.gui.GUIInputPanel;
import org.schema.game.client.view.gui.inventory.InventorySlotOverlayElement;
import org.schema.game.client.view.mainmenu.DialogInput;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.TooltipProvider;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.input.InputState;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.systems.FluidPort;
import videogoose.resourcesreorganized.systems.FluidSystemModule;
import videogoose.resourcesreorganized.utils.ReflectionUtils;

public class FluidPortDialog extends DialogInput {

	private final FluidPortPanel inputPanel;

	public FluidPortDialog(SegmentPiece segmentPiece, FluidSystemModule fluidSystemModule) {
		super(GameClient.getClientState());
		inputPanel = new FluidPortPanel(this, segmentPiece, fluidSystemModule);
	}

	@Override
	public void onDeactivate() {

	}

	@Override
	public void handleMouseEvent(MouseEvent mouseEvent) {

	}

	@Override
	public FluidPortPanel getInputPanel() {
		return inputPanel;
	}

	public static class FluidPortPanel extends GUIInputPanel {

		public FluidPortPanel(FluidPortDialog inputPanel, SegmentPiece segmentPiece, FluidSystemModule fluidSystemModule) {
			super("Fluid_Port_Panel", GameClient.getClientState(), inputPanel, "Fluid Port", "");
			createPanel(segmentPiece, fluidSystemModule);
		}

		public void createPanel(SegmentPiece segmentPiece, FluidSystemModule fluidSystemModule) {
			GUIContentPane contentPane = ((GUIDialogWindow) background).getMainContentPane();
			contentPane.setTextBoxHeightLast(300);
			Inventory inventory = fluidSystemModule.getInventory(segmentPiece);
			if(inventory == null) {
				ResourcesReorganized.getInstance().logWarning("Failed to get inventory for FluidPort!");
				return;
			}
			FluidPortAnchor inputAnchor = new FluidPortAnchor(getState(), true, inventory);
			FluidPortAnchor outputAnchor = new FluidPortAnchor(getState(), false, inventory);
			contentPane.getContent(0).attach(inputAnchor);
			contentPane.getContent(0).attach(outputAnchor);
			inputAnchor.orientate(ORIENTATION_LEFT | ORIENTATION_HORIZONTAL_MIDDLE);
			outputAnchor.orientate(ORIENTATION_RIGHT | ORIENTATION_HORIZONTAL_MIDDLE);
		}
	}

	private static class FluidPortAnchor extends GUIAncor implements TooltipProvider {

		private final InventorySlotOverlayElement slotOverlay;
		//Todo: Add visual indication of how much fluid is in this, something like a progress bar or something but for fluid, maybe also add a tooltip that shows the exact amount of fluid in this port and the type of fluid

		public FluidPortAnchor(InputState state, boolean inputMode, Inventory inventory) {
			super(state);
			try {
				ReflectionUtils.setPrivateField(this, "inventory", inventory);
			} catch (Exception exception) {
				ResourcesReorganized.getInstance().logException("Failed to set inventory for FluidPortAnchor", exception);
				throw new RuntimeException(exception);
			}
			slotOverlay = new InventorySlotOverlayElement(false, state, true, this) {
				@Override
				public void onDrop(InventorySlotOverlayElement draggable) {
					if(getSlot() != draggable.getSlot()) {
						if(inputMode) {
							if(FluidPort.isInputItem(draggable.getType())) {
								// Todo: Handle emptying a filled container into system
							}
						} else {
							if(FluidPort.isOutputItem(draggable.getType())) {
								// Todo: Handle filling an empty container into system
							}
						}
					}

					draggable.setStickyDrag(false);
					draggable.setDraggingCount(0);
					draggable.reset();

				}
			};
			slotOverlay.onInit();
			attach(slotOverlay);
		}

		@Override
		public void drawToolTip() {
			slotOverlay.drawToolTip();
		}
	}
}
