package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * Which ruleset a round uses. CONQUEST is the original capture-point mode;
 * TDM (Team Deathmatch) needs no capture points and is won by kills instead.
 */
public enum GameMode implements StringRepresentable {
    CONQUEST("conquest"),
    TDM("tdm");

    private final String key;

    GameMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public MutableComponent display() {
        return Component.translatable("conquest.mode." + key);
    }

    @Nullable
    public static GameMode byKey(String key) {
        for (GameMode mode : values()) {
            if (mode.key.equalsIgnoreCase(key)) {
                return mode;
            }
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
