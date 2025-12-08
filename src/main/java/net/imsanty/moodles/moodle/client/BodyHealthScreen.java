package net.imsanty.moodles.moodle.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.imsanty.moodles.item.ModItems;
import net.imsanty.moodles.moodle.network.ApplyBandagePayload;
import net.imsanty.moodles.moodle.pain.BodyPart;
import net.imsanty.moodles.MoodlesClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4fStack;
import net.minecraft.util.Identifier;

public final class BodyHealthScreen extends Screen {
  private static final int SCREEN_WIDTH = 380;
  private static final int SCREEN_HEIGHT = 260;
  private static final int SIDE_MARGIN = 16;
  private static final int LEFT_SHIFT = 0;
  private static final int INFO_PANEL_OFFSET_X = 120;
  private static final int INFO_PANEL_RIGHT_MARGIN = 15;
  private static final int PANEL_INNER_PADDING = 14;
  private static final int OVERALL_BAR_WIDTH = 16;
  private static final int BAR_TEXT_GAP = 10;
  private static final int SCROLL_TRACK_WIDTH = 4;
  private static final int SCROLL_TRACK_GAP = 6;
  private static final int INJURY_RIGHT_PADDING = PANEL_INNER_PADDING + SCROLL_TRACK_GAP + SCROLL_TRACK_WIDTH;
  private static final int BODY_SECTION_TOP_OFFSET = 15;
  private static final int BODY_SECTION_BOTTOM_PADDING = 55;
  private static final int INJURY_BUTTON_HEIGHT = 34;
  private static final int INJURY_BUTTON_VERTICAL_GAP = 8;
  private static final int MAX_VISIBLE_INJURIES = 2;
  private static final int INJURY_HEADER_GAP = 36;
  private static final Identifier BG_TEXTURE = Identifier.ofVanilla("textures/gui/container/inventory.png");
  private static final int COLOR_TEXT_PRIMARY = 0xFFE0E0E0;
  private static final int COLOR_TEXT_SECONDARY = 0xFFC0C0C0;
  private static final int COLOR_TEXT_MUTED = 0xFF808080;
  private static final int COLOR_SCROLL_TRACK = 0xFF5E5E5E;
  private static final int COLOR_SCROLL_KNOB = 0xFFB0B0B0;
  private static final int COLOR_BUTTON_BAR_BG = 0xFF2F2F2F;
  private static final int COLOR_BUTTON_BG = 0xFF4A4A4A;
  private static final int COLOR_BUTTON_BG_HOVER = 0xFF6A6A6A;
  private static final int COLOR_BUTTON_BG_DISABLED = 0xFF2E2E2E;

  private final Hand hand;
  private boolean canTreat;
  private int left;
  private int top;
  private final EnumMap<BodyPart, BodyPartButton> buttons = new EnumMap<>(BodyPart.class);
  private float scrollOffset;
  private boolean scrolling;
  private int currentInjuryCount;

  private int infoPanelLeft() {
    return this.left + INFO_PANEL_OFFSET_X;
  }

  private int infoPanelRight() {
    return this.left + SCREEN_WIDTH - INFO_PANEL_RIGHT_MARGIN;
  }

  private int infoColumnX() {
    return severityBarX() + OVERALL_BAR_WIDTH + BAR_TEXT_GAP;
  }

  private int severityBarX() {
    return infoPanelLeft() + PANEL_INNER_PADDING;
  }

  private int infoTop() {
    return this.top + 32;
  }

  private int injuryListTop() {
    return bodySectionTop() + INJURY_HEADER_GAP;
  }

  private int injuryListBottom() {
    return bodySectionBottom();
  }

  private int bodySectionTop() {
    return infoTop() + BODY_SECTION_TOP_OFFSET;
  }

  private int bodySectionBottom() {
    return this.top + SCREEN_HEIGHT - BODY_SECTION_BOTTOM_PADDING;
  }

