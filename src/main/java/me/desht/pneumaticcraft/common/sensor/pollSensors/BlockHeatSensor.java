package me.desht.pneumaticcraft.common.sensor.pollSensors;

import com.google.common.collect.ImmutableSet;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import me.desht.pneumaticcraft.api.item.EnumUpgrade;
import me.desht.pneumaticcraft.api.universal_sensor.IBlockAndCoordinatePollSensor;
import me.desht.pneumaticcraft.common.heat.HeatUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;
import java.util.Set;

public class BlockHeatSensor implements IBlockAndCoordinatePollSensor {

    @Override
    public String getSensorPath() {
        return "Block/Heat";
    }

    @Override
    public Set<EnumUpgrade> getRequiredUpgrades() {
        return ImmutableSet.of(EnumUpgrade.BLOCK_TRACKER);
    }

    @Override
    public int getPollFrequency() {
        return 20;
    }

    @Override
    public boolean needsTextBox() {
        return true;
    }

    @Override
    public int getRedstoneValue(World world, BlockPos pos, int sensorRange, String textBoxText, Set<BlockPos> positions) {
        double temperature = Double.MIN_VALUE;
        for (BlockPos p : positions) {
            TileEntity te = world.getTileEntity(p);
            for (Direction d : Direction.VALUES) {
                temperature = Math.max(temperature, te.getCapability(PNCCapabilities.HEAT_EXCHANGER_CAPABILITY, d)
                        .map(IHeatExchangerLogic::getTemperature).orElse(0d));
            }
        }
        return NumberUtils.isCreatable(textBoxText) ?
                temperature - 273 > NumberUtils.toInt(textBoxText) ? 15 : 0 :
                HeatUtil.getComparatorOutput((int) temperature);
    }

    @Override
    public void getAdditionalInfo(List<ITextComponent> info) {
        info.add(new StringTextComponent("Threshold temp."));
    }
}