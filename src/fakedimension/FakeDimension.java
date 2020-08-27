package fakedimension;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class FakeDimension extends JavaPlugin {

	private final FakeDimensionConfig config = new FakeDimensionConfig(new File(getDataFolder(), "config.yml"));
	private final FakeDimensionHandler handler = new FakeDimensionHandler(this, config);

	@Override
	public void onEnable() {
		config.load();
		handler.start();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("fakedimension.admin")) {
			sender.sendMessage(ChatColor.RED + "You have no power here");
			return true;
		}
		if ((args.length == 1) && args[0].equalsIgnoreCase("reload")) {
			config.load();
			sender.sendMessage(ChatColor.YELLOW + "Configuration reloaded");
			return true;
		}
		return false;
	}

}
