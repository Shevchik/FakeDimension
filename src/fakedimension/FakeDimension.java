package fakedimension;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.utility.MinecraftVersion;

public class FakeDimension extends JavaPlugin {

	private final Config config = new Config();
	private final Handler handler = new Handler(config);

	@Override
	public void onEnable() {
		if (!ProtocolLibrary.getProtocolManager().getMinecraftVersion().isAtLeast(MinecraftVersion.VILLAGE_UPDATE)) {
			System.err.println("Your server needs to be 1.14+");
			setEnabled(false);
			return;
		}
		config.load(this);
		handler.start(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("fakedimension.admin")) {
			sender.sendMessage(ChatColor.RED + "You have no power here");
			return true;
		}
		if ((args.length == 1) && args[0].equalsIgnoreCase("reload")) {
			config.load(this);
			sender.sendMessage(ChatColor.YELLOW + "Configuration reloaded");
			return true;
		}
		return false;
	}

}
