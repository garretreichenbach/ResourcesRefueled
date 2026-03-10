package videogoose.resourcesreorganized.gui;

import api.common.GameClient;
import org.schema.game.client.controller.PlayerInput;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.GUIInputPanel;
import org.schema.game.client.view.gui.inventory.InventorySlotOverlayElement;
import org.schema.game.client.view.gui.inventory.SingleInventorySlotIcon;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.player.inventory.StashInventory;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.Draggable;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.systems.FluidSystemModule;

public class FluidPortDialog extends PlayerInput {

	public static final int WIDTH = 500;
	public static final int HEIGHT = 300;

	private final FluidPortPanel fluidPortPanel;

	public FluidPortDialog(FluidSystemModule fluidSystemModule, SegmentPiece segmentPiece) {
		super(GameClient.getClientState());
		fluidPortPanel = new FluidPortPanel(getState(), this, fluidSystemModule, segmentPiece);
	}

	@Override
	public void activate() {
		super.activate();
		GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getInventoryControlManager().setActive(true);
	}

	@Override
	public void onDeactivate() {

	}

	@Override
	public void handleMouseEvent(MouseEvent mouseEvent) {

	}

	@Override
	public FluidPortPanel getInputPanel() {
		return fluidPortPanel;
	}

	public static class FluidPortPanel extends GUIInputPanel {

		private final FluidSystemModule module;
		private final SegmentPiece segmentPiece;
		private SingleInventorySlotIcon inputSlot;
		private SingleInventorySlotIcon outputSlot;

		public FluidPortPanel(GameClientState state, GUICallback guiCallback, FluidSystemModule module, SegmentPiece segmentPiece) {
			super("Fluid_Port_Panel", state, WIDTH, HEIGHT, guiCallback, "Fluid Port", "");
			this.module = module;
			this.segmentPiece = segmentPiece;
			setCancelButton(false);
		}

		@Override
		public void onInit() {
			super.onInit();
			GUIContentPane contentPane = ((GUIDialogWindow) background).getMainContentPane();
			contentPane.setTextBoxHeightLast(100);
			StashInventory inventory = (StashInventory) module.getInventory(segmentPiece);
			inventory.setSlotLimit(2);

			inputSlot = new SingleInventorySlotIcon((GameClientState) getState(), inventory, FluidSystemModule.INPUT_SLOT_INDEX, "INPUT");
			inputSlot.onInit();
			contentPane.getContent(0).attach(inputSlot);
			inputSlot.getPos().x += 50;
			inputSlot.getPos().y += 50;
			inputSlot.setDropHandler(overlayElement -> overlayElement.getType() == ElementRegistry.FLUID_CANISTER.getId() || overlayElement.getType() == ElementRegistry.HELIOGEN_PLASMA.getId());

			outputSlot = new SingleInventorySlotIcon((GameClientState) getState(), inventory, FluidSystemModule.OUTPUT_SLOT_INDEX, "OUTPUT");
			outputSlot.onInit();
			contentPane.getContent(0).attach(outputSlot);
			outputSlot.getPos().x += 300;
			outputSlot.getPos().y += 50;
			outputSlot.setDropHandler(overlayElement -> overlayElement.getType() == ElementRegistry.FLUID_CANISTER.getId() || overlayElement.getType() == ElementRegistry.HELIOGEN_PLASMA.getId());
		}

		@Override
		public void draw() {
			super.draw();
			inputSlot.drawToolTip();
			outputSlot.drawToolTip();

			Draggable dragging = GameClient.getClientState().getController().getInputController().getDragging();
			if(dragging instanceof InventorySlotOverlayElement) {
				InventorySlotOverlayElement e = (InventorySlotOverlayElement) dragging;
				inputSlot.drawDragging(e);
				outputSlot.drawDragging(e);
			}
		}
	}
}