package fakedimension;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.World.Environment;
import org.bukkit.configuration.file.YamlConfiguration;

public class FakeDimensionConfig {

	private final File storageFile;

	public FakeDimensionConfig(File storageFile) {
		this.storageFile = storageFile;
	}

	private final HashMap<String, Environment> fakeDimensions = new HashMap<>();

	public Optional<Environment> getDimension(String world) {
		return Optional.ofNullable(fakeDimensions.get(world));
	}

	private static final String fakeNormalDimensionKey = "fake-normal-worlds";
	private static final String fakeNetherDimensionKey = "fake-nether-worlds";
	private static final String fakeTheendDimensionKey = "fake-end-worlds";

	public void load() {
		YamlConfiguration configYml = YamlConfiguration.loadConfiguration(storageFile);
		fakeDimensions.clear();
		configYml.getStringList(fakeNormalDimensionKey).stream()
		.forEach(world -> fakeDimensions.put(world, Environment.NORMAL));
		configYml.getStringList(fakeNetherDimensionKey).stream()
		.forEach(world -> fakeDimensions.put(world, Environment.NETHER));
		configYml.getStringList(fakeTheendDimensionKey).stream()
		.forEach(world -> fakeDimensions.put(world, Environment.THE_END));
		save();
	}

	private void save() {
		YamlConfiguration configYml = new YamlConfiguration();
		configYml.set(fakeNormalDimensionKey, getWorlds(Environment.NORMAL));
		configYml.set(fakeNetherDimensionKey, getWorlds(Environment.NETHER));
		configYml.set(fakeTheendDimensionKey, getWorlds(Environment.THE_END));
		try {
			configYml.save(storageFile);
		} catch (IOException e) {
			System.err.println("Unable to save config");
			e.printStackTrace();
		}
	}

	private List<String> getWorlds(Environment dimension) {
		return
		fakeDimensions.entrySet().stream()
		.filter(entry -> entry.getValue() == dimension)
		.map(entry -> entry.getKey())
		.collect(Collectors.toList());
	}

}
