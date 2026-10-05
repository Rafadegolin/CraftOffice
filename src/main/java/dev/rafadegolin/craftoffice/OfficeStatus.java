package dev.rafadegolin.craftoffice;

/**
 * Status do player, como no Gather. Disponível conversa normal; ocupado só
 * mostra o aviso; foco encolhe o alcance para 2 blocos; não perturbe não
 * conecta com ninguém.
 */
public enum OfficeStatus {
	AVAILABLE(0xFF4CAF50),
	BUSY(0xFFFF9800),
	FOCUS(0xFF9C27B0),
	DO_NOT_DISTURB(0xFFF44336);

	/** Alcance de quem está em foco, em blocos. */
	public static final double FOCUS_RADIUS = 2;

	private final int color;

	OfficeStatus(int color) {
		this.color = color;
	}

	public int color() {
		return color;
	}

	public String translationKey() {
		return "craftoffice.status_name." + name().toLowerCase();
	}

	public OfficeStatus next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static OfficeStatus byId(int id) {
		return id >= 0 && id < values().length ? values()[id] : AVAILABLE;
	}
}
