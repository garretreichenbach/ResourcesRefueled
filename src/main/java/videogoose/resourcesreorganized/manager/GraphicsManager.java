package videogoose.resourcesreorganized.manager;

import api.listener.events.draw.RegisterWorldDrawersEvent;
import videogoose.resourcesreorganized.graphics.ConveyorItemDrawer;
import videogoose.resourcesreorganized.graphics.ConveyorNetworkDrawer;
import videogoose.resourcesreorganized.graphics.FluidNetworkDrawer;

public class GraphicsManager {

	public static FluidNetworkDrawer fluidNetworkDrawer;
	public static ConveyorNetworkDrawer conveyorNetworkDrawer;
	public static ConveyorItemDrawer conveyorItemDrawer;

	public static void registerDrawers(RegisterWorldDrawersEvent event) {
		event.getModDrawables().add(fluidNetworkDrawer = new FluidNetworkDrawer());
		event.getModDrawables().add(conveyorNetworkDrawer = new ConveyorNetworkDrawer());
		event.getModDrawables().add(conveyorItemDrawer = new ConveyorItemDrawer());
	}
}
