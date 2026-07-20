package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * The two fighting teams, NEUTRAL (= no team / unowned point), and ADMIN
 * (a spectator side: joinable like A/B but excluded from capture occupancy,
 * ticket/respawn cost and kill/death/assist scoring).
 */
public enum Team implements StringRepresentable {
    A("a", ChatFormatting.BLUE),
    B("b", ChatFormatting.RED),
    ADMIN("admin", ChatFormatting.GOLD),
    NEUTRAL("neutral", ChatFormatting.WHITE);

    private final String key;
    private final ChatFormatting color;

    Team(String key, ChatFormatting color) {
        this.key = key;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public ChatFormatting color() {
        return color;
    }

    /**
     * Fixed ARGB color for HUD/GUI elements (ticket bar, point icons, capture
     * overlay): Team A is always this blue and Team B always this red,
     * everywhere, regardless of which team the viewer is on.
     */
    public int hudColor() {
        return switch (this) {
            case A -> 0xFF3B6FE0;
            case B -> 0xFFE03B3B;
            case ADMIN -> 0xFFFFC83B;
            case NEUTRAL -> 0xFF808080;
        };
    }

    /** Colored display name, e.g. "Team A" / "チームA". */
    public MutableComponent display() {
        return Component.translatable("conquest.team." + key).withStyle(color);
    }

    public Team opponent() {
        return this == A ? B : this == B ? A : NEUTRAL;
    }

    /** True for the two fighting sides; false for NEUTRAL and ADMIN. */
    public boolean isCombatant() {
        return this == A || this == B;
    }

    /**
     * The team a flag currently "reads as": its owner once captured, the team
     * actively raising it from neutral, else neutral. Shared by the flag
     * block's coloring, the status GUI and the HUD so all three agree.
     */
    public static Team resolveActive(Team owner, Team capturingTeam, double flagLevel) {
        if (owner != NEUTRAL) {
            return owner;
        }
        if (capturingTeam != NEUTRAL && flagLevel > 0) {
            return capturingTeam;
        }
        return NEUTRAL;
    }

    /** Parses "a"/"b" (case-insensitive); null for anything else. */
    @Nullable
    public static Team byKey(String key) {
        for (Team team : values()) {
            if (team != NEUTRAL && team.key.equalsIgnoreCase(key)) {
                return team;
            }
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
