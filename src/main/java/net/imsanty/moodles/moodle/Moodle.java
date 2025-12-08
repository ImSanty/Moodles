package net.imsanty.moodles.moodle;

import java.util.Objects;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Describes a moodle type (e.g. hunger, fatigue) without any player-specific
 * state.
 */
public final class Moodle {
  private final Identifier id;
  private final Text displayName;
  private final Identifier iconTexture;
  private final boolean beneficial;

  private Moodle(Identifier id, Text displayName, Identifier iconTexture, boolean beneficial) {
    this.id = Objects.requireNonNull(id, "id");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.iconTexture = Objects.requireNonNull(iconTexture, "iconTexture");
    this.beneficial = beneficial;
  }

  public static Builder builder(Identifier id) {
    return new Builder(id);
  }

  public Identifier id() {
    return id;
  }

  public Text displayName() {
    return displayName;
  }

  public Identifier iconTexture() {
    return iconTexture;
  }

  public boolean isBeneficial() {
    return beneficial;
  }

  /**
   * Convenient builder for moodles so we can keep constructors private while
   * expanding metadata later.
   */
  public static final class Builder {
    private final Identifier id;
    private Text displayName;
    private Identifier iconTexture;
    private boolean beneficial;

    private Builder(Identifier id) {
      this.id = Objects.requireNonNull(id, "id");
      this.displayName = Text.literal(id.toString());
      this.iconTexture = Identifier.of(id.getNamespace(), "textures/gui/moodles/" + id.getPath() + ".png");
      this.beneficial = false;
    }

    public Builder displayName(Text displayName) {
      this.displayName = Objects.requireNonNull(displayName, "displayName");
      return this;
    }

    public Builder iconTexture(Identifier iconTexture) {
      this.iconTexture = Objects.requireNonNull(iconTexture, "iconTexture");
      return this;
    }

    public Builder beneficial(boolean beneficial) {
      this.beneficial = beneficial;
      return this;
    }

    public Moodle build() {
      return new Moodle(id, displayName, iconTexture, beneficial);
    }
  }
}
