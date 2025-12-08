package net.imsanty.moodles.moodle.network;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.imsanty.moodles.Moodles;
import net.imsanty.moodles.moodle.pain.BodyPart;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public record SyncBodyHealthPayload(Map<BodyPart, Float> health) implements CustomPayload {
  public static final Id<SyncBodyHealthPayload> ID = new Id<>(Identifier.of(Moodles.MOD_ID, "sync_body_health"));
  public static final PacketCodec<PacketByteBuf, SyncBodyHealthPayload> CODEC = CustomPayload.codecOf(
      (payload, buf) -> payload.write(buf), SyncBodyHealthPayload::new);

  public SyncBodyHealthPayload {
    health = Collections.unmodifiableMap(new EnumMap<>(health));
  }

  private SyncBodyHealthPayload(PacketByteBuf buf) {
    this(read(buf));
  }

  private void write(PacketByteBuf buf) {
    buf.writeVarInt(health.size());
    for (Map.Entry<BodyPart, Float> entry : health.entrySet()) {
      buf.writeEnumConstant(entry.getKey());
      buf.writeFloat(entry.getValue());
    }
  }

  @Override
  public Id<SyncBodyHealthPayload> getId() {
    return ID;
  }

  public static void register() {
    PayloadTypeRegistry.playS2C().register(ID, CODEC);
  }

  private static Map<BodyPart, Float> read(PacketByteBuf buf) {
    int size = buf.readVarInt();
    EnumMap<BodyPart, Float> data = new EnumMap<>(BodyPart.class);
    for (int i = 0; i < size; i++) {
      BodyPart part = buf.readEnumConstant(BodyPart.class);
      float value = buf.readFloat();
      data.put(part, value);
    }
    return data;
  }
}
