package net.imsanty.moodles.duck;

public interface HungerManagerBridge {
  void moodles$setPrevFoodLevel(int value);

  void moodles$setFoodTickTimer(int value);

  int moodles$getFoodLevel();
}
