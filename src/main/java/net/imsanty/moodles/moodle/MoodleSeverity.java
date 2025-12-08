package net.imsanty.moodles.moodle;

/**
 * Basic severity tiers for a moodle. These mirror Project Zomboid's escalating
 * warnings.
 */
public enum MoodleSeverity {
  NONE,
  MINOR,
  MODERATE,
  MAJOR,
  CRITICAL;

  /**
   * @return true when the moodle should be shown to the player.
   */
  public boolean isActive() {
    return this != NONE;
  }

  /**
   * Increase severity by one step without exceeding the maximum level.
   */
  public MoodleSeverity increase() {
    return switch (this) {
      case NONE -> MINOR;
      case MINOR -> MODERATE;
      case MODERATE -> MAJOR;
      case MAJOR, CRITICAL -> CRITICAL;
    };
  }

  /**
   * Decrease severity by one step without going below {@link #NONE}.
   */
  public MoodleSeverity decrease() {
    return switch (this) {
      case CRITICAL -> MAJOR;
      case MAJOR -> MODERATE;
      case MODERATE -> MINOR;
      case MINOR, NONE -> NONE;
    };
  }
}
