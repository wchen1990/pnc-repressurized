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

package me.desht.pneumaticcraft.common.sensor.pollSensors;

import com.google.common.collect.ImmutableSet;
import me.desht.pneumaticcraft.api.item.PNCUpgrade;
import me.desht.pneumaticcraft.api.universal_sensor.IBlockAndCoordinatePollSensor;
import me.desht.pneumaticcraft.common.core.ModUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Set;

public class BlockPresenceSensor implements IBlockAndCoordinatePollSensor {

    @Override
    public String getSensorPath() {
        return "Block/Presence";
    }

    @Override
    public Set<PNCUpgrade> getRequiredUpgrades() {
        return ImmutableSet.of(ModUpgrades.BLOCK_TRACKER.get());
    }

    @Override
    public int getPollFrequency() {
        return 2;
    }

    @Override
    public boolean needsTextBox() {
        return false;
    }

    @Override
    public int getRedstoneValue(Level world, BlockPos pos, int sensorRange, String textBoxText, Set<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (!world.isEmptyBlock(p)) return 15;
        }
        return 0;
    }
}