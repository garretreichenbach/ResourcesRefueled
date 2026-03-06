package videogoose.resourcesreorganized.manager;

import api.listener.events.draw.RegisterWorldDrawersEvent;
import videogoose.resourcesreorganized.graphics.FluidNetworkDrawer;

public class GraphicsManager {

	public static FluidNetworkDrawer fluidNetworkDrawer;

	public static void registerDrawers(RegisterWorldDrawersEvent event) {
		event.getModDrawables().add(fluidNetworkDrawer = new FluidNetworkDrawer());
	}
}
