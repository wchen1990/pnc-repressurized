/*
 * This file is part of pnc-repressurized.
 *
 *     pnc-repressurized is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     pnc-repressurized is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with pnc-repressurized.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.desht.pneumaticcraft.common.item.minigun;

import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.client.IFOVModifierItem;
import me.desht.pneumaticcraft.api.item.IInventoryItem;
import me.desht.pneumaticcraft.api.item.PNCUpgrade;
import me.desht.pneumaticcraft.api.lib.Names;
import me.desht.pneumaticcraft.client.render.MinigunItemRenderer;
import me.desht.pneumaticcraft.client.util.ClientUtils;
import me.desht.pneumaticcraft.common.block.entity.ChargingStationBlockEntity;
import me.desht.pneumaticcraft.common.core.ModItems;
import me.desht.pneumaticcraft.common.core.ModMenuTypes;
import me.desht.pneumaticcraft.common.core.ModUpgrades;
import me.desht.pneumaticcraft.common.inventory.AbstractPneumaticCraftMenu;
import me.desht.pneumaticcraft.common.inventory.MinigunMagazineMenu;
import me.desht.pneumaticcraft.common.inventory.handler.BaseItemStackHandler;
import me.desht.pneumaticcraft.common.item.IChargeableContainerProvider;
import me.desht.pneumaticcraft.common.item.IShiftScrollable;
import me.desht.pneumaticcraft.common.item.PressurizableItem;
import me.desht.pneumaticcraft.common.minigun.Minigun;
import me.desht.pneumaticcraft.common.minigun.MinigunPlayerTracker;
import me.desht.pneumaticcraft.common.network.NetworkHandler;
import me.desht.pneumaticcraft.common.network.PacketMinigunStop;
import me.desht.pneumaticcraft.common.network.PacketPlaySound;
import me.desht.pneumaticcraft.common.util.NBTUtils;
import me.desht.pneumaticcraft.common.util.UpgradableItemUtils;
import me.desht.pneumaticcraft.common.util.upgrade.ApplicableUpgradesDB;
import me.desht.pneumaticcraft.lib.Log;
import me.desht.pneumaticcraft.lib.PneumaticValues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.IItemRenderProperties;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

public class MinigunItem extends PressurizableItem implements
        IChargeableContainerProvider, IFOVModifierItem,
        IInventoryItem, IShiftScrollable {
    public static final int MAGAZINE_SIZE = 4;

    private static final String NBT_MAGAZINE = "Magazine";
    public static final String NBT_LOCKED_SLOT = "LockedSlot";
    public static final String OWNING_PLAYER_ID = "owningPlayerId";

    public MinigunItem() {
        super(ModItems.toolProps(), PneumaticValues.AIR_CANISTER_MAX_AIR, PneumaticValues.AIR_CANISTER_VOLUME);
    }

    @Override
    public void initializeClient(Consumer<IItemRenderProperties> consumer) {
        consumer.accept(new MinigunItemRenderer.RenderProperties());
    }

    @Nonnull
    public MagazineHandler getMagazine(ItemStack stack) {
        Validate.isTrue(stack.getItem() instanceof MinigunItem);
        return new MagazineHandler(stack);
    }

    /**
     * Called on server only, when player equips or unequips a minigun
     * @param player the player
     * @param stack the minigun item
     * @param equipping true if equipping, false if unequipping
     */
    public void onEquipmentChange(ServerPlayer player, ItemStack stack, boolean equipping) {
        if (equipping) {
            // tag the minigun with the player's entity ID - it's sync'd to clients
            // so other clients will know who's wielding it, and render appropriately
            // See RenderItemMinigun
            stack.getOrCreateTag().putInt(OWNING_PLAYER_ID, player.getId());
        } else {
            stack.getOrCreateTag().remove(OWNING_PLAYER_ID);
            Minigun minigun = getMinigun(stack, player);
            if (minigun.getMinigunSpeed() > 0 || minigun.isMinigunActivated()) {
                NetworkHandler.sendToPlayer(new PacketMinigunStop(stack), player);
            }
            minigun.setMinigunSpeed(0);
            minigun.setMinigunActivated(false);
            minigun.setMinigunTriggerTimeOut(0);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean currentItem) {
        super.inventoryTick(stack, world, entity, slot, currentItem);

        Player player = (Player) entity;

        Minigun minigun = null;
        if (currentItem) {
            minigun = getMinigun(stack, player);
            minigun.tick(player.getX(), player.getY(), player.getZ());
        }
        if (!world.isClientSide && slot >= 0 && slot <= 8) {
            // if on hotbar, possibility of ammo replenishment via item life upgrades
            if (minigun == null) minigun = getMinigun(stack, player);
            handleAmmoRepair(stack, world, minigun);
        }
    }

    private void handleAmmoRepair(ItemStack stack, Level world, Minigun minigun) {
        if (minigun.getPlayer().containerMenu instanceof MinigunMagazineMenu) {
            return;  // avoid potential item duping or other shenanigans
        }
        int itemLife = minigun.getUpgrades(ModUpgrades.ITEM_LIFE.get());
        if (itemLife > 0) {
            MagazineHandler handler = getMagazine(stack);
            boolean repaired = false;
            float pressure = minigun.getAirCapability().orElseThrow(RuntimeException::new).getPressure();
            if (world.getGameTime() % (200 - itemLife * 35L) == 0) {
                for (int i = 0; i < handler.getSlots() && pressure > 0.25f; i++) {
                    ItemStack ammo = handler.getStackInSlot(i);
                    if (ammo.getItem() instanceof AbstractGunAmmoItem && ammo.getDamageValue() > 0) {
                        ammo.setDamageValue(ammo.getDamageValue() - 1);
                        minigun.getAirCapability().ifPresent(h -> h.addAir(-(100 * itemLife)));
                        pressure = minigun.getAirCapability().orElseThrow(RuntimeException::new).getPressure();
                        repaired = true;
                    }
                }
            }
            if (repaired) {
                handler.save();
            }
        }
    }

    private Minigun getMinigun(ItemStack stack, Player player, ItemStack ammo) {
        return new ItemMinigunImpl(player, stack)
                .setAmmoStack(ammo)
                .setAirHandler(stack.getCapability(PNCCapabilities.AIR_HANDLER_ITEM_CAPABILITY), PneumaticValues.USAGE_ITEM_MINIGUN)
                .setWorld(player.level);
    }

    public Minigun getMinigun(ItemStack stack, Player player) {
        return getMinigun(stack, player, getMagazine(stack).getAmmo());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand handIn) {
        ItemStack stack = player.getItemInHand(handIn);
        if (player.isShiftKeyDown()) {
            if (!world.isClientSide && stack.getCount() == 1) {
                NetworkHooks.openGui((ServerPlayer) player, new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return stack.getHoverName();
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int i, Inventory playerInventory, Player playerEntity) {
                        return new MinigunMagazineMenu(i, playerInventory, handIn);
                    }
                }, buf -> AbstractPneumaticCraftMenu.putHand(buf, handIn));
            }
            return InteractionResultHolder.consume(stack);
        } else {
            MagazineHandler magazineHandler = getMagazine(stack);
            ItemStack ammo = magazineHandler.getAmmo();
            if (!ammo.isEmpty()) {
                player.startUsingItem(handIn);
                return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
            }
            if (player.level.isClientSide) {
                player.playSound(SoundEvents.COMPARATOR_CLICK, 1f, 1f);
                player.displayClientMessage(new TranslatableComponent("pneumaticcraft.message.minigun.outOfAmmo"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
    }

    @Override
    public void onUsingTick(ItemStack stack, LivingEntity entity, int count) {
        if (!(entity instanceof Player player)) return;

        MagazineHandler magazineHandler = getMagazine(stack);
        ItemStack ammo = magazineHandler.getAmmo();
        if (!ammo.isEmpty()) {
            int prevDamage = ammo.getDamageValue();
            Minigun minigun = getMinigun(stack, player, ammo);
            // an item life upgrade will prevent the stack from being destroyed
            boolean usedUpAmmo = minigun.tryFireMinigun(null) && minigun.getUpgrades(ModUpgrades.ITEM_LIFE.get()) == 0;
            if (usedUpAmmo) ammo.setCount(0);
            if (usedUpAmmo || ammo.getDamageValue() != prevDamage) {
                magazineHandler.save();
            }
        } else {
            if (player.level.isClientSide) {
                player.playSound(SoundEvents.COMPARATOR_CLICK, 1f, 1f);
                player.displayClientMessage(new TranslatableComponent("pneumaticcraft.message.minigun.outOfAmmo"), true);
            }
            player.releaseUsingItem();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level worldIn, LivingEntity entityLiving) {
        return super.finishUsingItem(stack, worldIn, entityLiving);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    public float getFOVModifier(ItemStack stack, Player player, EquipmentSlot slot) {
        Minigun minigun = getMinigun(stack, player);
        int trackers = minigun.getUpgrades(ModUpgrades.ENTITY_TRACKER.get());
        if (!minigun.isMinigunActivated() || trackers == 0) return 1.0f;
        return 1 - (trackers * minigun.getMinigunSpeed() / 2);
    }

    @Override
    public void getStacksInItem(ItemStack stack, List<ItemStack> curStacks) {
        MagazineHandler handler = getMagazine(stack);
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                curStacks.add(handler.getStackInSlot(i));
            }
        }
    }

    @Override
    public Component getInventoryHeader() {
        return xlate("pneumaticcraft.gui.tooltip.gunAmmo.loaded").withStyle(ChatFormatting.GREEN);
    }

    @Override
    public MenuProvider getContainerProvider(ChargingStationBlockEntity te) {
        return new IChargeableContainerProvider.Provider(te, ModMenuTypes.CHARGING_MINIGUN.get());
    }

    @Override
    public void onShiftScrolled(Player player, boolean forward, InteractionHand hand) {
        // cycle the locked slot to the next valid ammo type (assuming any valid ammo)
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof MinigunItem) {
            MagazineHandler handler = getMagazine(stack);
            int newSlot = Math.max(0, getLockedSlot(stack));
            for (int i = 0; i < MAGAZINE_SIZE - 1; i++) {
                newSlot = (newSlot + (forward ? 1 : -1));
                if (newSlot < 0) newSlot = MAGAZINE_SIZE - 1;
                else if (newSlot >= MAGAZINE_SIZE) newSlot = 0;
                if (handler.getStackInSlot(newSlot).getItem() instanceof AbstractGunAmmoItem) {
                    // found one!
                    NBTUtils.setInteger(stack, MinigunItem.NBT_LOCKED_SLOT, newSlot);
                    return;
                }
            }
        }
    }

    public static int getLockedSlot(ItemStack stack) {
        if (NBTUtils.hasTag(stack, NBT_LOCKED_SLOT)) {
            int slot = NBTUtils.getInteger(stack, NBT_LOCKED_SLOT);
            if (slot >= 0 && slot < MAGAZINE_SIZE) {
                return slot;
            } else {
                Log.warning("removed out of range saved ammo slot: " + slot);
                NBTUtils.removeTag(stack, NBT_LOCKED_SLOT);
            }
        }
        return -1;
    }

    @Mod.EventBusSubscriber(modid = Names.MOD_ID)
    public static class Listener {
        @SubscribeEvent
        public static void onLivingAttack(LivingAttackEvent event) {
            if (event.getEntityLiving() instanceof Player player
                    && event.getSource() instanceof EntityDamageSource d && d.isThorns()) {
                // don't take thorns damage when attacking with minigun (it applies direct damage, but it's effectively ranged...)
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof MinigunItem) {
                    Minigun minigun = ((MinigunItem) stack.getItem()).getMinigun(stack, player);
                    if (minigun != null && minigun.getMinigunSpeed() >= Minigun.MAX_GUN_SPEED) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    public static class MagazineHandler extends BaseItemStackHandler {
        private final ItemStack gunStack;

        MagazineHandler(ItemStack gunStack) {
            super(MAGAZINE_SIZE);

            this.gunStack = gunStack;
            if (gunStack.hasTag() && gunStack.getTag().contains(NBT_MAGAZINE)) {
                deserializeNBT(gunStack.getTag().getCompound(NBT_MAGAZINE));
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            return itemStack.isEmpty() || itemStack.getItem() instanceof AbstractGunAmmoItem;
        }

        public ItemStack getAmmo() {
            int slot = getLockedSlot(gunStack);
            if (slot >= 0) {
                return getStackInSlot(slot);
            }
            for (int i = 0; i < MAGAZINE_SIZE; i++) {
                if (getStackInSlot(i).getItem() instanceof AbstractGunAmmoItem) {
                    return getStackInSlot(i);
                }
            }
            return ItemStack.EMPTY;
        }

        public void save() {
            if (!gunStack.isEmpty()) NBTUtils.setCompoundTag(gunStack, NBT_MAGAZINE, serializeNBT());
        }
    }

    private static class ItemMinigunImpl extends Minigun {
        private final ItemStack minigunStack;
        private final MinigunPlayerTracker tracker;

        ItemMinigunImpl(Player player, ItemStack stack) {
            super(player, false);
            tracker = MinigunPlayerTracker.getInstance(player);
            this.minigunStack = stack;
        }

        @Override
        public boolean isMinigunActivated() {
            return tracker.isActivated();
        }

        @Override
        public void setMinigunActivated(boolean activated) {
            tracker.setActivated(activated);
        }

        @Override
        public void setAmmoColorStack(@Nonnull ItemStack ammo) {
            if (!ammo.isEmpty() ) {
                tracker.setAmmoColor(getAmmoColor(ammo));
            } else {
                tracker.setAmmoColor(0);
            }
        }

        @Override
        public int getAmmoColor() {
            return tracker.getAmmoColor();
        }

        @Override
        public void playSound(SoundEvent soundName, float volume, float pitch) {
            if (!player.level.isClientSide) {
                NetworkHandler.sendToAllTracking(new PacketPlaySound(soundName, SoundSource.PLAYERS, player.blockPosition(), volume, pitch, false), player.level, player.blockPosition());
            }
        }

        @Override
        public Vec3 getMuzzlePosition() {
            float pitch = player.getXRot() * ((float) Math.PI / 180F);
            // 12 degree clockwise rotation
            float yaw = -(player.getYRot() + 13.5f) * ((float) Math.PI / 180F);
            float f2 = Mth.cos(yaw);
            float f3 = Mth.sin(yaw);
            float f4 = Mth.cos(pitch);
            float f5 = Mth.sin(pitch);
            Vec3 lookVec = new Vec3(f3 * f4, -f5, f2 * f4);
            return player.getEyePosition(0f).add(lookVec.scale(2.2f)).subtract(0, .3, 0);
        }

        @Override
        public Vec3 getLookAngle() {
            return player.getLookAngle();
        }

        @Override
        public float getParticleScale() {
            return player.getId() == ClientUtils.getClientPlayer().getId() && ClientUtils.isFirstPersonCamera() ? 0.4f : 1f;
        }

        @Override
        public float getMinigunSpeed() {
            return tracker.getRotationSpeed();
        }

        @Override
        public void setMinigunSpeed(float minigunSpeed) {
            tracker.setRotationSpeed(minigunSpeed);
        }

        @Override
        public int getMinigunTriggerTimeOut() {
            return tracker.getTriggerTimeout();
        }

        @Override
        public void setMinigunTriggerTimeOut(int minigunTriggerTimeOut) {
            tracker.setTriggerTimeout(minigunTriggerTimeOut);
        }

        @Override
        public float getMinigunRotation() {
            return tracker.getBarrelRotation();
        }

        @Override
        public void setMinigunRotation(float minigunRotation) {
            tracker.setBarrelRotation(minigunRotation);
        }

        @Override
        public float getOldMinigunRotation() {
            return tracker.getPrevBarrelRotation();
        }

        @Override
        public void setOldMinigunRotation(float oldMinigunRotation) {
            tracker.setPrevBarrelRotation(oldMinigunRotation);
        }

        @Override
        public int getUpgrades(PNCUpgrade upgrade) {
            return Math.min(ApplicableUpgradesDB.getInstance().getMaxUpgrades(minigunStack.getItem(), upgrade),
                    UpgradableItemUtils.getUpgradeCount(minigunStack, upgrade));
        }
    }
}
