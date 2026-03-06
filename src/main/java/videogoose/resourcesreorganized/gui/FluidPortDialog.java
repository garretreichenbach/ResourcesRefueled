package videogoose.resourcesreorganized.gui;

import api.common.GameClient;
import org.schema.game.client.view.mainmenu.DialogInput;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;

public class FluidPortDialog extends DialogInput {

	public FluidPortDialog() {
		super(GameClient.getClientState());
	}

	@Override
	public void onDeactivate() {

	}

	@Override
	public void handleMouseEvent(MouseEvent mouseEvent) {

	}

	@Override
	public GUIElement getInputPanel() {
		return null;
	}
}
