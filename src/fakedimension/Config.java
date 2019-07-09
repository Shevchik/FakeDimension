package fakedimension;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.YamlConfiguration;

public class Config {

	private final HashMap<String, Dimension> fakeDimensions = new HashMap<>();

	public Optional<Dimension> getDimension(String world) {
		return Optional.ofNullable(fakeDimensions.get(world));
	}

	private static final String config = "config.yml";
	private static final String fakeNormalDimensionKey = "fake-normal-worlds";
	private static final String fakeNetherDimensionKey = "fake-nether-worlds";
	private static final String fakeTheendDimensionKey = "fake-end-worlds";

	public void load(FakeDimension plugin) {
		File file = new File(plugin.getDataFolder(), config);
		YamlConfiguration configYml = YamlConfiguration.loadConfiguration(file);
		fakeDimensions.clear();
		configYml.getStringList(fakeNormalDimensionKey).stream()
		.forEach(world -> fakeDimensions.put(world, Dimension.NORMAL));
		configYml.getStringList(fakeNetherDimensionKey).stream()
		.forEach(world -> fakeDimensions.put(world, Dimension.NETHER));
		configYml.getStringList(fakeTheendDimensionKey).stream()
		.forEach(world -> fakeDimensions.put(world, Dimension.END));
		save(plugin);
	}

	private void save(FakeDimension plugin) {
		YamlConfiguration configYml = new YamlConfiguration();
		configYml.set(fakeNormalDimensionKey, getWorlds(Dimension.NORMAL));
		configYml.set(fakeNetherDimensionKey, getWorlds(Dimension.NETHER));
		configYml.set(fakeTheendDimensionKey, getWorlds(Dimension.END));
		try {
			configYml.save(new File(plugin.getDataFolder(), config));
		} catch (IOException e) {
			System.err.println("Unable to save config");
			e.printStackTrace();
		}
	}

	private List<String> getWorlds(Dimension dimension) {
		return
		fakeDimensions.entrySet().stream()
		.filter(entry -> entry.getValue() == dimension)
		.map(entry -> entry.getKey())
		.collect(Collectors.toList());
	}

}
