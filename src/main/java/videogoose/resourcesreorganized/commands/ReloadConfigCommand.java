package videogoose.resourcesreorganized.commands;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.schema.game.common.data.player.PlayerState;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.manager.ConfigManager;

public class ReloadConfigCommand implements CommandInterface {

	@Override
	public String getCommand() {
		return "rr_reload_config";
	}

	@Override
	public String[] getAliases() {
		return new String[]{"reload_rr_config"};
	}

	@Override
	public String getDescription() {
		return "Reloads Resources Reorganized config values from disk and reapplies synced config values.";
	}

	@Override
	public boolean isAdminOnly() {
		return true;
	}

	@Override
	public boolean onCommand(PlayerState player, String[] args) {
		try {
			ConfigManager.reload();
			if(player != null) {
				PlayerUtils.sendMessage(player, "Resources Reorganized config reloaded.");
			}
			ResourcesReorganized.getInstance().logInfo("Config reload command executed.");
		} catch(Exception exception) {
			if(player != null) {
				PlayerUtils.sendMessage(player, "Failed to reload Resources Reorganized config. Check server log.");
			}
			ResourcesReorganized.getInstance().logException("Failed to reload config via command", exception);
		}
		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState player, String[] args) {
		// No asynchronous server action required for this command.
	}

	@Override
	public StarMod getMod() {
		return ResourcesReorganized.getInstance();
	}
}

