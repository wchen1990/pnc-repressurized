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

package me.desht.pneumaticcraft.common.block.entity;

import com.google.common.collect.ImmutableMap;
import me.desht.pneumaticcraft.api.lib.Names;
import me.desht.pneumaticcraft.api.pressure.PressureTier;
import me.desht.pneumaticcraft.client.util.ClientUtils;
import me.desht.pneumaticcraft.common.PneumaticCraftTags;
import me.desht.pneumaticcraft.common.core.ModBlockEntities;
import me.desht.pneumaticcraft.common.core.ModBlocks;
import me.desht.pneumaticcraft.common.core.ModUpgrades;
import me.desht.pneumaticcraft.common.entity.drone.DroneEntity;
import me.desht.pneumaticcraft.common.inventory.VacuumTrapMenu;
import me.desht.pneumaticcraft.common.item.SpawnerCoreItem.SpawnerCoreItemHandler;
import me.desht.pneumaticcraft.common.network.DescSynced;
import me.desht.pneumaticcraft.common.network.GuiSynced;
import me.desht.pneumaticcraft.common.util.ITranslatableEnum;
import me.desht.pneumaticcraft.common.util.PNCFluidTank;
import me.desht.pneumaticcraft.lib.PneumaticValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VacuumTrapBlockEntity extends AbstractAirHandlingBlockEntity implements
        IMinWorkingPressure, MenuProvider, ISerializableTanks, IRangedTE {
    static final String DEFENDER_TAG = Names.MOD_ID + ":defender";
    public static final int MEMORY_ESSENCE_AMOUNT = 100;

    private final SpawnerCoreItemHandler inv = new SpawnerCoreItemHandler(this);
    private final LazyOptional<IItemHandler> invCap = LazyOptional.of(() -> inv);

    private final List<Mob> targetEntities = new ArrayList<>();

    private final RangeManager rangeManager = new RangeManager(this, 0x60600060);

    @GuiSynced
    private final SmartSyncTank xpTank = new XPTank();
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> xpTank);

    @DescSynced
    private boolean isCoreLoaded;
    @DescSynced
    public Problems problem = Problems.OK;

    public VacuumTrapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VACUUM_TRAP.get(), pos, state, PressureTier.TIER_ONE, PneumaticValues.VOLUME_VACUUM_TRAP, 4);
    }

    @Override
    public void tickCommonPre() {
        super.tickCommonPre();

        xpTank.tick();

        rangeManager.setRange(3 + getUpgrades(ModUpgrades.RANGE.get()));
    }

    @Override
    public void tickClient() {
        super.tickClient();

        if (isOpen() && isCoreLoaded && nonNullLevel().random.nextBoolean()) {
            ClientUtils.emitParticles(level, worldPosition, ParticleTypes.PORTAL);
        }
    }

    @Override
    public void tickServer() {
        super.tickServer();

        isCoreLoaded = inv.getStats() != null;

        if (isOpen() && isCoreLoaded && inv.getStats().getUnusedPercentage() > 0 && getPressure() <= getMinWorkingPressure()) {
            if ((nonNullLevel().getGameTime() & 0xf) == 0) {
                scanForEntities();
            }
            Vec3 trapVec = Vec3.atCenterOf(worldPosition);
            double min = nonNullLevel().getFluidState(worldPosition).getType() == Fluids.WATER ? 2.5 : 1.75;
            for (Mob e : targetEntities) {
                if (!e.isAlive() || e.getTags().contains(DEFENDER_TAG)) continue;
                // kludge: mobs in water seem a bit flaky about getting close enough so increase the absorb dist a bit
                if (e.distanceToSqr(trapVec) <= min) {
                    absorbEntity(e);
                    addAir((int) (PneumaticValues.USAGE_VACUUM_TRAP * e.getHealth()));
                } else {
                    e.getNavigation().moveTo(trapVec.x(), trapVec.y(), trapVec.z(), 1.2);
                }
            }
        }
        if (!isCoreLoaded)
            problem = Problems.NO_CORE;
        else if (inv.getStats().getUnusedPercentage() == 0)
            problem = Problems.CORE_FULL;
        else if (!isOpen())
            problem = Problems.TRAP_CLOSED;
        else
            problem = Problems.OK;
    }

    private void absorbEntity(Mob e) {
        int toAdd = 1;
        if (xpTank.getFluid().getAmount() >= MEMORY_ESSENCE_AMOUNT) {
            toAdd += e.level.random.nextInt(3) + 1;
        }
        if (inv.getStats().addAmount(e.getType(), toAdd)) {
            e.discard();
            if (toAdd > 1) xpTank.drain(MEMORY_ESSENCE_AMOUNT, IFluidHandler.FluidAction.EXECUTE);
            inv.getStats().serialize(inv.getStackInSlot(0));
            e.level.playSound(null, worldPosition, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1f, 2f);
            if (level instanceof ServerLevel) {
                ((ServerLevel) level).sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.5, e.getZ(), 5, 0, 1, 0, 0);
            }
        }
    }

    private void scanForEntities() {
        targetEntities.clear();
        targetEntities.addAll(nonNullLevel().getEntitiesOfClass(Mob.class, rangeManager.getExtents(), this::isApplicable));
    }

    private boolean isApplicable(LivingEntity e) {
        return e.canChangeDimensions()
                && !(e instanceof DroneEntity)
                && !(e instanceof TamableAnimal && ((TamableAnimal) e).isTame())
                && !isEntityBlacklisted(e.getType());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return fluidCap.cast();
        } else {
            return super.getCapability(cap, side);
        }
    }

    public IFluidTank getFluidTank() {
        return xpTank;
    }

    @Nonnull
    @Override
    public Map<String, PNCFluidTank> getSerializableTanks() {
        return ImmutableMap.of("Tank", xpTank);
    }

    @Override
    public IItemHandler getPrimaryInventory() {
        return inv;
    }

    @Nonnull
    @Override
    protected LazyOptional<IItemHandler> getInventoryCap() {
        return invCap;
    }

    @Override
    public boolean canConnectPneumatic(Direction side) {
        return side == Direction.DOWN || side.getAxis() == getRotation().getAxis();
    }

    @Override
    public float getMinWorkingPressure() {
        return -0.5f;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        return new VacuumTrapMenu(windowId, inv, getBlockPos());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        inv.deserializeNBT(tag.getCompound("Items"));
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.put("Items", inv.serializeNBT());
    }

    @Override
    public void getContentsToDrop(NonNullList<ItemStack> drops) {
        // if we're wrenching, any spawner core should stay in the trap
        if (!shouldPreserveStateOnBreak()) {
            super.getContentsToDrop(drops);
        }
    }

    @Override
    public void serializeExtraItemData(CompoundTag blockEntityTag, boolean preserveState) {
        super.serializeExtraItemData(blockEntityTag, preserveState);

        if (preserveState) {
            // if wrenching, spawner core stays inside the trap when broken
            blockEntityTag.put("Items", inv.serializeNBT());
        }
    }

    public boolean isOpen() {
        return getBlockState().getBlock() == ModBlocks.VACUUM_TRAP.get() && getBlockState().getValue(BlockStateProperties.OPEN);
    }

    @Override
    public RangeManager getRangeManager() {
        return rangeManager;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return rangeManager.shouldShowRange() ? rangeManager.getExtents() : super.getRenderBoundingBox();
    }

    private static boolean isEntityBlacklisted(EntityType<?> type) {
        return type.is(PneumaticCraftTags.EntityTypes.VACUUM_TRAP_BLACKLISTED);
    }

    public enum Problems implements ITranslatableEnum {
        OK,
        NO_CORE,
        CORE_FULL,
        TRAP_CLOSED;

        @Override
        public String getTranslationKey() {
            return "pneumaticcraft.gui.tab.problems.vacuum_trap." + this.toString().toLowerCase(Locale.ROOT);
        }
    }

    private class XPTank extends SmartSyncTank {
        public XPTank() {
            super(VacuumTrapBlockEntity.this, 16000);
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid().is(PneumaticCraftTags.Fluids.EXPERIENCE);
        }
    }

    @Mod.EventBusSubscriber(modid = Names.MOD_ID)
    public static class Listener {
        @SubscribeEvent
        public static void onMobSpawn(LivingSpawnEvent.SpecialSpawn event) {
            // tag any mob spawned by a vanilla Spawner (rather than naturally) as a "defender"
            // such defenders are immune to being absorbed by a Vacuum Trap
            // note: mobs spawned by a Pressurized Spawner are not considered to be defenders
            if (!event.isCanceled() && event.getSpawner() != null) {
                event.getEntity().addTag(DEFENDER_TAG);
            }
        }
    }
}
