package videogoose.resourcesrefueled.manager;

import api.listener.events.draw.RegisterWorldDrawersEvent;
import videogoose.resourcesrefueled.graphics.FluidNetworkDrawer;

public class GraphicsManager {

	public static FluidNetworkDrawer fluidNetworkDrawer;

	public static void registerDrawers(RegisterWorldDrawersEvent event) {
		event.getModDrawables().add(fluidNetworkDrawer = new FluidNetworkDrawer());
	}
}
