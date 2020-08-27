package fakedimension;

import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.MinecraftKey;

public class FakeDimensionHandler implements Listener {

	private final FakeDimension plugin;
	private final FakeDimensionConfig config;

	public FakeDimensionHandler(FakeDimension plugin, FakeDimensionConfig config) {
		this.plugin = plugin;
		this.config = config;
	}

	protected final MinecraftKey DIM_EFFECT_NORMAL = new MinecraftKey("overworld");
	protected final MinecraftKey DIM_EFFECT_NETHER = new MinecraftKey("the_nether");
	protected final MinecraftKey DIM_EFFECT_END = new MinecraftKey("the_end");

	protected MinecraftKey getDimensionEffectKey(Environment dimension) {
		switch (dimension) {
			case NORMAL: {
				return DIM_EFFECT_NORMAL;
			}
			case NETHER: {
				return DIM_EFFECT_NETHER;
			}
			case THE_END: {
				return DIM_EFFECT_END;
			}
			default: {
				throw new IllegalArgumentException(MessageFormat.format("Unknown dimension {0}", dimension));
			}
		}
	}

	protected final Map<UUID, String> playerWorld = new ConcurrentHashMap<>();

	public void start() {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);

		Class<?> dimensionManagerClass = MinecraftReflection.getMinecraftClass("DimensionManager");
		StructureModifier<Object> dimensionManagerStructureModifier = new StructureModifier<>(dimensionManagerClass, Object.class, false, true);
		Class<?> minecraftKeyClass = MinecraftReflection.getMinecraftKeyClass();

		ProtocolLibrary.getProtocolManager().addPacketListener(
			new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.LOGIN, PacketType.Play.Server.RESPAWN)) {
				@Override
				public void onPacketSending(PacketEvent event) {
					config.getDimension(event.getPlayer().getWorld().getName())
					.ifPresent(dimension ->
						dimensionManagerStructureModifier
						.withTarget(event.getPacket().getSpecificModifier(dimensionManagerClass).read(0))
						.withType(minecraftKeyClass, MinecraftKey.getConverter())
						.write(1, getDimensionEffectKey(dimension))
					);
				}
			}
		);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	protected void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		playerWorld.put(player.getUniqueId(), player.getWorld().getName());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	protected void onWorldChange(PlayerTeleportEvent event) {
		World world = event.getTo().getWorld();
		if (!world.equals(event.getFrom().getWorld())) {
			playerWorld.put(event.getPlayer().getUniqueId(), world.getName());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	protected void onRespawn(PlayerRespawnEvent event) {
		playerWorld.put(event.getPlayer().getUniqueId(), event.getRespawnLocation().getWorld().getName());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	protected void onQuit(PlayerQuitEvent event) {
		playerWorld.remove(event.getPlayer().getUniqueId());
	}

}
