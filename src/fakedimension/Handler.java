package fakedimension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
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
import com.comphenix.protocol.wrappers.EnumWrappers.Difficulty;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class Handler implements Listener {

	private final Config config;
	public Handler(Config config) {
		this.config = config;
	}

	protected final Map<UUID, FakeDimensionEntry> fakeDimensions = new ConcurrentHashMap<>();
	protected final Set<UUID> dimensionSwitched = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public void start(FakeDimension plugin) {
		Bukkit.getPluginManager().registerEvents(this, plugin);

		//fake dimension on login
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.LOGIN)) {

			@Override
			public void onPacketSending(PacketEvent event) {
				Player player = event.getPlayer();
				World world = player.getWorld();

				config.getDimension(world.getName())
				.ifPresent(fakeDim -> {
					fakeDimensions.put(player.getUniqueId(), new FakeDimensionEntry(world.getEnvironment(), fakeDim));
					event.getPacket().getIntegers().write(1, fakeDim.getId());
				});
			}

		});

		//track player dimension switch, because we can't get player world at that time
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.RESPAWN)) {
			@Override
			public void onPacketSending(PacketEvent event) {
				Player player = event.getPlayer();
				removePlayer(player);
				dimensionSwitched.add(player.getUniqueId());
			}
		});

		//fake dimension on first position packet after dimension switch
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.POSITION)) {

			PacketContainer createDimensionSwitch(Player player, Difficulty difficulty, WorldType wtype, Dimension targetDimension) {
				PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.RESPAWN);
				packet.getIntegers().write(0, targetDimension.getId());
				packet.getDifficulties().write(0, difficulty);
				packet.getGameModes().write(0, NativeGameMode.fromBukkit(player.getGameMode()));
				packet.getWorldTypeModifier().write(0, wtype);
				return packet;
			}

			Difficulty fromBukkit(org.bukkit.Difficulty difficulty) {
				switch (difficulty) {
					case PEACEFUL: {
						return Difficulty.PEACEFUL;
					}
					case EASY: {
						return Difficulty.EASY;
					}
					case NORMAL: {
						return Difficulty.NORMAL;
					}
					case HARD: {
						return Difficulty.HARD;
					}
					default: {
						return Difficulty.PEACEFUL;
					}
				}
			}

			@Override
			public void onPacketSending(PacketEvent event) {
				Player player = event.getPlayer();

				if (!dimensionSwitched.remove(player.getUniqueId())) {
					return;
				}

				World world = player.getWorld();
				config.getDimension(world.getName())
				.ifPresent(fakeDim -> {
					fakeDimensions.put(player.getUniqueId(), new FakeDimensionEntry(world.getEnvironment(), fakeDim));
					Dimension middleDim = fakeDim == Dimension.NORMAL ? Dimension.NETHER : Dimension.NORMAL;
					try {
						Difficulty difficulty = fromBukkit(world.getDifficulty());
						WorldType wtype = world.getWorldType();
						ProtocolLibrary.getProtocolManager().sendServerPacket(player, createDimensionSwitch(player, difficulty, wtype, middleDim), false);
						ProtocolLibrary.getProtocolManager().sendServerPacket(player, createDimensionSwitch(player, difficulty, wtype, fakeDim), false);
					} catch (Exception e) {
						System.err.println("Unable to send dimension switch packets");
						e.printStackTrace();
					}
				});
			}

		});

		//rewrite chunks, because skylight is not sent for worlds that doesn't have skylight
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Server.MAP_CHUNK)) {

			boolean hasBiomeData;
			final ChunkSection[] sections = new ChunkSection[16];
			final byte[] biomeData = new byte[256];

			class ChunkSection {

				final byte bitsPerBlock;
				final int[] palette;
				final byte[] blockdata;
				final byte[] blocklight = new byte[2048];
				final byte[] skylight = new byte[2048];

				public ChunkSection(ByteBuf datastream, boolean hasSkyLight) {
					bitsPerBlock = datastream.readByte();
					palette = new int[VarNumberSerializer.readVarInt(datastream)];
					for (int i = 0; i < palette.length; i++) {
						palette[i] = VarNumberSerializer.readVarInt(datastream);
					}
					blockdata = new byte[VarNumberSerializer.readVarInt(datastream) * Long.BYTES];
					datastream.readBytes(blockdata);
					datastream.readBytes(blocklight);
					if (hasSkyLight) {
						datastream.readBytes(skylight);
					}
				}

				protected void writeTo(ByteBuf datastream, boolean hasSkyLight) {
					datastream.writeByte(bitsPerBlock);
					VarNumberSerializer.writeVarInt(datastream, palette.length);
					for (int palettei : palette) {
						VarNumberSerializer.writeVarInt(datastream, palettei);
					}
					VarNumberSerializer.writeVarInt(datastream, blockdata.length / Long.BYTES);
					datastream.writeBytes(blockdata);
					datastream.writeBytes(blocklight);
					if (hasSkyLight) {
						Arrays.fill(skylight, (byte) 255);
						datastream.writeBytes(skylight);
					}
				}

			}

			@Override
			public void onPacketSending(PacketEvent event) {
				FakeDimensionEntry entry = fakeDimensions.get(event.getPlayer().getUniqueId());
				if (entry == null) {
					return;
				}

				PacketContainer packet = event.getPacket();
				hasBiomeData = packet.getBooleans().read(0);
				int columnsCount = Integer.bitCount(packet.getIntegers().read(2));

				//decode chunk data
				ByteBuf chunkdata = Unpooled.wrappedBuffer(packet.getByteArrays().read(0));
				for (int i = 0; i < columnsCount; i++) {
					sections[i] = new ChunkSection(chunkdata, entry.realDimensionSkyLight);
				}
				if (hasBiomeData) {
					chunkdata.readBytes(biomeData);
				}

				//encode chunk data
				chunkdata = Unpooled.buffer(40000);
				for (int i = 0; i < columnsCount; i++) {
					sections[i].writeTo(chunkdata, entry.fakeDimensionSkyLight);
				}
				if (hasBiomeData) {
					chunkdata.writeBytes(biomeData);
				}

				//write it to packet
				byte[] chunkdatarr = new byte[chunkdata.readableBytes()];
				chunkdata.readBytes(chunkdatarr);
				packet.getByteArrays().write(0, chunkdatarr);
			}

		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLeave(PlayerQuitEvent event) {
		removePlayer(event.getPlayer());
	}

	private void removePlayer(Player player) {
		fakeDimensions.remove(player.getUniqueId());
		dimensionSwitched.remove(player.getUniqueId());
	}

	private static final class FakeDimensionEntry {
		private final boolean realDimensionSkyLight;
		private final boolean fakeDimensionSkyLight;
		public FakeDimensionEntry(Environment realDimension, Dimension fakeDimension) {
			this.realDimensionSkyLight = realDimension == Environment.NORMAL;
			this.fakeDimensionSkyLight = fakeDimension == Dimension.NORMAL;
		}
	}


}
