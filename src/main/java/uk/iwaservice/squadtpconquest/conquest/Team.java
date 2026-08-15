package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * The two fighting teams, NEUTRAL (= no team / unowned point), ADMIN (an OP-only spectator side:
 * joinable like A/B but excluded from capture occupancy, ticket/respawn cost and kill/death/assist
 * scoring), RANGE (the training range: same exclusions as ADMIN, open to anyone, tied to its own
 * area instead of the match — see {@link ConquestManager#setRange}), SPECTATOR (same match
 * exclusions and HUD visibility as ADMIN, but open to anyone and forced into vanilla spectator
 * gamemode instead of relying on OP trust not to interfere), and WAITING (a neutral holding team,
 * open to anyone: stays in normal survival — unlike SPECTATOR — but can neither deal nor take PvP
 * damage, and has its inventory cleared the moment it joins).
 */
public enum Team implements StringRepresentable {
    A("a", ChatFormatting.BLUE),
    B("b", ChatFormatting.RED),
    ADMIN("admin", ChatFormatting.GOLD),
    RANGE("range", ChatFormatting.GREEN),
    SPECTATOR("spectator", ChatFormatting.AQUA),
    WAITING("waiting", ChatFormatting.GRAY),
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
            case RANGE -> 0xFF3BE05E;
            case SPECTATOR -> 0xFF3BC8E0;
            case WAITING -> 0xFFA0A0A0;
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

    /** True for the two fighting sides; false for NEUTRAL, ADMIN, RANGE, SPECTATOR and WAITING. */
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
