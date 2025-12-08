package net.imsanty.moodles.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.imsanty.moodles.moodle.player.PlayerMoodleManager;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {
  @Unique
  private ItemStack moodles$pendingEatenStack;

  protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
    super(entityType, world);
  }

  @Inject(method = "addExhaustion", at = @At("RETURN"))
  private void moodles$afterAddExhaustion(float exhaustion, CallbackInfo ci) {
    if ((Object) this instanceof ServerPlayerEntity serverPlayer) {
      PlayerMoodleManager.onExhaustion(serverPlayer, exhaustion);
      serverPlayer.getHungerManager().setExhaustion(0.0f);
    }
  }

  @Inject(method = "eatFood", at = @At("HEAD"))
  private void moodles$rememberFood(World world, ItemStack stack, FoodComponent food,
      CallbackInfoReturnable<ItemStack> cir) {
    if ((Object) this instanceof ServerPlayerEntity && food != null && !stack.isEmpty()) {
      this.moodles$pendingEatenStack = stack.copy();
    }
  }

  @Inject(method = "eatFood", at = @At("RETURN"))
  private void moodles$afterEatFood(World world, ItemStack stack, FoodComponent food,
      CallbackInfoReturnable<ItemStack> cir) {
    if ((Object) this instanceof ServerPlayerEntity serverPlayer) {
      ItemStack eatenStack = this.moodles$pendingEatenStack != null ? this.moodles$pendingEatenStack : stack.copy();
      this.moodles$pendingEatenStack = null;
      PlayerMoodleManager.onFoodEaten(serverPlayer, eatenStack, food);
    }
  }

  @Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
  private void moodles$allowConstantEating(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
    if (!ignoreHunger && (Object) this instanceof ServerPlayerEntity serverPlayer) {
      if (PlayerMoodleManager.canAlwaysEat(serverPlayer)) {
        cir.setReturnValue(true);
      }
    }
  }
}
