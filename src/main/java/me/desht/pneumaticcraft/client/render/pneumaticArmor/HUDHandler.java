package me.desht.pneumaticcraft.client.render.pneumaticArmor;

import me.desht.pneumaticcraft.PneumaticCraftRepressurized;
import me.desht.pneumaticcraft.api.client.IGuiAnimatedStat;
import me.desht.pneumaticcraft.api.client.pneumaticHelmet.IUpgradeRenderHandler;
import me.desht.pneumaticcraft.api.item.IPressurizable;
import me.desht.pneumaticcraft.client.IKeyListener;
import me.desht.pneumaticcraft.client.KeyHandler;
import me.desht.pneumaticcraft.client.gui.pneumaticHelmet.GuiHelmetMainScreen;
import me.desht.pneumaticcraft.client.gui.widget.GuiKeybindCheckBox;
import me.desht.pneumaticcraft.client.render.RenderProgressBar;
import me.desht.pneumaticcraft.common.CommonHUDHandler;
import me.desht.pneumaticcraft.common.item.Itemss;
import me.desht.pneumaticcraft.common.network.NetworkHandler;
import me.desht.pneumaticcraft.common.network.PacketToggleHelmetFeature;
import me.desht.pneumaticcraft.lib.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class HUDHandler implements IKeyListener {

    private final List<ArmorMessage> messageList = new ArrayList<ArmorMessage>();
    private boolean gaveEmptyWarning;
    private boolean gaveNearlyEmptyWarning;

    private static final HUDHandler INSTANCE = new HUDHandler();

    public static HUDHandler instance() {
        return INSTANCE;
    }

    public <T extends IUpgradeRenderHandler> T getSpecificRenderer(Class<T> clazz) {
        for (IUpgradeRenderHandler renderHandler : UpgradeRenderHandlerList.instance().upgradeRenderers) {
            if (clazz.isAssignableFrom(renderHandler.getClass())) return (T) renderHandler;
        }
        return null;
    }

    @SubscribeEvent
    public void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (!GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade.coreComponents").checked) return;
        Minecraft mc = FMLClientHandler.instance().getClient();
        EntityPlayer player = mc.player;
        double playerX = player.prevPosX + (player.posX - player.prevPosX) * event.getPartialTicks();
        double playerY = player.prevPosY + (player.posY - player.prevPosY) * event.getPartialTicks();
        double playerZ = player.prevPosZ + (player.posZ - player.prevPosZ) * event.getPartialTicks();

        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);
        ItemStack helmetStack = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (helmetStack.getItem() == Itemss.PNEUMATIC_HELMET) {
            if (((IPressurizable) helmetStack.getItem()).getPressure(helmetStack) > 0F) {
                CommonHUDHandler comHudHandler = CommonHUDHandler.getHandlerForPlayer(player);
                if (comHudHandler.ticksExisted > comHudHandler.getStartupTime()) {

                    GL11.glDisable(GL11.GL_TEXTURE_2D);

                    for (int i = 0; i < UpgradeRenderHandlerList.instance().upgradeRenderers.size(); i++) {
                        if (comHudHandler.upgradeRenderersInserted[i] && GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade." + UpgradeRenderHandlerList.instance().upgradeRenderers.get(i).getUpgradeName()).checked)
                            UpgradeRenderHandlerList.instance().upgradeRenderers.get(i).render3D(event.getPartialTicks());
                    }

                    GL11.glEnable(GL11.GL_TEXTURE_2D);

                }
            }
        }
        GL11.glPopMatrix();
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = FMLClientHandler.instance().getClient();
            if (mc != null && mc.player != null) {
                render2D(event.renderTickTime);
            }
        }
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = FMLClientHandler.instance().getClient();
            EntityPlayer player = event.player;
            if (player == mc.player) {
                ItemStack helmetStack = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
                if (helmetStack.getItem() == Itemss.PNEUMATIC_HELMET) {
                    if (player.world.isRemote) {
                        update(mc.player);
                    }
                } else {
                    CommonHUDHandler.getHandlerForPlayer(player).ticksExisted = 0;
                }
            }
        }
    }

    private void render2D(float partialTicks) {
        Minecraft minecraft = FMLClientHandler.instance().getClient();
        EntityPlayer player = minecraft.player;
        ItemStack helmetStack = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (minecraft.inGameHasFocus && helmetStack.getItem() == Itemss.PNEUMATIC_HELMET) {
            ScaledResolution sr = new ScaledResolution(minecraft);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glPushMatrix();
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glColor4d(0, 1, 0, 0.8D);
            CommonHUDHandler comHudHandler = CommonHUDHandler.getHandlerForPlayer(player);
            if (comHudHandler.ticksExisted <= comHudHandler.getStartupTime()) {
                // blockTrackInfo = null;
                gaveEmptyWarning = false;
                gaveNearlyEmptyWarning = false;
                RenderProgressBar.render(sr.getScaledWidth() / 2, 10, sr.getScaledWidth() - 10, 30, -90F, comHudHandler.ticksExisted * 100 / comHudHandler.getStartupTime());
            } else {

                if (comHudHandler.helmetPressure < 0.05F && !gaveEmptyWarning) {
                    addMessage(new ArmorMessage("The helmet is out of air!", new ArrayList<String>(), 100, 0x70FF0000));
                    gaveEmptyWarning = true;
                }
                if (comHudHandler.helmetPressure > 0.2F && comHudHandler.helmetPressure < 0.5F && !gaveNearlyEmptyWarning) {
                    addMessage(new ArmorMessage("The helmet almost out of air!", new ArrayList<String>(), 60, 0x70FF0000));
                    gaveNearlyEmptyWarning = true;
                }
                if (GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade.coreComponents").checked) {
                    for (int i = 0; i < UpgradeRenderHandlerList.instance().upgradeRenderers.size(); i++) {
                        IUpgradeRenderHandler upgradeRenderHandler = UpgradeRenderHandlerList.instance().upgradeRenderers.get(i);
                        if (comHudHandler.upgradeRenderersInserted[i] && GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade." + upgradeRenderHandler.getUpgradeName()).checked) {
                            IGuiAnimatedStat stat = upgradeRenderHandler.getAnimatedStat();
                            if (stat != null) {
                                stat.render(-1, -1, partialTicks);
                            }
                            upgradeRenderHandler.render2D(partialTicks, comHudHandler.helmetPressure > 0F);
                        }
                    }
                }
            }

            // render every item in the list.
            for (ArmorMessage message : messageList) {
                message.renderMessage(minecraft.fontRenderer, partialTicks);
            }

            GL11.glPopMatrix();
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            if (comHudHandler.ticksExisted <= comHudHandler.getStartupTime())
                minecraft.fontRenderer.drawString(CommonHUDHandler.getHandlerForPlayer().ticksExisted * 100 / comHudHandler.getStartupTime() + "%", sr.getScaledWidth() * 3 / 4 - 8, 16, 0x000000);
        } else if (helmetStack.isEmpty()) {
            messageList.clear();
        }
    }

    private void update(EntityPlayer player) {
        for (ArmorMessage message : messageList) {
            message.getStat().update();
        }
        CommonHUDHandler comHudHandler = CommonHUDHandler.getHandlerForPlayer(player);
        boolean helmetEnabled = GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade.coreComponents").checked;
        if (comHudHandler.ticksExisted == 1) {
            for (IUpgradeRenderHandler handler : UpgradeRenderHandlerList.instance().upgradeRenderers) {
                handler.reset();
            }
            for (int i = 0; i < comHudHandler.upgradeRenderersEnabled.length; i++) {
                NetworkHandler.sendToServer(new PacketToggleHelmetFeature((byte) i, helmetEnabled && GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade." + UpgradeRenderHandlerList.instance().upgradeRenderers.get(i).getUpgradeName()).checked));
            }
        }
        if (comHudHandler.ticksExisted > comHudHandler.getStartupTime() && helmetEnabled) {
            for (int i = 0; i < UpgradeRenderHandlerList.instance().upgradeRenderers.size(); i++) {
                IUpgradeRenderHandler upgradeRenderHandler = UpgradeRenderHandlerList.instance().upgradeRenderers.get(i);
                if (comHudHandler.upgradeRenderersInserted[i] && GuiKeybindCheckBox.trackedCheckboxes.get("pneumaticHelmet.upgrade." + upgradeRenderHandler.getUpgradeName()).checked) {
                    IGuiAnimatedStat stat = upgradeRenderHandler.getAnimatedStat();
                    if (stat != null) {
                        if (comHudHandler.helmetPressure > upgradeRenderHandler.getMinimumPressure()) {
                            stat.openWindow();
                        } else {
                            stat.closeWindow();
                        }
                        stat.update();
                    }
                    upgradeRenderHandler.update(player, comHudHandler.rangeUpgradesInstalled);
                }
            }
        }
        // clean the list
        for (int i = 0; i < messageList.size(); i++) {
            ArmorMessage message = messageList.get(i);
            if (message == null || --message.lifeSpan <= 0) {
                messageList.remove(i);
                i--;
            }
        }

        for (int i = 0; i < UpgradeRenderHandlerList.instance().upgradeRenderers.size(); i++) {
            if (comHudHandler.ticksExisted == comHudHandler.getStartupTime() / (UpgradeRenderHandlerList.instance().upgradeRenderers.size() + 2) * (i + 1)) {
                player.world.playSound(player.posX, player.posY, player.posZ, Sounds.HUD_INIT, SoundCategory.PLAYERS, 0.1F, 0.5F + (float) (i + 1) / (UpgradeRenderHandlerList.instance().upgradeRenderers.size() + 2) * 0.5F, true);
                boolean upgradeEnabled = comHudHandler.upgradeRenderersInserted[i];
                addMessage(new ArmorMessage(I18n.format("pneumaticHelmet.upgrade." + UpgradeRenderHandlerList.instance().upgradeRenderers.get(i).getUpgradeName()) + " " + (upgradeEnabled ? "found" : "not installed"), new ArrayList<String>(), 50, upgradeEnabled ? 0x7000AA00 : 0x70FF0000));
            }
        }

        if (comHudHandler.ticksExisted == 1) {
            player.world.playSound(player.posX, player.posY, player.posZ, Sounds.HUD_INIT, SoundCategory.PLAYERS, 0.1F, 0.5F, true);
            addMessage(new ArmorMessage("Initializing head-up display...", Collections.emptyList(), 50, 0x7000AA00));
        }

        if (comHudHandler.ticksExisted == comHudHandler.getStartupTime()) {
            player.world.playSound(player.posX, player.posY, player.posZ, Sounds.HUD_INIT_COMPLETE, SoundCategory.PLAYERS, 0.1F, 1.0F, true);
            addMessage(new ArmorMessage("Initialization complete!", Collections.emptyList(), 50, 0x7000AA00));
        }
    }

    public void addMessage(String title, List<String> message, int duration, int backColor) {
        addMessage(new ArmorMessage(title, message, duration, backColor));
    }

    public void addMessage(ArmorMessage message) {
        if (messageList.size() > 0) {
            message.setDependingMessage(messageList.get(messageList.size() - 1).getStat()); //set the depending stat of the new stat to the last stat.
        }
        messageList.add(message);
    }

    @Override
    public void onKeyPress(KeyBinding key) {
        Minecraft mc = FMLClientHandler.instance().getClient();
        if (mc.inGameHasFocus) {
            if (key == KeyHandler.getInstance().keybindOpenOptions) {
                ItemStack helmetStack = mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
                if (helmetStack.getItem() == Itemss.PNEUMATIC_HELMET) {
                    FMLCommonHandler.instance().showGuiScreen(GuiHelmetMainScreen.getInstance());
                }
            } else if (key == KeyHandler.getInstance().keybindHack && HackUpgradeRenderHandler.enabledForPlayer(mc.player)) {
                getSpecificRenderer(BlockTrackUpgradeHandler.class).hack();
                getSpecificRenderer(EntityTrackUpgradeHandler.class).hack();
            } else if (key == KeyHandler.getInstance().keybindDebuggingDrone && DroneDebugUpgradeHandler.enabledForPlayer(PneumaticCraftRepressurized.proxy.getPlayer())) {
                getSpecificRenderer(EntityTrackUpgradeHandler.class).selectAsDebuggingTarget();
            }
        }
    }

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        boolean isCaptured = getSpecificRenderer(BlockTrackUpgradeHandler.class).scroll(event);
        if (!isCaptured) isCaptured = getSpecificRenderer(EntityTrackUpgradeHandler.class).scroll(event);
        if (isCaptured) event.setCanceled(true);
    }
}
