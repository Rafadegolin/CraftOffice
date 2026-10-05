package dev.rafadegolin.craftoffice.zone;

/**
 * Sala marcada por dois cantos, em blocos, com os dois cantos inclusos.
 * Trancar não é salvo: ao reiniciar o servidor, toda sala abre.
 */
public record Zone(String name, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public static Zone of(String name, String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
		return new Zone(name, dimension,
				Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
				Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}

	/** Contém o ponto, em coordenadas de mundo. O bloco máximo conta inteiro. */
	public boolean contains(String dimension, double x, double y, double z) {
		return this.dimension.equals(dimension)
				&& x >= minX && x < maxX + 1
				&& y >= minY && y < maxY + 1
				&& z >= minZ && z < maxZ + 1;
	}

	public boolean overlaps(Zone other) {
		return dimension.equals(other.dimension)
				&& minX <= other.maxX && maxX >= other.minX
				&& minY <= other.maxY && maxY >= other.minY
				&& minZ <= other.maxZ && maxZ >= other.minZ;
	}

	public double centerX() {
		return (minX + maxX + 1) / 2.0;
	}

	public double centerZ() {
		return (minZ + maxZ + 1) / 2.0;
	}
}
