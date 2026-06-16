package videogoose.resourcesreorganized.commands;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.player.PlayerState;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.logistics.item.topology.ItemTransportNetwork;
import videogoose.resourcesreorganized.systems.ItemTransportSystemModule;

import java.util.List;

public class ItemTopologyCommand implements CommandInterface {

	@Override
	public String getCommand() {
		return "rr_item_topology";
	}

	@Override
	public String[] getAliases() {
		return new String[0];
	}

	@Override
	public String getDescription() {
		return "Lists item transport networks (conveyor / tube) on every loaded entity. Admin only.";
	}

	@Override
	public boolean isAdminOnly() {
		return true;
	}

	@Override
	public boolean onCommand(PlayerState player, String[] args) {
		List<ItemTransportSystemModule> modules = ItemTransportSystemModule.snapshotInstances();
		if(modules.isEmpty()) {
			send(player, "No entities with item transport modules are currently loaded.");
			return true;
		}
		StringBuilder out = new StringBuilder();
		out.append("Item transport topology — ").append(modules.size()).append(" entity/entities\n");
		for(ItemTransportSystemModule module : modules) {
			SegmentController controller = module.getSegmentController();
			String uid = controller != null ? controller.getUniqueIdentifier() : "<unknown>";
			List<ItemTransportNetwork> networks = module.getNetworks();
			out.append(" - ").append(uid).append(": ").append(networks.size()).append(" network(s); ").append(module.getConveyorSegments().size()).append(" conveyor block(s), ").append(module.getTubeSegments().size()).append(" tube/pump block(s)\n");
			for(int i = 0; i < networks.size(); i++) {
				ItemTransportNetwork net = networks.get(i);
				out.append("    [").append(i).append("] family=").append(net.family).append(" members=").append(net.memberIndices.size()).append(" ports=").append(net.portIndices.size()).append("\n");
			}
		}
		send(player, out.toString());
		ResourcesReorganized.getInstance().logInfo(out.toString());
		return true;
	}

	private static void send(PlayerState player, String message) {
		if(player != null) {
			PlayerUtils.sendMessage(player, message);
		}
	}

	@Override
	public void serverAction(@Nullable PlayerState player, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return ResourcesReorganized.getInstance();
	}
}
