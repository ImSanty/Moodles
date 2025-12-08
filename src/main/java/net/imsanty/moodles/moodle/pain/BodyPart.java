package net.imsanty.moodles.moodle.pain;

import java.util.Locale;

import net.minecraft.text.Text;

/**
 * Represents a body region tracked for pain and damage accumulation.
 */
public enum BodyPart {
  HEAD("head", 6.0f),
  TORSO("torso", 10.0f),
  LEFT_ARM("left_arm", 5.0f),
  RIGHT_ARM("right_arm", 5.0f),
  LEFT_LEG("left_leg", 6.0f),
  RIGHT_LEG("right_leg", 6.0f);

  private final String translationKey;
  private final float maxHealth;

  BodyPart(String keySuffix, float maxHealth) {
    this.translationKey = "bodypart.moodles." + keySuffix;
    this.maxHealth = maxHealth;
  }

  public float maxHealth() {
    return maxHealth;
  }

  public Text displayName() {
    return Text.translatable(translationKey);
  }

  public String shortName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
