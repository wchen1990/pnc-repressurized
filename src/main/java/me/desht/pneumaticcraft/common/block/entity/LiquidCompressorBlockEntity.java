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
import me.desht.pneumaticcraft.api.pressure.PressureTier;
import me.desht.pneumaticcraft.client.util.ClientUtils;
import me.desht.pneumaticcraft.common.core.ModBlockEntities;
import me.desht.pneumaticcraft.common.fluid.FuelRegistry;
import me.desht.pneumaticcraft.common.inventory.LiquidCompressorMenu;
import me.desht.pneumaticcraft.common.inventory.handler.BaseItemStackHandler;
import me.desht.pneumaticcraft.common.network.DescSynced;
import me.desht.pneumaticcraft.common.network.GuiSynced;
import me.desht.pneumaticcraft.common.util.PNCFluidTank;
import me.desht.pneumaticcraft.lib.PneumaticValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class LiquidCompressorBlockEntity extends AbstractAirHandlingBlockEntity implements
        IRedstoneControl<LiquidCompressorBlockEntity>, ISerializableTanks, MenuProvider {
    public static final int INVENTORY_SIZE = 2;

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;

    @DescSynced
    @GuiSynced
    private final SmartSyncTank tank = new SmartSyncTank(this, PneumaticValues.NORMAL_TANK_CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return FuelRegistry.getInstance().getFuelValue(level, stack.getFluid()) > 0;
        }
    };

    private final ItemStackHandler itemHandler = new BaseItemStackHandler(this, INVENTORY_SIZE) {
        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            return itemStack.isEmpty() || FluidUtil.getFluidHandler(itemStack) != null;
        }
    };
    private final LazyOptional<IItemHandler> inventoryCap = LazyOptional.of(() -> itemHandler);
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);

    private double internalFuelBuffer;
    private float burnMultiplier = 1f;  // how fast this fuel burns (and produces pressure)
    @GuiSynced
    public final RedstoneController<LiquidCompressorBlockEntity> rsController = new RedstoneController<>(this);
    @GuiSynced
    public float airPerTick;
    private float airBuffer;
    @DescSynced
    @GuiSynced
    public boolean isProducing;

    public LiquidCompressorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LIQUID_COMPRESSOR.get(), pos, state,PressureTier.TIER_ONE, 5000);
    }

    LiquidCompressorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, PressureTier tier, int volume) {
        super(type, pos, state, tier, volume, 4);
    }

    public IFluidTank getTank() {
        return tank;
    }

    @Override
    public void tickCommonPre() {
        super.tickCommonPre();

        tank.tick();
    }

    @Override
    public void tickClient() {
        super.tickClient();

        if (isProducing && nonNullLevel().random.nextInt(5) == 0) {
            ClientUtils.emitParticles(getLevel(), getBlockPos(), ParticleTypes.SMOKE);
        }
    }

    @Override
    public void tickServer() {
        super.tickServer();

        processFluidItem(INPUT_SLOT, OUTPUT_SLOT);

        isProducing = false;

        airPerTick = getBaseProduction() * burnMultiplier * this.getSpeedMultiplierFromUpgrades() * (getHeatEfficiency() / 100f);

        if (rsController.shouldRun()) {
            double usageRate = getBaseProduction() * this.getSpeedUsageMultiplierFromUpgrades() * burnMultiplier;
            if (internalFuelBuffer < usageRate) {
                double fuelValue = FuelRegistry.getInstance().getFuelValue(level, tank.getFluid().getFluid()) / 1000D;
                if (fuelValue > 0) {
                    int usedFuel = Math.min(tank.getFluidAmount(), (int) (usageRate / fuelValue) + 1);
                    tank.drain(usedFuel, IFluidHandler.FluidAction.EXECUTE);
                    internalFuelBuffer += usedFuel * fuelValue;
                    burnMultiplier = FuelRegistry.getInstance().getBurnRateMultiplier(level, tank.getFluid().getFluid());
                }
            }
            if (internalFuelBuffer >= usageRate) {
                isProducing = true;
                internalFuelBuffer -= usageRate;

                airBuffer += airPerTick;
                if (airBuffer >= 1f) {
                    int toAdd = (int) airBuffer;
                    addAir(toAdd);
                    airBuffer -= toAdd;
                    addHeatForAir(toAdd);
                }
            }
        }
    }

    protected void addHeatForAir(int air) {
        // do nothing, override in advanced
    }

    public int getHeatEfficiency() {
        return 100;
    }

    public int getBaseProduction() {
        return 10;
    }

    @Override
    public boolean canConnectPneumatic(Direction dir) {
        Direction orientation = getRotation();
        return orientation == dir || orientation == dir.getOpposite() || dir == Direction.UP;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.put("Items", itemHandler.serializeNBT());
        tag.putDouble("internalFuelBuffer", internalFuelBuffer);
        tag.putFloat("burnMultiplier", burnMultiplier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        itemHandler.deserializeNBT(tag.getCompound("Items"));
        internalFuelBuffer = tag.getDouble("internalFuelBuffer");
        burnMultiplier = tag.getFloat("burnMultiplier");
    }

    @Override
    public void handleGUIButtonPress(String tag, boolean shiftHeld, ServerPlayer player) {
        rsController.parseRedstoneMode(tag);
    }

    @Override
    public IItemHandler getPrimaryInventory() {
        return itemHandler;
    }

    @Override
    public RedstoneController<LiquidCompressorBlockEntity> getRedstoneController() {
        return rsController;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction facing) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return fluidCap.cast();
        } else {
            return super.getCapability(cap, facing);
        }
    }

    @Nonnull
    @Override
    protected LazyOptional<IItemHandler> getInventoryCap() {
        return inventoryCap;
    }

    @Nonnull
    @Override
    public Map<String, PNCFluidTank> getSerializableTanks() {
        return ImmutableMap.of("Tank", tank);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerInventory, Player playerEntity) {
        return new LiquidCompressorMenu(i, playerInventory, getBlockPos());
    }
}
