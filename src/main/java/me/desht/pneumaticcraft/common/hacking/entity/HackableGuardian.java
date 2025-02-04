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

package me.desht.pneumaticcraft.common.hacking.entity;

import me.desht.pneumaticcraft.api.client.pneumatic_helmet.IHackableEntity;
import me.desht.pneumaticcraft.common.util.Reflections;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static me.desht.pneumaticcraft.api.PneumaticRegistry.RL;
import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

public class HackableGuardian implements IHackableEntity {

    private static final ResourceLocation ID = RL("guardian");

    @Override
    public ResourceLocation getHackableId() {
        return ID;
    }

    @Override
    public boolean canHack(Entity entity, Player player) {
        return Reflections.guardian_aiGuardianAttack != null;
    }

    @Override
    public void addHackInfo(Entity entity, List<Component> curInfo, Player player) {
        curInfo.add(xlate("pneumaticcraft.armor.hacking.result.disarm"));
    }

    @Override
    public void addPostHackInfo(Entity entity, List<Component> curInfo, Player player) {
        curInfo.add(xlate("pneumaticcraft.armor.hacking.finished.disarmed"));
    }

    @Override
    public int getHackTime(Entity entity, Player player) {
        return 60;
    }

    @Override
    public void onHackFinished(Entity entity, Player player) {
        GoalSelector tasks = ((Guardian) entity).goalSelector;

        tasks.getRunningGoals()
                .filter(goal -> Reflections.guardian_aiGuardianAttack.isAssignableFrom(goal.getClass()))
                .forEach(tasks::removeGoal);

        if (entity instanceof ElderGuardian) {
            player.removeEffectNoUpdate(MobEffects.DIG_SLOWDOWN);
        }
    }

    @Override
    public boolean afterHackTick(Entity entity) {
        return false;
    }
}
