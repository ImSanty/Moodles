package net.imsanty.moodles.moodle.network;

import net.imsanty.moodles.Moodles;
import net.imsanty.moodles.moodle.pain.BodyPart;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public record ApplyBandagePayload(BodyPart part, Hand hand) implements CustomPayload {
  public static final Id<ApplyBandagePayload> ID = new Id<>(Identifier.of(Moodles.MOD_ID, "apply_bandage"));
  public static final PacketCodec<PacketByteBuf, ApplyBandagePayload> CODEC = CustomPayload.codecOf(
      (payload, buf) -> payload.write(buf), ApplyBandagePayload::new);

  public ApplyBandagePayload {
    part = part == null ? BodyPart.TORSO : part;
    hand = hand == null ? Hand.MAIN_HAND : hand;
  }

  private ApplyBandagePayload(PacketByteBuf buf) {
    this(buf.readEnumConstant(BodyPart.class), buf.readEnumConstant(Hand.class));
  }

  private void write(PacketByteBuf buf) {
    buf.writeEnumConstant(part);
    buf.writeEnumConstant(hand);
  }

  @Override
  public Id<ApplyBandagePayload> getId() {
    return ID;
  }

  public static void register() {
    PayloadTypeRegistry.playC2S().register(ID, CODEC);
  }
}
