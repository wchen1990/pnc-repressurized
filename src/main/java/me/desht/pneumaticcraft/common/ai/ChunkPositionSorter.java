package me.desht.pneumaticcraft.common.ai;

import me.desht.pneumaticcraft.common.progwidgets.IBlockOrdered.Ordering;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Comparator;

public class ChunkPositionSorter implements Comparator<BlockPos> {
    private static final double EPSILON = 0.01;

    private final double x, y, z;
    private final Ordering order;

    ChunkPositionSorter(IDroneBase entity) {
        this(entity, Ordering.CLOSEST);
    }

    ChunkPositionSorter(IDroneBase entity, Ordering order) {
        Vector3d vec = entity.getDronePos();
        // work from middle of the block the drone is in (try to minimize inconsistency)
        x = Math.floor(vec.x) + 0.5;
        y = Math.floor(vec.y) + 0.5;
        z = Math.floor(vec.z) + 0.5;

        this.order = order;
    }

    public ChunkPositionSorter(double x, double y, double z, Ordering order) {
        this.order = order;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int compare(BlockPos c1, BlockPos c2) {
        if (order != Ordering.CLOSEST && c1.getY() != c2.getY()) {
            return order == Ordering.HIGH_TO_LOW ? c2.getY() - c1.getY() : c1.getY() - c2.getY();
        } else {
            double d = PneumaticCraftUtils.distBetweenSq(c1.getX(), c1.getY(), c1.getZ(), x, y, z)
                    - PneumaticCraftUtils.distBetweenSq(c2.getX(), c2.getY(), c2.getZ(), x, y, z);
            if (Math.abs(d) < EPSILON) {
                return c1.compareTo(c2);
            } else {
                return d < 0 ? -1 : 1;
            }
        }
    }
}
