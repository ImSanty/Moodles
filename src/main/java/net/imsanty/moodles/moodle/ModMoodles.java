package net.imsanty.moodles.moodle;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imsanty.moodles.Moodles;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Central place to declare and bootstrap all moodle types.
 */
public final class ModMoodles {
  private static final Map<Identifier, Moodle> REGISTERED = new LinkedHashMap<>();

  public static final Moodle SATIETY = register("satiety", true);
  public static final Moodle HUNGER = register("hunger", false);
  public static final Moodle PAIN = register("pain", false);

  private ModMoodles() {
  }

  private static Moodle register(String path, boolean beneficial) {
    Identifier id = Identifier.of(Moodles.MOD_ID, path);
    Moodle moodle = Moodle.builder(id)
        .displayName(Text.translatable("moodle." + id.getNamespace() + "." + id.getPath()))
        .beneficial(beneficial)
        .build();
    REGISTERED.put(id, moodle);
    return moodle;
  }

  public static void bootstrap() {
    Moodles.LOGGER.info("Registering {} moodles", REGISTERED.size());
  }

  public static Collection<Moodle> values() {
    return Collections.unmodifiableCollection(REGISTERED.values());
  }

  public static Moodle get(Identifier id) {
    return REGISTERED.get(id);
  }
}
