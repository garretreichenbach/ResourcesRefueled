package videogoose.resourcesreorganized.commands;

import api.common.GameServer;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONObject;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.server.data.PlayerNotFountException;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.data.FluidMeta;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.utils.InventoryUtils;

public class GiveFluidContainerCommand implements CommandInterface {
	@Override
	public String getCommand() {
		return "give_fluid_container";
	}

	@Override
	public String[] getAliases() {
		return new String[0];
	}

	@Override
	public String getDescription() {
		return "Gives the player a fluid item.\n" + "- /give_fluid_container <\"Fluid Canister\" | \"Fluid Tank\" | \"Fluid Port\"> : Gives the player an empty fluid container.\n" + "- /give_fluid_container<\"Fluid Canister\" | \"Fluid Tank\" | \"Fluid Port\"> <fluid_name> <amount> [filled] : Gives the a filled fluid container with the specified fluid and amount. If no filled amount is specified, it defaults to 100% filled volume for that container.\n" + "- /give_fluid_container <player_name> <\"Fluid Canister\" | \"Fluid Tank\" | \"Fluid Port\"> <fluid_name> <amount> [filled] : Gives the specified player a filled fluid container with the specified fluid and amount. If no filled amount is specified, it defaults to 100% filled volume for that container.";
	}

	@Override
	public boolean isAdminOnly() {
		return true;
	}

	@Override
	public boolean onCommand(PlayerState player, String[] args) {
		boolean success = true;
		if(args.length == 0) {
			PlayerUtils.sendMessage(player, "Usage: /give_fluid_container <player_name> <\"Fluid Canister\" | \"Fluid Tank\" | \"Fluid Port\"> <fluid_name> <amount> [filled]");
		} else if(args.length == 1) {
			success = giveFluidContainer(player, args[0], null, null, null);
		} else if(args.length == 3 || args.length == 4) {
			success = giveFluidContainer(player, args[0], args[1], args[2], args.length == 4 ? args[3] : null);
		} else if(args.length == 5 || args.length == 6) {
			PlayerState targetPlayer = null;
			try {
				targetPlayer = GameServer.getServerState().getPlayerFromName(args[0]);
			} catch(PlayerNotFountException e) {
				PlayerUtils.sendMessage(player, "Player not found: " + args[0]);
			}
			success = giveFluidContainer(targetPlayer, args[1], args[2], args[3], args.length == 6 ? args[5] : null);
		} else {
			PlayerUtils.sendMessage(player, "Invalid usage. Use /give_fluid_container <player_name> <\"Fluid Canister\" | \"Fluid Tank\" | \"Fluid Port\"> <fluid_name> <amount> [filled]");
		}
		if(success) {
			PlayerUtils.sendMessage(player, "Gave fluid container successfully.");
		} else {
			PlayerUtils.sendMessage(player, "Error: Invalid container type, fluid name, amount, or filled percent.\nUsage: /give_fluid_container <player_name> <\"Fluid Canister\" | \"Fluid Tank\" | \"Fluid Port\"> <fluid_name> <amount> [filled]");
		}
		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState player, String[] args) {

	}

	@Override
	public StarMod getMod() {
		return ResourcesReorganized.getInstance();
	}

	private boolean giveFluidContainer(PlayerState targetPlayer, String containerType, @Nullable String fluidName, @Nullable String amount, @Nullable String filled) {
		short containerId = ElementRegistry.getIdByName(containerType);
		JSONObject customData = new JSONObject();
		int amountValue = 1;
		if(!ElementRegistry.isContainer(containerId)) {
			return false;
		}
		if(fluidName == null) {
			// Give empty container
			customData.put("fluid_id", 0);
			customData.put("fluid_amount", 0);
			customData.put("fluid_capacity", ElementRegistry.getCapacityForContainer(containerId));
			InventoryUtils.putItems(targetPlayer.getInventory(), containerId, amountValue, customData);
		} else {
			// Give filled container
			short fluidId = FluidMeta.getFluidId(fluidName);
			if(fluidId == -1) {
				return false;
			}

			if(amount != null) {
				try {
					amountValue = Integer.parseInt(amount);
				} catch(NumberFormatException e) {
					return false;
				}
			}

			double capacity = ElementRegistry.getCapacityForContainer(containerId);
			double filledAmount;
			try {
				if(filled != null) {
					if(filled.endsWith("%")) {
						double percent = Double.parseDouble(filled.substring(0, filled.length() - 1));
						filledAmount = capacity * (percent / 100.0);
					} else {
						filledAmount = Double.parseDouble(filled);
					}
				} else {
					filledAmount = capacity;
				}
			} catch(NumberFormatException e) {
				return false;
			}
			JSONObject meta = new JSONObject();
			meta.put("fluid_id", fluidId);
			meta.put("fluid_amount", filledAmount);
			meta.put("fluid_capacity", ElementRegistry.getCapacityForContainer(containerId));
			InventoryUtils.putItems(targetPlayer.getInventory(), containerId, amountValue, meta);
		}
		return true;
	}
}