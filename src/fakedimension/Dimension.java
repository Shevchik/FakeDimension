package fakedimension;

public enum Dimension {

	NORMAL(0), NETHER(-1), END(1);

	private final int id;
	Dimension(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

}
