package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A named, OP-registered scorestreak-style reward: once a player's current-round score (kills/
 * assists/revives, weighted the same as the scoreboard, minus whatever they've already spent
 * this round) reaches {@code scoreCost}, they can redeem it via {@code /conquest callin use} for
 * {@code count} of {@code itemId}, spending the cost from their available score.
 */
public final class CallIn {
    private final String name;
    private final int scoreCost;
    private final ResourceLocation itemId;
    private final int count;

    public CallIn(String name, int scoreCost, ResourceLocation itemId, int count) {
        this.name = name;
        this.scoreCost = scoreCost;
        this.itemId = itemId;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public int getScoreCost() {
        return scoreCost;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    // --- persistence ---

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putInt("ScoreCost", scoreCost);
        tag.putString("ItemId", itemId.toString());
        tag.putInt("Count", count);
        return tag;
    }

    static CallIn load(CompoundTag tag) {
        return new CallIn(tag.getString("Name"), tag.getInt("ScoreCost"),
                new ResourceLocation(tag.getString("ItemId")), tag.getInt("Count"));
    }
}
