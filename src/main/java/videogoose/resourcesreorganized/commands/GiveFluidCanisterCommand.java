package videogoose.resourcesreorganized.commands;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.data.FluidMeta;
import videogoose.resourcesreorganized.manager.ConfigManager;

public class GiveFluidCanisterCommand implements CommandInterface {

	@Override
	public String getCommand() {
		return "give_canister";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"give_canister"};
	}

	@Override
	public String getDescription() {
		return "Gives the player an fluid canister of the specified name and amount.\n" + "Usage: /give_canister <fluid_id> <amount>";
	}

	@Override
	public boolean isAdminOnly() {
		return true;
	}

	@Override
	public boolean onCommand(PlayerState playerState, String[] strings) {
		try {
			short fluidId = Short.parseShort(strings[0]);
			int amount = Integer.parseInt(strings[1]);
			double unitsPerCanister = ConfigManager.getCapacityPerCanister();
			Inventory inventory = playerState.getInventory();
			int slot = inventory.getFreeSlot();
			FluidMeta.writeFilled(inventory, slot, fluidId, amount, unitsPerCanister);
		} catch(Exception exception) {
			exception.printStackTrace();
			PlayerUtils.sendMessage(playerState, "Invalid usage. Correct usage: /give_canister <fluid_id> <amount>");
		}
		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState playerState, String[] strings) {

	}

	@Override
	public StarMod getMod() {
		return ResourcesReorganized.getInstance();
	}
}
