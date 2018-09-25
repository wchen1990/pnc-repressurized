package me.desht.pneumaticcraft.common.recipes.programs;

import me.desht.pneumaticcraft.common.item.ItemAssemblyProgram;
import me.desht.pneumaticcraft.common.recipes.AssemblyRecipe;
import me.desht.pneumaticcraft.common.tileentity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

public class ProgramDrillLaser extends AssemblyProgram {

    @Override
    public EnumMachine[] getRequiredMachines() {
        return new EnumMachine[]{EnumMachine.PLATFORM, EnumMachine.IO_UNIT_EXPORT, EnumMachine.IO_UNIT_IMPORT, EnumMachine.DRILL, EnumMachine.LASER};
    }

    @Override
    public boolean executeStep(TileEntityAssemblyController controller, TileEntityAssemblyPlatform platform, TileEntityAssemblyIOUnit ioUnitImport, TileEntityAssemblyIOUnit ioUnitExport, TileEntityAssemblyDrill drill, TileEntityAssemblyLaser laser) {
        boolean useAir = true;

        if (!platform.getHeldStack().isEmpty()) {
            if (canItemBeDrilled(platform.getHeldStack())) {
                drill.goDrilling();
            } else if (drill.isIdle() && canItemBeLasered(platform.getHeldStack())) {
                laser.startLasering();
            } else if (drill.isIdle() && laser.isIdle()) {
                useAir = ioUnitExport.pickupItem(null);
            }
        } else if (!ioUnitExport.isIdle()) {
            useAir = ioUnitExport.pickupItem(null);
        } else {
            List<AssemblyRecipe> recipes = new ArrayList<>();
            recipes.addAll(getRecipeList());
            recipes.addAll(new ProgramDrill().getRecipeList());
            recipes.addAll(new ProgramLaser().getRecipeList());
            useAir = ioUnitImport.pickupItem(recipes);
        }

        return useAir;
    }

    /*
    private boolean canItemBeProcessed(ItemStack item) {
    	return(this.canItemBeDrilled(item) && this.canItemBeLasered(item));
    }
    */

    private boolean canItemBeLasered(ItemStack item) {
        for (AssemblyRecipe recipe : AssemblyRecipe.laserRecipes) {
            if (isValidInput(recipe, item)) return true;
        }
        return false;
    }

    private boolean canItemBeDrilled(ItemStack item) {
        for (AssemblyRecipe recipe : AssemblyRecipe.drillRecipes) {
            if (isValidInput(recipe, item)) return true;
        }
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {

    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {

    }

    @Override
    public List<AssemblyRecipe> getRecipeList() {
        return AssemblyRecipe.drillLaserRecipes;
    }

    @Override
    protected int getItemMeta() {
        return ItemAssemblyProgram.DRILL_LASER_DAMAGE;
    }

}
