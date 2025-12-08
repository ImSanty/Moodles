package net.imsanty.moodles.moodle.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imsanty.moodles.Moodles;
import net.imsanty.moodles.moodle.MoodleSeverity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Packet mirroring the server-side moodle snapshot to the client.
 */
public record SyncMoodlesPayload(Map<Identifier, MoodleSeverity> moodles) implements CustomPayload {
  public static final Id<SyncMoodlesPayload> ID = new Id<>(Identifier.of(Moodles.MOD_ID, "sync_moodles"));
  public static final PacketCodec<PacketByteBuf, SyncMoodlesPayload> CODEC = CustomPayload.codecOf(
      (payload, buf) -> payload.write(buf), SyncMoodlesPayload::new);

  public SyncMoodlesPayload {
    moodles = Collections.unmodifiableMap(new LinkedHashMap<>(moodles));
  }

  private SyncMoodlesPayload(PacketByteBuf buf) {
    this(read(buf));
  }

  public void write(PacketByteBuf buf) {
    buf.writeVarInt(moodles.size());
    moodles.forEach((id, severity) -> {
      buf.writeIdentifier(id);
      buf.writeEnumConstant(severity);
    });
  }

  @Override
  public Id<SyncMoodlesPayload> getId() {
    return ID;
  }

  public static void register() {
    PayloadTypeRegistry.playS2C().register(ID, CODEC);
  }

  private static Map<Identifier, MoodleSeverity> read(PacketByteBuf buf) {
    int size = buf.readVarInt();
    Map<Identifier, MoodleSeverity> result = new LinkedHashMap<>(size);
    for (int i = 0; i < size; i++) {
      Identifier id = buf.readIdentifier();
      MoodleSeverity severity = buf.readEnumConstant(MoodleSeverity.class);
      result.put(id, severity);
    }
    return result;
  }
}
