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

import me.desht.pneumaticcraft.common.block.entity.SecurityStationBlockEntity;
import me.desht.pneumaticcraft.common.core.ModItems;
import me.desht.pneumaticcraft.common.core.ModMenuTypes;
import me.desht.pneumaticcraft.common.inventory.RemoteMenu;
import me.desht.pneumaticcraft.common.util.GlobalPosHelper;
import me.desht.pneumaticcraft.common.util.NBTUtils;
import me.desht.pneumaticcraft.common.variables.GlobalVariableManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

public class RemoteItem extends Item {
    private static final String NBT_SECURITY_POS = "securityPos";

    public RemoteItem() {
        super(ModItems.defaultProps().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand handIn) {
        ItemStack stack = player.getItemInHand(handIn);
        if (player instanceof ServerPlayer sp) {
            openGui(sp, stack, handIn);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack remote, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity te = world.getBlockEntity(pos);

        if (te instanceof SecurityStationBlockEntity teSS && player instanceof ServerPlayer && player.isCrouching() && isAllowedToEdit(player, remote)) {
            if (teSS.doesAllowPlayer(player)) {
                GlobalPos gPos = GlobalPosHelper.makeGlobalPos(world, pos);
                setSecurityStationPos(remote, gPos);
                player.displayClientMessage(xlate("pneumaticcraft.gui.remote.boundSecurityStation", GlobalPosHelper.prettyPrint(gPos)), false);
                return InteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(xlate("pneumaticcraft.gui.remote.cantBindSecurityStation"), true);
                return InteractionResult.FAIL;
            }
        } else if (player instanceof ServerPlayer sp) {
            openGui(sp, remote, ctx.getHand());
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * allows items to add custom lines of information to the mouseover description
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack remote, Level world, List<Component> curInfo, TooltipFlag moreInfo) {
        super.appendHoverText(remote, world, curInfo, moreInfo);
        curInfo.add(xlate("pneumaticcraft.gui.remote.tooltip.sneakRightClickToEdit"));
        GlobalPos gPos = getSecurityStationPos(remote);
        if (gPos != null) {
            curInfo.add(xlate("pneumaticcraft.gui.remote.tooltip.boundToSecurityStation", GlobalPosHelper.prettyPrint(gPos)));
        } else {
            curInfo.add(xlate("pneumaticcraft.gui.remote.tooltip.rightClickToBind"));
        }
    }

    private void openGui(ServerPlayer player, ItemStack remote, InteractionHand hand) {
        if (player.isCrouching()) {
            if (isAllowedToEdit(player, remote)) {
                NetworkHooks.openGui(player, new RemoteEditorContainerProvider(remote, hand), buf -> toBytes(buf, player, hand, true));
            }
        } else {
            NetworkHooks.openGui(player, new RemoteContainerProvider(remote, hand), buf -> toBytes(buf, player, hand, false));
        }
    }

    private void toBytes(FriendlyByteBuf buf, Player player, InteractionHand hand, boolean syncGlobals) {
        // see RemoteMenu constructor for corresponding deserialisation
        buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
        if (syncGlobals) {
            Collection<String> variables = GlobalVariableManager.getInstance().getAllActiveVariableNames(player);
            buf.writeVarInt(variables.size());
            variables.forEach(buf::writeUtf);
        } else {
            buf.writeVarInt(0);
        }
    }

    public static boolean hasSameSecuritySettings(ItemStack remote1, ItemStack remote2) {
        GlobalPos g1 = getSecurityStationPos(remote1);
        GlobalPos g2 = getSecurityStationPos(remote2);
        return g1 == null && g2 == null || g1 != null && g1.equals(g2);
    }

    private boolean isAllowedToEdit(Player player, ItemStack remote) {
        GlobalPos gPos = getSecurityStationPos(remote);
        if (gPos != null) {
            BlockEntity te = GlobalPosHelper.getTileEntity(gPos);
            if (te instanceof SecurityStationBlockEntity) {
                boolean canAccess = ((SecurityStationBlockEntity) te).doesAllowPlayer(player);
                if (!canAccess) {
                    player.displayClientMessage(new TranslatableComponent("pneumaticcraft.gui.remote.noEditRights", gPos).withStyle(ChatFormatting.RED), false);
                }
                return canAccess;
            }
        }
        return true;
    }

    private static GlobalPos getSecurityStationPos(ItemStack stack) {
        return stack.hasTag() && Objects.requireNonNull(stack.getTag()).contains(NBT_SECURITY_POS) ?
                GlobalPosHelper.fromNBT(stack.getTag().getCompound(NBT_SECURITY_POS)) : null;
    }

    private static void setSecurityStationPos(ItemStack stack, GlobalPos gPos) {
        NBTUtils.setCompoundTag(stack, NBT_SECURITY_POS, GlobalPosHelper.toNBT(gPos));
    }

    @Override
    public void inventoryTick(ItemStack remote, Level world, Entity entity, int slot, boolean holdingItem) {
        if (!world.isClientSide) {
            GlobalPos gPos = getSecurityStationPos(remote);
            if (gPos != null) {
                BlockEntity te = GlobalPosHelper.getTileEntity(gPos);
                if (!(te instanceof SecurityStationBlockEntity) && remote.hasTag()) {
                    Objects.requireNonNull(remote.getTag()).remove(NBT_SECURITY_POS);
                }
            }
        }
    }

    static class RemoteContainerProvider implements MenuProvider {
        private final ItemStack stack;
        private final InteractionHand hand;

        RemoteContainerProvider(ItemStack stack, InteractionHand hand) {
            this.stack = stack;
            this.hand = hand;
        }

        @Override
        public Component getDisplayName() {
            return stack.getHoverName();
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player playerEntity) {
            return new RemoteMenu(getType(), windowId, playerInventory, hand);
        }

        protected MenuType<? extends RemoteMenu> getType() {
            return ModMenuTypes.REMOTE.get();
        }
    }

    static class RemoteEditorContainerProvider extends RemoteContainerProvider {
        RemoteEditorContainerProvider(ItemStack stack, InteractionHand hand) {
            super(stack, hand);
        }

        @Override
        protected MenuType<? extends RemoteMenu> getType() {
            return ModMenuTypes.REMOTE_EDITOR.get();
        }
    }
}
