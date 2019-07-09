package fakedimension;

import org.bukkit.World.Environment;

public enum Dimension {

	NORMAL(0), NETHER(-1), END(1);

	private final int id;
	Dimension(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static Dimension getById(int id) {
		switch (id) {
			case 0: {
				return NORMAL;
			}
			case 1: {
				return END;
			}
			case -1: {
				return NETHER;
			}
			default: {
				throw new IllegalArgumentException("Unknown dimension " + id);
			}
		}
	}

	public static Dimension getByBukkit(Environment env) {
		switch (env) {
			case NORMAL: {
				return NORMAL;
			}
			case THE_END: {
				return END;
			}
			case NETHER: {
				return NETHER;
			}
			default: {
				throw new IllegalArgumentException("Unknown dimension " + env);
			}
		}
	} 

}