  public static void open(Hand hand, ItemStack snapshot) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player == null) {
      return;
    }
    client.setScreen(new BodyHealthScreen(hand, snapshot));
  }

  private BodyHealthScreen(Hand hand, ItemStack snapshot) {
    super(Text.translatable("screen.moodles.body_health"));
    this.hand = hand;
    MinecraftClient client = MinecraftClient.getInstance();
    PlayerEntity player = client.player;
    this.canTreat = hasBandageAvailable(player);
    this.scrollOffset = 0.0f;
    this.scrolling = false;
    this.currentInjuryCount = 0;
  }

  @Override
  protected void init() {
    super.init();
    int baseLeft = (this.width - SCREEN_WIDTH) / 2;
    int shiftedLeft = baseLeft - LEFT_SHIFT;
    this.left = Math.max(SIDE_MARGIN, Math.min(shiftedLeft, this.width - SCREEN_WIDTH - SIDE_MARGIN));
    this.top = (this.height - SCREEN_HEIGHT) / 2;

    buttons.clear();
    for (BodyPart part : BodyPart.values()) {
      BodyPartButton button = new BodyPartButton(part, this.left, this.top, 0, 0);
      button.visible = false;
      button.active = false;
      buttons.put(part, addDrawableChild(button));
    }
    this.scrollOffset = 0.0f;
    this.scrolling = false;
  }

  @Override
  public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    refreshTreatmentAvailability();
    this.renderBackground(context, mouseX, mouseY, delta);
    this.renderBackground(context, mouseX, mouseY, delta);
    drawVanillaPanel(context, left, top, SCREEN_WIDTH, SCREEN_HEIGHT);
    context.drawCenteredTextWithShadow(this.textRenderer, this.title, left + (SCREEN_WIDTH / 2), top + 6,
        COLOR_TEXT_PRIMARY);

    drawInfoBackground(context);

    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player != null) {
      int modelX = left + 65;
      int modelY = top + 172;
      int scale = 58;
      float offsetX = modelX - mouseX;
      float offsetY = modelY - mouseY;
      renderPlayerModel(context, modelX, modelY, scale, offsetX, offsetY, client.player);
    }

    float overallRatio = 0.0f;
    List<BodyPart> injuredParts = new ArrayList<>();
    for (BodyPart part : BodyPart.values()) {
      float ratio = BodyHealthClientState.ratio(part);
      overallRatio += ratio;
      if (ratio < 0.995f) {
        injuredParts.add(part);
      }
    }
    overallRatio /= BodyPart.values().length;

    renderOverallCondition(context, overallRatio);

    if (injuredParts.isEmpty()) {
      renderHealthyState(context, overallRatio);
    } else {
      renderInjuryHeader(context, overallRatio);
    }

    layoutButtons(injuredParts);
    renderScrollBar(context);

    if (canTreat) {
      Text bandageName = treatmentDisplayName();
      context.drawText(this.textRenderer, bandageName, left + 24, top + SCREEN_HEIGHT - 38, COLOR_TEXT_PRIMARY,
          false);
      context.drawText(this.textRenderer, Text.literal("Left-click to use"), left + 24, top + SCREEN_HEIGHT - 24,
          COLOR_TEXT_MUTED, false);
    } else {
      context.drawText(this.textRenderer, Text.literal("No treatment item equipped"), left + 24,
          top + SCREEN_HEIGHT - 38, COLOR_TEXT_PRIMARY, false);
      context.drawText(this.textRenderer, Text.literal("Keep a bandage in your inventory to treat injuries."),
          left + 24,
          top + SCREEN_HEIGHT - 24, COLOR_TEXT_MUTED, false);
    }

    super.render(context, mouseX, mouseY, delta);
  }

  @Override
  public boolean shouldPause() {
    return false;
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    if (!hasScrollableInjuries()) {
      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    if (isWithinInjuryList(mouseX, mouseY)) {
      scrollOffset = MathHelper.clamp(scrollOffset - (float) verticalAmount * 0.5f, 0.0f,
          getMaxScrollOffset());
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
  }

  @Override
  public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
    if (scrolling) {
      float trackTop = injuryListTop();
      float trackBottom = trackTop + getScrollTrackHeight();
      float knobHeight = MathHelper.clamp(getScrollKnobHeight(), 12.0f, (float) getScrollTrackHeight());
      float available = trackBottom - trackTop - knobHeight;
      float relative = (float) (mouseY - trackTop - knobHeight / 2.0f);
      float normalized = MathHelper.clamp(relative / Math.max(available, 1.0f), 0.0f, 1.0f);
      scrollOffset = normalized * getMaxScrollOffset();
      return true;
    }
    return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && hasScrollableInjuries() && isOverScrollKnob(mouseX, mouseY)) {
      scrolling = true;
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  public boolean mouseReleased(double mouseX, double mouseY, int button) {
    if (button == 0) {
      scrolling = false;
    }
    return super.mouseReleased(mouseX, mouseY, button);
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (MoodlesClient.bodyHealthKeyBinding() != null
        && MoodlesClient.bodyHealthKeyBinding().matchesKey(keyCode, scanCode)) {
      if (this.client != null) {
        this.client.setScreen(null);
      }
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  private void sendBandageRequest(BodyPart part) {
    if (!canTreat) {
      return;
    }
    float current = BodyHealthClientState.current(part);
    if (current >= part.maxHealth() - 0.05f) {
      return;
    }
    ClientPlayNetworking.send(new ApplyBandagePayload(part, resolveBandageHand()));
  }

  private void renderOverallCondition(DrawContext context, float ratio) {
    int headerX = infoColumnX();
    int headerY = infoTop() + 16;
    String label = "Overall ";
    int labelWidth = this.textRenderer.getWidth(label);
    context.drawText(this.textRenderer, Text.literal(label), headerX, headerY, COLOR_TEXT_PRIMARY, false);
    String percent = String.format(Locale.ROOT, "%d%%", Math.round(ratio * 100.0f));
    context.drawText(this.textRenderer, Text.literal(percent), headerX + labelWidth, headerY, COLOR_TEXT_SECONDARY,
        false);

    int barX = severityBarX();
    int barTop = bodySectionTop();
    int barBottom = bodySectionBottom();
    int barHeight = Math.max(80, barBottom - barTop);

    context.drawBorder(barX - 2, barTop - 2, OVERALL_BAR_WIDTH + 4, barHeight + 4, 0xFF3F3F3F);
    context.fill(barX, barTop, barX + OVERALL_BAR_WIDTH, barTop + barHeight, COLOR_BUTTON_BAR_BG);
    int filled = MathHelper.floor(barHeight * MathHelper.clamp(ratio, 0.0f, 1.0f));
    context.fill(barX, barTop + (barHeight - filled), barX + OVERALL_BAR_WIDTH, barTop + barHeight,
        0xFF7FC07F);
  }

  private void renderHealthyState(DrawContext context, float overallRatio) {
    int messageX = infoColumnX();
    int messageY = bodySectionTop() + 12;
    float scale = 0.9f;
    drawScaledText(context, Text.literal("No injuries detected."), messageX, messageY, scale, COLOR_TEXT_SECONDARY);
  }

  private void renderInjuryHeader(DrawContext context, float overallRatio) {
    int headerX = infoColumnX();
    int headerY = bodySectionTop() + 12;
    float scale = 0.9f;
    drawScaledText(context, Text.literal("Injuries requiring treatment."), headerX, headerY, scale,
        COLOR_TEXT_SECONDARY);
  }

  private void layoutButtons(List<BodyPart> injuredParts) {
    int buttonStartX = infoColumnX();
    int contentRight = scrollTrackX() - SCROLL_TRACK_GAP;
    int availableWidth = contentRight - buttonStartX;
    int buttonWidth = Math.max(120, availableWidth);
    int buttonHeight = INJURY_BUTTON_HEIGHT;
    int listTop = injuryListTop();
    int spacing = buttonHeight + INJURY_BUTTON_VERTICAL_GAP;

    currentInjuryCount = injuredParts.size();
    int maxVisible = Math.max(1, getMaxVisibleButtons());
    float maxOffset = Math.max(0.0f, currentInjuryCount - maxVisible);
    scrollOffset = MathHelper.clamp(scrollOffset, 0.0f, maxOffset);
    if (maxOffset <= 0.0f) {
      scrollOffset = 0.0f;
      scrolling = false;
    }
    int startIndex = MathHelper.floor(scrollOffset);
    float fractionalOffset = scrollOffset - startIndex;

    Set<BodyPart> displayed = new HashSet<>();
    int index = 0;
    int maxRenderable = maxVisible + 1;
    for (int i = startIndex; i < injuredParts.size() && index < maxRenderable; i++) {
      BodyPart part = injuredParts.get(i);
      BodyPartButton button = buttons.get(part);
      if (button != null) {
        int y = listTop + MathHelper.floor((index - fractionalOffset) * spacing);
        button.setBounds(buttonStartX, y, buttonWidth, buttonHeight);
        button.visible = true;
        button.setTreatEnabled(canTreat);
        displayed.add(part);
      }
      index++;
    }

    for (Map.Entry<BodyPart, BodyPartButton> entry : buttons.entrySet()) {
      if (!displayed.contains(entry.getKey())) {
        BodyPartButton button = entry.getValue();
        button.visible = false;
        button.setTreatEnabled(false);
      }
    }
  }

  private boolean hasScrollableInjuries() {
    if (currentInjuryCount <= 0) {
      return false;
    }
    return currentInjuryCount > getMaxVisibleButtons();
  }

  private int getMaxVisibleButtons() {
    int buttonHeight = INJURY_BUTTON_HEIGHT;
    int spacing = buttonHeight + INJURY_BUTTON_VERTICAL_GAP;
    int available = injuryListBottom() - injuryListTop();
    int fit = Math.max(1, available / spacing);
    return Math.min(MAX_VISIBLE_INJURIES, fit);
  }

  private float getMaxScrollOffset() {
    return Math.max(0.0f, currentInjuryCount - getMaxVisibleButtons());
  }

  private void renderScrollBar(DrawContext context) {
    if (!hasScrollableInjuries()) {
      return;
    }
    int trackX = scrollTrackX();
    int trackTop = injuryListTop();
    int trackBottom = trackTop + getScrollTrackHeight();
    context.fill(trackX, trackTop, trackX + SCROLL_TRACK_WIDTH, trackBottom, COLOR_SCROLL_TRACK);

    int knobHeight = MathHelper.clamp((int) getScrollKnobHeight(), 12, getScrollTrackHeight());
    int knobTop = MathHelper.floor(trackTop + (getScrollTrackHeight() - knobHeight) * getScrollProgress());
    context.fill(trackX, knobTop, trackX + SCROLL_TRACK_WIDTH, knobTop + knobHeight, COLOR_SCROLL_KNOB);
  }

  private int getScrollTrackHeight() {
    return Math.max(32, injuryListBottom() - injuryListTop());
  }

  private float getScrollKnobHeight() {
    int visible = getMaxVisibleButtons();
    if (currentInjuryCount <= 0 || currentInjuryCount <= visible) {
      return getScrollTrackHeight();
    }
    return Math.max(12.0f, getScrollTrackHeight() * (visible / (float) currentInjuryCount));
  }

  private float getScrollProgress() {
    float maxOffset = getMaxScrollOffset();
    return maxOffset <= 0.0f ? 0.0f : scrollOffset / maxOffset;
  }

  private int scrollTrackX() {
    return infoPanelRight() - PANEL_INNER_PADDING - SCROLL_TRACK_WIDTH;
  }

  private void drawScaledText(DrawContext context, Text text, int x, int y, float scale, int color) {
    MatrixStack matrices = context.getMatrices();
    matrices.push();
    matrices.translate(x, y, 0.0f);
    matrices.scale(scale, scale, 1.0f);
    context.drawText(this.textRenderer, text, 0, 0, color, false);
    matrices.pop();
  }

  private void drawInfoBackground(DrawContext context) {
    int left = infoPanelLeft();
    int top = infoTop();
    int right = infoPanelRight();
    int bottom = this.top + SCREEN_HEIGHT - 44;
    int width = Math.max(16, right - left);
    int height = Math.max(16, bottom - top);
    drawVanillaPanel(context, left, top, width, height);
    int inset = 7;
    context.fill(left + inset, top + inset, left + width - inset, top + height - inset, 0xA0101010);
  }

  private void drawVanillaPanel(DrawContext context, int x, int y, int width, int height) {
    int corner = 7;
    if (width <= corner * 2 || height <= corner * 2) {
      context.fill(x, y, x + width, y + height, 0xFF000000);
      return;
    }
    int baseU = 0;
    int baseV = 0;
    int baseWidth = 176;
    int baseHeight = 166;
    int textureSize = 256;
    int horizontalRegion = Math.max(1, baseWidth - corner * 2);
    int verticalRegion = Math.max(1, baseHeight - corner * 2);
    int innerWidth = width - corner * 2;
    int innerHeight = height - corner * 2;

    context.drawTexture(BG_TEXTURE, x, y, baseU, baseV, corner, corner, textureSize, textureSize);
    context.drawTexture(BG_TEXTURE, x + width - corner, y, baseU + baseWidth - corner, baseV, corner, corner,
        textureSize, textureSize);
    context.drawTexture(BG_TEXTURE, x, y + height - corner, baseU, baseV + baseHeight - corner, corner, corner,
        textureSize, textureSize);
    context.drawTexture(BG_TEXTURE, x + width - corner, y + height - corner, baseU + baseWidth - corner,
        baseV + baseHeight - corner, corner, corner, textureSize, textureSize);

    for (int dx = 0; dx < innerWidth; dx += horizontalRegion) {
      int drawWidth = Math.min(horizontalRegion, innerWidth - dx);
      context.drawTexture(BG_TEXTURE, x + corner + dx, y, baseU + corner, baseV, drawWidth, corner, textureSize,
          textureSize);
      context.drawTexture(BG_TEXTURE, x + corner + dx, y + height - corner, baseU + corner,
          baseV + baseHeight - corner, drawWidth, corner, textureSize, textureSize);
    }

    for (int dy = 0; dy < innerHeight; dy += verticalRegion) {
      int drawHeight = Math.min(verticalRegion, innerHeight - dy);
      context.drawTexture(BG_TEXTURE, x, y + corner + dy, baseU, baseV + corner, corner, drawHeight, textureSize,
          textureSize);
      context.drawTexture(BG_TEXTURE, x + width - corner, y + corner + dy, baseU + baseWidth - corner,
          baseV + corner, corner, drawHeight, textureSize, textureSize);
    }

    int innerLeft = x + corner;
    int innerTop = y + corner;
    context.fill(innerLeft, innerTop, innerLeft + innerWidth, innerTop + innerHeight, 0xD0121212);
  }

  private void refreshTreatmentAvailability() {
    MinecraftClient client = MinecraftClient.getInstance();
    this.canTreat = hasBandageAvailable(client.player);
  }

  private Text treatmentDisplayName() {
    ItemStack preview = treatmentPreviewStack();
    if (!preview.isEmpty()) {
      return preview.getName();
    }
    return Text.translatable(ModItems.BANDAGE.getTranslationKey());
  }

  private ItemStack treatmentPreviewStack() {
    ItemStack stack = findFirstBandageStack(MinecraftClient.getInstance().player);
    if (stack.isEmpty()) {
      return ItemStack.EMPTY;
    }
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  private Hand resolveBandageHand() {
    if (this.hand != null) {
      return this.hand;
    }
    MinecraftClient client = MinecraftClient.getInstance();
    PlayerEntity player = client.player;
    if (player != null) {
      if (player.getMainHandStack().isOf(ModItems.BANDAGE)) {
        return Hand.MAIN_HAND;
      }
      if (player.getOffHandStack().isOf(ModItems.BANDAGE)) {
        return Hand.OFF_HAND;
      }
    }
    return Hand.MAIN_HAND;
  }

  private static boolean hasBandageAvailable(PlayerEntity player) {
    return !findFirstBandageStack(player).isEmpty();
  }

  private static ItemStack findFirstBandageStack(PlayerEntity player) {
    if (player == null) {
      return ItemStack.EMPTY;
    }
    ItemStack main = player.getMainHandStack();
    if (!main.isEmpty() && main.isOf(ModItems.BANDAGE)) {
      return main;
    }
    ItemStack off = player.getOffHandStack();
    if (!off.isEmpty() && off.isOf(ModItems.BANDAGE)) {
      return off;
    }
    PlayerInventory inventory = player.getInventory();
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getStack(i);
      if (!stack.isEmpty() && stack.isOf(ModItems.BANDAGE)) {
        return stack;
      }
    }
    return ItemStack.EMPTY;
  }

  private boolean isWithinInjuryList(double mouseX, double mouseY) {
    int listLeft = infoColumnX();
    int listRight = infoPanelRight() - INJURY_RIGHT_PADDING;
    int listTop = injuryListTop();
    int listBottom = injuryListBottom();
    return mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
  }

  private boolean isOverScrollKnob(double mouseX, double mouseY) {
    if (!hasScrollableInjuries()) {
      return false;
    }
    int trackX = scrollTrackX();
    int trackWidth = SCROLL_TRACK_WIDTH;
    int trackTop = injuryListTop();
    int knobHeight = MathHelper.clamp((int) getScrollKnobHeight(), 12, getScrollTrackHeight());
    int knobTop = MathHelper.floor(trackTop + (getScrollTrackHeight() - knobHeight) * getScrollProgress());
    return mouseX >= trackX && mouseX <= trackX + trackWidth && mouseY >= knobTop && mouseY <= knobTop + knobHeight;
  }

  private Text severityDescriptor(float ratio) {
    if (ratio >= 0.75f) {
      return Text.literal("Minor bruising");
    }
    if (ratio >= 0.5f) {
      return Text.literal("Moderate trauma");
    }
    if (ratio >= 0.25f) {
      return Text.literal("Severe trauma");
    }
    return Text.literal("Critical trauma");
  }

  private static void renderPlayerModel(DrawContext context, int x, int y, int scale, float mouseX, float mouseY,
      LivingEntity entity) {
    float yawOffset = (float) Math.atan(mouseX / 40.0f);
    float pitchOffset = (float) Math.atan(mouseY / 40.0f);

    float originalBodyYaw = entity.bodyYaw;
    float originalYaw = entity.getYaw();
    float originalHeadYaw = entity.headYaw;
    float originalPitch = entity.getPitch();
    float originalPrevHeadYaw = entity.prevHeadYaw;
    float originalPrevPitch = entity.prevPitch;
    float originalPrevBodyYaw = entity.prevBodyYaw;

    Matrix4fStack modelView = RenderSystem.getModelViewStack();
    modelView.pushMatrix();
    modelView.translate(x, y, 1050.0f);
    modelView.scale(1.0f, 1.0f, -1.0f);
    RenderSystem.applyModelViewMatrix();

    MatrixStack matrices = new MatrixStack();
    matrices.translate(0.0f, 0.0f, 1000.0f);
    matrices.scale(scale, scale, scale);
    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitchOffset * 20.0f));

    entity.bodyYaw = 180.0f + yawOffset * 20.0f;
    entity.setYaw(180.0f + yawOffset * 40.0f);
    entity.setPitch(-pitchOffset * 20.0f);
    entity.headYaw = entity.getYaw();
    entity.prevHeadYaw = entity.headYaw;
    entity.prevPitch = entity.getPitch();
    entity.prevBodyYaw = entity.bodyYaw;

    DiffuseLighting.disableGuiDepthLighting();
    EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
    dispatcher.setRenderShadows(false);
    Immediate vertices = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
    float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, tickDelta, matrices, vertices, 0xF000F0);
    vertices.draw();
    dispatcher.setRenderShadows(true);
    DiffuseLighting.enableGuiDepthLighting();

    entity.bodyYaw = originalBodyYaw;
    entity.setYaw(originalYaw);
    entity.headYaw = originalHeadYaw;
    entity.setPitch(originalPitch);
    entity.prevHeadYaw = originalPrevHeadYaw;
    entity.prevPitch = originalPrevPitch;
    entity.prevBodyYaw = originalPrevBodyYaw;

    modelView.popMatrix();
    RenderSystem.applyModelViewMatrix();
  }

  private int barColor(float ratio) {
    if (ratio >= 0.75f) {
      return 0xFF78C869;
    }
    if (ratio >= 0.5f) {
      return 0xFFE5C76B;
    }
    if (ratio >= 0.25f) {
      return 0xFFDA8F4F;
    }
    return 0xFFD66763;
  }

  private class BodyPartButton extends PressableWidget {
    private final BodyPart part;
    private boolean treatEnabled = true;

    BodyPartButton(BodyPart part, int x, int y, int width, int height) {
      super(x, y, width, height, Text.empty());
      this.part = part;
    }

    void setBounds(int x, int y, int width, int height) {
      this.setX(x);
      this.setY(y);
      this.setWidth(width);
      this.height = height;
    }

    void setTreatEnabled(boolean enabled) {
      this.treatEnabled = enabled;
      this.active = enabled && this.visible;
    }

    @Override
    public void onPress() {
      sendBandageRequest(part);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      float ratio = BodyHealthClientState.ratio(part);
      int bgColor;
      if (!treatEnabled) {
        bgColor = COLOR_BUTTON_BG_DISABLED;
      } else {
        bgColor = this.isHovered() ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG;
      }
      context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

      Text label = part.displayName();
      int labelColor = treatEnabled ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED;
      context.drawText(textRenderer, label, this.getX() + 8, this.getY() + 5, labelColor, false);

      Text severity = severityDescriptor(ratio);
      int severityColor = treatEnabled ? COLOR_TEXT_SECONDARY : COLOR_TEXT_MUTED;
      context.drawText(textRenderer, severity, this.getX() + 8, this.getY() + this.height - 18, severityColor,
          false);

      String percent = String.format(Locale.ROOT, "%.0f%%", ratio * 100.0f);
      int percentWidth = textRenderer.getWidth(percent);
      int percentColor = treatEnabled ? COLOR_TEXT_PRIMARY : COLOR_TEXT_MUTED;
      context.drawText(textRenderer, percent, this.getX() + this.width - percentWidth - 8, this.getY() + 5,
          percentColor, false);

      int barX = this.getX() + 8;
      int barY = this.getY() + this.height - 6;
      int barWidth = this.width - 16;
      context.fill(barX, barY, barX + barWidth, barY + 2, COLOR_BUTTON_BAR_BG);
      int filled = MathHelper.floor(barWidth * ratio);
      int fillColor = treatEnabled ? barColor(ratio) : COLOR_SCROLL_KNOB;
      context.fill(barX, barY, barX + filled, barY + 2, fillColor);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
      builder.put(NarrationPart.TITLE, part.displayName());
    }
  }
}
