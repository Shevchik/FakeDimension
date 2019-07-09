package fakedimension;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;

public class Handler implements Listener {

	private final Config config;

	public Handler(Config config) {
		this.config = config;
	}

	protected final Set<UUID> switchedDimension = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public void start(FakeDimension plugin) {
		Bukkit.getPluginManager().registerEvents(this, plugin);

		// fake dimension on login
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.LOGIN)) {

			@Override
			public void onPacketSending(PacketEvent event) {
				config.getDimension(event.getPlayer().getWorld().getName())
				.ifPresent(dimension -> event.getPacket().getDimensions().write(0, dimension.getId()));
			}

		});

		// track dimension on dimension switch (we have to do this because dimension switch is sent before player actually enters world)
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.RESPAWN)) {
			@Override
			public void onPacketSending(PacketEvent event) {
				switchedDimension.add(event.getPlayer().getUniqueId());
			}
		});

		/* actually fake dimension on first position packet <br>
		/* we switch dimensions using middle dimension twice even we don't have fake dimension in new world
		/* because player might have respawned in the same dimension after switch from faked to non faked dimension*/
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.POSITION)) {

			PacketContainer createDimensionSwitch(Player player, WorldType wtype, Dimension targetDimension) {
				PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.RESPAWN);
				packet.getDimensions().write(0, targetDimension.getId());
				packet.getGameModes().write(0, NativeGameMode.fromBukkit(player.getGameMode()));
				packet.getWorldTypeModifier().write(0, wtype);
				return packet;
			}

			Dimension getMiddleDimension(Dimension dimension) {
				return dimension == Dimension.NORMAL ? Dimension.NETHER : Dimension.NORMAL;
			}

			void sendPacketNow(Player player, PacketContainer packet) {
				try {
					ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false);
				} catch (InvocationTargetException e) {
					player.kickPlayer("Fake dimension failure");
					e.printStackTrace();
				}
			}

			@Override
			public void onPacketSending(PacketEvent event) {
				Player player = event.getPlayer();

				if (!switchedDimension.remove(player.getUniqueId())) {
					return;
				}

				World world = player.getWorld();
				Optional<Dimension> optDimension = config.getDimension(world.getName());

				Dimension targetDimension = optDimension.orElseGet(() -> Dimension.getByBukkit(world.getEnvironment()));

				sendPacketNow(player, createDimensionSwitch(player, world.getWorldType(), getMiddleDimension(targetDimension)));
				sendPacketNow(player, createDimensionSwitch(player, world.getWorldType(), targetDimension));
			}

		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		switchedDimension.remove(event.getPlayer().getUniqueId());
	}

}
