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

package me.desht.pneumaticcraft.common.block;

import me.desht.pneumaticcraft.client.ColorHandlers;
import me.desht.pneumaticcraft.client.gui.AphorismTileScreen;
import me.desht.pneumaticcraft.common.block.entity.AphorismTileBlockEntity;
import me.desht.pneumaticcraft.common.config.ConfigHelper;
import me.desht.pneumaticcraft.common.core.ModBlocks;
import me.desht.pneumaticcraft.common.core.ModItems;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.List;

import static me.desht.pneumaticcraft.api.lib.NBTKeys.BLOCK_ENTITY_TAG;
import static me.desht.pneumaticcraft.api.lib.NBTKeys.NBT_EXTRA;
import static me.desht.pneumaticcraft.common.block.entity.AphorismTileBlockEntity.NBT_BACKGROUND_COLOR;
import static me.desht.pneumaticcraft.common.block.entity.AphorismTileBlockEntity.NBT_BORDER_COLOR;
import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

public class AphorismTileBlock extends AbstractPneumaticCraftBlock implements ColorHandlers.ITintableBlock, PneumaticCraftEntityBlock {
    public static final float APHORISM_TILE_THICKNESS = 1 / 16F;
    public static final BooleanProperty INVISIBLE = BooleanProperty.create("invisible");

    private static final VoxelShape[] SHAPES = new VoxelShape[] {
            Block.box(0, 0, 0, 16,  1, 16),
            Block.box(0, 15, 0, 16, 16, 16),
            Block.box(0, 0, 0, 16, 16,  1),
            Block.box(0, 0, 15, 16, 16, 16),
            Block.box(0, 0, 0,  1, 16, 16),
            Block.box(15, 0, 0, 16, 16, 16),
    };

