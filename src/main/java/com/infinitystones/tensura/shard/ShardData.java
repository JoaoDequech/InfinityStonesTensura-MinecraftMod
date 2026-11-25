package com.infinitystones.tensura.shard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data component of a mini gem. Display copy only: the authoritative state (revoked, expiry, binding) lives in the
 * server's {@link ShardRegistry}; the stack just carries what the tooltip and the item model need.
 * expiry = absolute overworld game time in ticks (survives restarts), ignored when infinite.
 */
public record ShardData(UUID id, UUID creator, String creatorName, int stone, List<String> powers, long expiry,
                        long total, boolean infinite, @Nullable UUID boundTo, String boundName) {
    public static final Codec<ShardData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ShardData::id),
            UUIDUtil.CODEC.fieldOf("creator").forGetter(ShardData::creator),
            Codec.STRING.fieldOf("creator_name").forGetter(ShardData::creatorName),
            Codec.INT.fieldOf("stone").forGetter(ShardData::stone),
            Codec.STRING.listOf().fieldOf("powers").forGetter(ShardData::powers),
            Codec.LONG.fieldOf("expiry").forGetter(ShardData::expiry),
            Codec.LONG.optionalFieldOf("total", 0L).forGetter(ShardData::total),
            Codec.BOOL.fieldOf("infinite").forGetter(ShardData::infinite),
            UUIDUtil.CODEC.optionalFieldOf("bound").forGetter(d -> java.util.Optional.ofNullable(d.boundTo)),
            Codec.STRING.optionalFieldOf("bound_name", "").forGetter(ShardData::boundName)
    ).apply(i, (id, c, cn, st, pw, ex, tot, inf, b, bn) -> new ShardData(id, c, cn, st, List.copyOf(pw), ex, tot, inf, b.orElse(null), bn)));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShardData> STREAM_CODEC = StreamCodec.of((buf, d) -> {
        buf.writeUUID(d.id); buf.writeUUID(d.creator); buf.writeUtf(d.creatorName, 64); buf.writeVarInt(d.stone);
        buf.writeVarInt(d.powers.size());
        for (String p : d.powers) buf.writeUtf(p, 64);
        buf.writeLong(d.expiry); buf.writeLong(d.total); buf.writeBoolean(d.infinite);
        buf.writeBoolean(d.boundTo != null);
        if (d.boundTo != null) buf.writeUUID(d.boundTo);
        buf.writeUtf(d.boundName, 64);
    }, buf -> {
        UUID id = buf.readUUID(), creator = buf.readUUID();
        String cn = buf.readUtf(64);
        int stone = buf.readVarInt();
        int n = buf.readVarInt();
        List<String> powers = new ArrayList<>(n);
        for (int k = 0; k < n; k++) powers.add(buf.readUtf(64));
        long expiry = buf.readLong(), total = buf.readLong();
        boolean inf = buf.readBoolean();
        UUID bound = buf.readBoolean() ? buf.readUUID() : null;
        return new ShardData(id, creator, cn, stone, powers, expiry, total, inf, bound, buf.readUtf(64));
    });

    public ShardData withBound(@Nullable UUID uuid, String name) {
        return new ShardData(id, creator, creatorName, stone, powers, expiry, total, infinite, uuid, name);
    }

    /** 4-character short code of the id, shown to the players ("A3F9"). */
    public String code() { return codeOf(id); }

    public static String codeOf(UUID id) {
        String h = Long.toHexString(id.getMostSignificantBits() ^ id.getLeastSignificantBits()).toUpperCase();
        while (h.length() < 4) h = "0" + h;
        return h.substring(h.length() - 4);
    }
}
