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

package me.desht.pneumaticcraft.common.item;

import me.desht.pneumaticcraft.api.lib.Names;
import me.desht.pneumaticcraft.client.ColorHandlers;
import me.desht.pneumaticcraft.common.XPFluidManager;
import me.desht.pneumaticcraft.common.capabilities.FluidItemWrapper;
import me.desht.pneumaticcraft.common.core.ModFluids;
import me.desht.pneumaticcraft.common.core.ModItems;
import me.desht.pneumaticcraft.common.network.NetworkHandler;
import me.desht.pneumaticcraft.common.network.PacketLeftClickEmpty;
import me.desht.pneumaticcraft.common.thirdparty.curios.Curios;
import me.desht.pneumaticcraft.common.thirdparty.curios.CuriosUtils;
import me.desht.pneumaticcraft.common.util.EnchantmentUtils;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class MemoryStickItem extends Item implements ColorHandlers.ITintableItem, ILeftClickableItem {
    private static final String TANK_NAME = "Tank";
    private static final String NBT_ABSORB_ORBS = "AbsorbXPOrbs";
    private static final int XP_FLUID_CAPACITY = 512000;
    private static final int[] TINT_COLORS = new int[] {
                0xf7ffbf,
                0xf2ff99,
                0xedff73,
                0xe8ff4d,
                0xe3ff26,
                0xdeff00
    };

    public MemoryStickItem() {
        super(ModItems.defaultProps().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
        ItemStack stack = playerIn.getItemInHand(handIn);
        if (stack.getCount() != 1) return InteractionResultHolder.pass(stack);

        if (!worldIn.isClientSide) {
            stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).ifPresent(handler -> {
                int ratio = XPFluidManager.getInstance().getXPRatio(ModFluids.MEMORY_ESSENCE.get());
                int playerXp = EnchantmentUtils.getPlayerXP(playerIn);
                if (playerIn.isShiftKeyDown()) {
                    // take XP fluid from the stick and give to player
                    int xpToGive = EnchantmentUtils.getExperienceForLevel(playerIn.experienceLevel + 1) - playerXp;
                    int fluidAmount = xpToGive * ratio;
                    FluidStack toDrain = handler.drain(fluidAmount, IFluidHandler.FluidAction.SIMULATE);
                    if (!toDrain.isEmpty()) {
                        EnchantmentUtils.addPlayerXP(playerIn, toDrain.getAmount() / ratio);
                        handler.drain(toDrain.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                        playerIn.setItemInHand(handIn, handler.getContainer());
                    }
                } else {
                    if (playerXp > 0) {
                        // take XP from player and fill the stick
                        int xpToTake = playerXp - EnchantmentUtils.getExperienceForLevel(playerIn.experienceLevel);
                        if (xpToTake == 0) {
                            xpToTake = playerXp - EnchantmentUtils.getExperienceForLevel(playerIn.experienceLevel - 1);
                        }
                        int fluidAmount = xpToTake * ratio;
                        FluidStack toFill = new FluidStack(ModFluids.MEMORY_ESSENCE.get(), fluidAmount);
                        int filled = handler.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
                        if (filled >= ratio) {
                            EnchantmentUtils.addPlayerXP(playerIn, -(filled / ratio));
                            handler.fill(new FluidStack(ModFluids.MEMORY_ESSENCE.get(), filled), IFluidHandler.FluidAction.EXECUTE);
                            playerIn.setItemInHand(handIn, handler.getContainer());
                        }
                    }
                }
            });
        } else {
            stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).ifPresent(handler -> {
                int amount = handler.getFluidInTank(0).getAmount();
                if (EnchantmentUtils.getPlayerXP(playerIn) > 0 && amount < handler.getTankCapacity(0) && !playerIn.isShiftKeyDown()
                        || handler.getFluidInTank(0).getAmount() > 0 && playerIn.isShiftKeyDown()) {
                    playerIn.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.1f,
                            (worldIn.random.nextFloat() - worldIn.random.nextFloat()) * 0.35F + 0.9F);
                }
            });
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn);

        if (worldIn != null) {
            stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).ifPresent(handler -> {
                int ratio = XPFluidManager.getInstance().getXPRatio(ModFluids.MEMORY_ESSENCE.get());
                if (ratio > 0) {  // could be 0 if queried too early, e.g. JEI item scanning
                    FluidStack fluidStack = handler.getFluidInTank(0);
                    int amount = fluidStack.getAmount();
                    int levels = EnchantmentUtils.getLevelForExperience(amount / ratio);
                    tooltip.add(new TranslatableComponent("pneumaticcraft.gui.tooltip.memory_stick.xp_stored", amount / ratio, levels).withStyle(ChatFormatting.GREEN));
                }
            });
            boolean absorb = shouldAbsorbXPOrbs(stack);
            tooltip.add(new TranslatableComponent("pneumaticcraft.message.memory_stick.absorb." + absorb).withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public int getBarWidth(ItemStack pStack) {
        return pStack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).map(handler -> {
            FluidStack fluidStack = handler.getFluidInTank(0);
            return Math.round((float)fluidStack.getAmount() / (float) handler.getTankCapacity(0) * 13F);
        }).orElse(0);
    }

    @Override
    public boolean isBarVisible(ItemStack pStack) {
        return true;
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidItemWrapper(stack, TANK_NAME, XP_FLUID_CAPACITY, fluid -> fluid == ModFluids.MEMORY_ESSENCE.get());
    }

    public static boolean shouldAbsorbXPOrbs(ItemStack stack) {
        return stack.getItem() == ModItems.MEMORY_STICK.get()
                && stack.getCount() == 1
                && stack.hasTag() && Objects.requireNonNull(stack.getTag()).getBoolean(NBT_ABSORB_ORBS);
    }

    public static void setAbsorbXPOrbs(ItemStack stack, boolean absorb) {
        if (stack.getItem() == ModItems.MEMORY_STICK.get()) {
            stack.getOrCreateTag().putBoolean(NBT_ABSORB_ORBS, absorb);
        }
    }

    @Override
    public int getTintColor(ItemStack stack, int tintIndex) {
        return switch (tintIndex) {
            case 1 -> stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).map(handler -> {
                FluidStack fluidStack = handler.getFluidInTank(0);
                if (fluidStack.isEmpty()) return 0xFFFFFF;
                float f = (float) fluidStack.getAmount() / (float) handler.getTankCapacity(0);
                return TINT_COLORS[(int) (f * 5)];
            }).orElse(0xFFFFFFFF);
            case 2 -> shouldAbsorbXPOrbs(stack) ? 0xFF00FF00 : 0xFF808080;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public void onLeftClickEmpty(ServerPlayer sender) {
        toggleXPAbsorption(sender, sender.getMainHandItem());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        if (shouldAbsorbXPOrbs(stack) && entityIn instanceof Player && itemSlot >= 0) {
            cacheMemoryStickLocation((Player) entityIn, MemoryStickLocator.playerInv(itemSlot));
        }
    }

    public static boolean isRoomInStick(ItemStack stick) {
        return stick.getItem() instanceof MemoryStickItem && stick.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY)
                .map(h -> h.getFluidInTank(0).getAmount() < h.getTankCapacity(0))
                .orElseThrow(RuntimeException::new);
    }

    private static void toggleXPAbsorption(Player player, ItemStack stack) {
        if (stack.getItem() instanceof MemoryStickItem) {
            boolean absorb = shouldAbsorbXPOrbs(stack);
            setAbsorbXPOrbs(stack, !absorb);
            player.displayClientMessage(new TranslatableComponent("pneumaticcraft.message.memory_stick.absorb." + !absorb).withStyle(ChatFormatting.YELLOW), true);
            player.getCommandSenderWorld().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 1f, absorb ? 1.5f : 2f);
        }
    }

    public static void cacheMemoryStickLocation(Player entityIn, MemoryStickLocator locator) {
        Listener.memoryStickCache.computeIfAbsent(entityIn.getUUID(), k -> new HashSet<>()).add(locator);
    }

    @Mod.EventBusSubscriber(modid = Names.MOD_ID)
    public static class Listener {
        private static final Map<UUID, Long> lastEvent = new HashMap<>();
        private static final Map<UUID, Set<MemoryStickLocator>> memoryStickCache = new HashMap<>();

        @SubscribeEvent
        public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getItemStack().getItem() instanceof MemoryStickItem) {
                if (!event.getWorld().isClientSide) {
                    long now = event.getWorld().getGameTime();
                    long last = lastEvent.getOrDefault(event.getPlayer().getUUID(), 0L);
                    if (now - last > 5) {
                        toggleXPAbsorption(event.getPlayer(), event.getItemStack());
                        lastEvent.put(event.getPlayer().getUUID(), now);
                    }
                }
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
            // client only event, but let's be paranoid...
            if (event.getWorld().isClientSide && event.getItemStack().getItem() instanceof MemoryStickItem) {
                NetworkHandler.sendToServer(new PacketLeftClickEmpty());
            }
        }

        @SubscribeEvent
        public static void onXpOrbPickup(PlayerXpEvent.PickupXp event) {
            ItemStack stack = findMemoryStick(event.getPlayer());
            if (!stack.isEmpty()) {
                stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).ifPresent(handler -> {
                    if (PneumaticCraftUtils.fillTankWithOrb(handler, event.getOrb(), IFluidHandler.FluidAction.EXECUTE)) {
                        // orb's xp can fit in the memory stick: remove the entity, cancel the event
                        stack.setTag(handler.getContainer().getTag());
                        event.getOrb().discard();
                        event.setCanceled(true);
                    }
                });
            }
        }

        private static ItemStack findMemoryStick(Player player) {
            Set<MemoryStickLocator> locators = memoryStickCache.get(player.getUUID());
            if (locators == null || locators.isEmpty()) return ItemStack.EMPTY;

            locators.removeIf(loc -> !shouldAbsorbXPOrbs(loc.getMemoryStick(player))); // prune old entries

            // use first suitable memory stick in inventory (xp absorb switched on, not full)
            return locators.stream()
                    .map(loc -> loc.getMemoryStick(player))
                    .filter(MemoryStickItem::isRoomInStick)
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
        }
    }

    public static class MemoryStickLocator {
        final String invName; // empty string for player inv, curio inv identifier for curios inv
        final int slot;

        private MemoryStickLocator(@Nonnull String invName, int slot) {
            Validate.notNull(invName);
            Validate.isTrue(slot >= 0);
            this.invName = invName;
            this.slot = slot;
        }

        public static MemoryStickLocator playerInv(int slot) {
            return new MemoryStickLocator("", slot);
        }

        public static MemoryStickLocator namedInv(String name, int slot) {
            return new MemoryStickLocator(name, slot);
        }

        public ItemStack getMemoryStick(Player player) {
            if (invName.isEmpty()) {
                return player.getInventory().getItem(slot);
            } else if (Curios.available) {
                return CuriosUtils.getStack(player, invName, slot);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoryStickLocator that)) return false;
            return slot == that.slot && invName.equals(that.invName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(invName, slot);
        }
    }
}