    public AphorismTileBlock() {
        super(Block.Properties.of(Material.STONE).strength(1.5f, 4.0f).noCollission());
        registerDefaultState(getStateDefinition().any().setValue(INVISIBLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INVISIBLE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext selectionContext) {
        if (state.getBlock() == ModBlocks.APHORISM_TILE.get() && state.getValue(AphorismTileBlock.INVISIBLE)) {
            // bad mapping: should be isSneaking()
            return selectionContext.isDescending() ? SHAPES[getRotation(state).get3DDataValue()] : Shapes.empty();
        } else {
            return SHAPES[getRotation(state).get3DDataValue()];
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(directionProperty(), ctx.getClickedFace().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getBlock() == ModBlocks.APHORISM_TILE.get() && state.getValue(AphorismTileBlock.INVISIBLE) ?
                RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public void appendHoverText(ItemStack stack, BlockGetter world, List<Component> curInfo, TooltipFlag flag) {
        super.appendHoverText(stack, world, curInfo, flag);

        CompoundTag tag = stack.getTagElement(BLOCK_ENTITY_TAG);
        if (tag != null && tag.contains(NBT_EXTRA)) {
            CompoundTag subTag = tag.getCompound(NBT_EXTRA);
            if (subTag.contains(NBT_BORDER_COLOR) || subTag.contains(NBT_BACKGROUND_COLOR)) {
                ListTag l = subTag.getList(AphorismTileBlockEntity.NBT_TEXT_LINES, Tag.TAG_STRING);
                if (!l.isEmpty()) {
                    curInfo.add(xlate("gui.tooltip.block.pneumaticcraft.aphorism_tile.text").withStyle(ChatFormatting.YELLOW));
                    l.forEach(el -> curInfo.add(new TextComponent("  " + el.getAsString()).withStyle(ChatFormatting.ITALIC)));
                }
                curInfo.add(xlate("gui.tooltip.block.pneumaticcraft.aphorism_tile.reset").withStyle(ChatFormatting.DARK_GREEN));
            }
        }
    }

    /**
     * Called when the block is placed in the world.
     */
    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity entityLiving, ItemStack iStack) {
        super.setPlacedBy(world, pos, state, entityLiving, iStack);

        if (world.isClientSide) {
            PneumaticCraftUtils.getTileEntityAt(world, pos, AphorismTileBlockEntity.class).ifPresent(teAT -> {
                CompoundTag tag = iStack.getTagElement(BLOCK_ENTITY_TAG);
                if (tag != null) teAT.readFromPacket(tag);
                AphorismTileScreen.openGui(teAT, true);
                if (entityLiving instanceof Player) sendEditorMessage((Player) entityLiving);
            });
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult brtr) {
        BlockEntity te = world.getBlockEntity(pos);
        if (!(te instanceof AphorismTileBlockEntity teAT)) return InteractionResult.FAIL;

        if (!world.isClientSide && player.getItemInHand(hand).is(Tags.Items.DYES) && !teAT.isInvisible()) {
            return tryDyeTile(state, player, hand, brtr, teAT);
        } else if (world.isClientSide && hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).isEmpty()) {
            return openEditorGui(player, teAT);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult tryDyeTile(BlockState state, Player player, InteractionHand hand, BlockHitResult brtr, AphorismTileBlockEntity teAT) {
        DyeColor color = DyeColor.getColor(player.getItemInHand(hand));
        if (color != null) {
            if (clickedBorder(state, brtr.getLocation())) {
                if (teAT.getBorderColor() != color.getId()) {
                    teAT.setBorderColor(color.getId());
                    if (ConfigHelper.common().general.useUpDyesWhenColoring.get()) player.getItemInHand(hand).shrink(1);
                }
            } else {
                if (teAT.getBackgroundColor() != color.getId()) {
                    teAT.setBackgroundColor(color.getId());
                    if (ConfigHelper.common().general.useUpDyesWhenColoring.get()) player.getItemInHand(hand).shrink(1);
                }
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    private InteractionResult openEditorGui(Player player, AphorismTileBlockEntity teAT) {
        AphorismTileScreen.openGui(teAT, false);
        sendEditorMessage(player);
        return InteractionResult.SUCCESS;
    }

    private boolean clickedBorder(BlockState state, Vec3 hitVec) {
        double x = Math.abs(hitVec.x - (int) hitVec.x);
        double y = Math.abs(hitVec.y - (int) hitVec.y);
        double z = Math.abs(hitVec.z - (int) hitVec.z);
        return switch (getRotation(state)) {
            case EAST, WEST -> y < 0.1 || y > 0.9 || z < 0.1 || z > 0.9;
            case NORTH, SOUTH -> y < 0.1 || y > 0.9 || x < 0.1 || x > 0.9;
            case UP, DOWN -> x < 0.1 || x > 0.9 || z < 0.1 || z > 0.9;
        };
    }

    private void sendEditorMessage(Player player) {
        Component msg = new TextComponent(ChatFormatting.WHITE.toString())
                .append(new TranslatableComponent("pneumaticcraft.gui.aphorismTileEditor"))
                .append(new TextComponent(": "))
                .append(new TranslatableComponent("pneumaticcraft.gui.holdF1forHelp"));
        player.displayClientMessage(msg, true);
    }

    @Override
    public boolean isRotatable() {
        return true;
    }

    @Override
    protected boolean canRotateToTopOrBottom() {
        return true;
    }

    @Override
    public boolean onWrenched(Level world, Player player, BlockPos pos, Direction face, InteractionHand hand) {
        if (player != null && player.isShiftKeyDown()) {
            return PneumaticCraftUtils.getTileEntityAt(world, pos, AphorismTileBlockEntity.class).map(teAt -> {
                if (++teAt.textRotation > 3) teAt.textRotation = 0;
                teAt.sendDescriptionPacket();
                return true;
            }).orElse(false);
        } else {
            return super.onWrenched(world, player, pos, face, hand);
        }
    }

    @Override
    protected boolean rotateForgeWay() {
        return false;
    }

    @Override
    public int getTintColor(BlockState state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos, int tintIndex) {
        if (world != null && pos != null) {
            return PneumaticCraftUtils.getTileEntityAt(world, pos, AphorismTileBlockEntity.class).map(teAt -> switch (tintIndex) {
                case 0 -> // border
                        PneumaticCraftUtils.getDyeColorAsRGB(DyeColor.byId(teAt.getBorderColor()));
                case 1 -> // background
                        ColorHandlers.desaturate(PneumaticCraftUtils.getDyeColorAsRGB(DyeColor.byId(teAt.getBackgroundColor())));
                default -> 0xFFFFFFFF;
            }).orElse(0xFFFFFFFF);
        }
        return 0xFFFFFFFF;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new AphorismTileBlockEntity(pPos, pState);
    }

    public static class ItemBlockAphorismTile extends BlockItem implements ColorHandlers.ITintableItem {
        public ItemBlockAphorismTile(AphorismTileBlock blockAphorismTile) {
            super(blockAphorismTile, ModItems.defaultProps());
        }

        private static int getColor(ItemStack stack, String key, DyeColor fallback) {
            CompoundTag tag = stack.getTagElement(BLOCK_ENTITY_TAG);
            if (tag != null && tag.contains(NBT_EXTRA)) {
                return tag.getCompound(NBT_EXTRA).getInt(key);
            }
            return fallback.getId();
        }

        @Override
        public int getTintColor(ItemStack stack, int tintIndex) {
            return switch (tintIndex) {
                case 0 -> // border
                        PneumaticCraftUtils.getDyeColorAsRGB(DyeColor.byId(getColor(stack, NBT_BORDER_COLOR, DyeColor.BLUE)));
                case 1 -> // background
                        ColorHandlers.desaturate(PneumaticCraftUtils.getDyeColorAsRGB(DyeColor.byId(getColor(stack, NBT_BACKGROUND_COLOR, DyeColor.WHITE))));
                default -> 0xFFFFFF;
            };
        }
    }
}
