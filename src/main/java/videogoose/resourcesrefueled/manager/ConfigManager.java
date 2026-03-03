package videogoose.resourcesrefueled.manager;

import api.mod.ModSkeleton;
import api.mod.StarLoader;
import api.mod.config.FileConfiguration;
import videogoose.resourcesrefueled.ResourcesRefueled;

import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {

	private static FileConfiguration mainConfig;
	private static final String[] defaultMainConfig = {
			"debug_mode: false # If true, enables debug logging and features.",
	};

	public static void initialize(ResourcesRefueled instance) {
		mainConfig = instance.getConfig("config");
		saveDefaultConfig(defaultMainConfig);
		populateServerMods();
	}

	/**
	 * Adds any server-installed mod IDs that are not already in the approved_client_mods config
	 * list, then saves the config. This ensures mods running on the server are always permitted
	 * for connecting clients without requiring manual config edits.
	 */
	public static void populateServerMods() {
		try {
			List<String> approved = mainConfig.getList("approved_client_mods");
			// Work on a mutable copy; getList may return an unmodifiable view
			List<String> updated = (approved != null) ? new ArrayList<>(approved) : new ArrayList<>();
			boolean changed = false;
			for(ModSkeleton mod : StarLoader.starMods) {
				String id = String.valueOf(mod.getSmdResourceId());
				if(!updated.contains(id)) {
					updated.add(id);
					ResourcesRefueled.getInstance().logInfo("Auto-approved server mod ID " + id + " (" + mod.getName() + ") in approved_client_mods.");
					changed = true;
				}
			}
			if(changed) {
				mainConfig.set("approved_client_mods", updated);
				mainConfig.saveConfig();
			}
		} catch(Exception e) {
			ResourcesRefueled.getInstance().logException("Failed to populate server mods into approved_client_mods", e);
		}
	}

	private static void saveDefaultConfig(String[] config) {
		for(String line : config) {
			String key = line.substring(0, line.indexOf(':')).trim();
			String value = line.substring(line.indexOf(':') + 1, line.indexOf('#')).trim();
			String comment = line.substring(line.indexOf('#') + 1).trim();
			if(!mainConfig.getKeys().contains(key)) {
				mainConfig.set(key, value);
				mainConfig.setComment(key, comment);
			}
		}
		mainConfig.saveConfig();
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}
}


