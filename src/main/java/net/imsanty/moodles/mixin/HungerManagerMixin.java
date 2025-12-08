package net.imsanty.moodles.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.imsanty.moodles.duck.HungerManagerBridge;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

@Mixin(HungerManager.class)
public abstract class HungerManagerMixin implements HungerManagerBridge {
  @Shadow
  private int foodLevel;

  @Shadow
  private float exhaustion;

  @Shadow
  private int foodTickTimer;

  @Shadow
  private int prevFoodLevel;

  @Inject(method = "update", at = @At("HEAD"), cancellable = true)
  private void moodles$skipVanillaUpdate(PlayerEntity player, CallbackInfo ci) {
    if (player instanceof ServerPlayerEntity) {
      this.prevFoodLevel = this.foodLevel;
      this.exhaustion = 0.0f;
      this.foodTickTimer = 0;
      ci.cancel();
    }
  }

  @Override
  public void moodles$setPrevFoodLevel(int value) {
    this.prevFoodLevel = value;
  }

  @Override
  public void moodles$setFoodTickTimer(int value) {
    this.foodTickTimer = value;
  }

  @Override
  public int moodles$getFoodLevel() {
    return this.foodLevel;
  }
}
