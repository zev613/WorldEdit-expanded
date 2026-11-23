/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.argument.HintedArguments;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.age.AgeBox;
import com.sk89q.worldedit.function.generator.age.AgeSettings;
import com.sk89q.worldedit.function.generator.age.EditSessionAgeWorld;
import com.sk89q.worldedit.function.generator.age.OvergrowGenerator;
import com.sk89q.worldedit.function.generator.age.RuinGenerator;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.Counts;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.Switch;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;

/**
 * Ageing a build: turning it into a ruin and letting plants grow over it.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class AgeCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public AgeCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    /**
     * Read the settings and flags typed after them, printing a message and
     * returning null if they are wrong or the selection is too big.
     */
    private AgeSettings settings(Actor actor, Region region, List<String> settings, String switches)
        throws WorldEditException {
        AgeSettings parsed;
        try {
            parsed = AgeSettings.parse(settings, switches);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return null;
        }
        AgeBox box = EditSessionAgeWorld.boxOf(region);
        if (box.volume() > AgeSettings.MAX_VOLUME) {
            actor.printError(TextComponent.of("The selection is too big (" + box.volume() + " blocks in its box); "
                + "select at most " + AgeSettings.MAX_VOLUME + " blocks and do it in parts."));
            return null;
        }
        worldEdit.checkMaxRadius(Math.max(box.maxX() - box.minX(), box.maxZ() - box.minZ()) / 2.0);
        return parsed;
    }

    @Command(
        name = "/ruin",
        aliases = { "/decay" },
        desc = "Turns the build in the selection into a ruin",
        descFooter = "Walls and roofs crumble in chunks (more at the top and outer edges), loose pieces fall, "
            + "stone cracks and gets mossy, wood rots, glass breaks and furnishings go; fallen blocks pile up as "
            + "rubble at the foot of the walls. Settings: amount (0-1, default 0.5) and seed, in order or "
            + "name=value. -r: no rubble, -g: grass, moss and dirt patches. Example: //ruin 0.7 -g"
    )
    @CommandPermissions("worldedit.region.ruin")
    @Logging(REGION)
    public int ruin(Actor actor, EditSession editSession, @Selection Region region,
                    @Arg(desc = "Settings: amount (0-1), seed; numbers in order or name=value",
                        def = "", variable = true)
                        List<HintedArguments.SettingWord> settingWords,
                    @Switch(name = 'r', desc = "Leave no rubble")
                        boolean noRubble,
                    @Switch(name = 'g', desc = "Grass, moss and dirt patches on flat surfaces")
                        boolean greenery) throws WorldEditException {
        List<String> settings = HintedArguments.strings(settingWords, HintedArguments.SettingWord::value);
        AgeSettings parsed = settings(actor, region, settings, "rg");
        if (parsed == null) {
            return 0;
        }
        EditSessionAgeWorld world = new EditSessionAgeWorld(editSession, region);
        RuinGenerator.Result result = RuinGenerator.ruin(world, EditSessionAgeWorld.boxOf(region),
            new RuinGenerator.Options(parsed.amount(), parsed.seed(),
                !(noRubble || parsed.has('r')), greenery || parsed.has('g')));
        world.rethrow();
        actor.printInfo(TextComponent.of("Ruined (amount " + parsed.amountText() + ", seed " + parsed.seed() + "): "
            + result.knockedOut() + " knocked out, " + result.collapsed() + " collapsed, " + result.weathered()
            + " weathered, " + result.rubble() + " rubble"
            + (result.greenery() > 0 ? ", " + result.greenery() + " greenery" : "")
            + "; " + result.changed() + " blocks changed."));
        return result.changed();
    }

    @Command(
        name = "/overgrow",
        aliases = { "/ivy" },
        desc = "Lets plants creep over the build and terrain in the selection",
        descFooter = "Vines hang down walls (more on north faces and in the shade), bushes gather at the foot "
            + "of walls and in corners, moss covers tops, grass, ferns and flowers grow on soil, roots and cave "
            + "vines hang under overhangs and glow lichen grows in dark spots. Plants only fill air (above amount "
            + "0.6 some stone tops turn to moss). Settings: amount (0-1, default 0.5) and seed. -n: no flowers, "
            + "-l: no glow lichen. Example: //overgrow 0.8 -n"
    )
    @CommandPermissions("worldedit.region.overgrow")
    @Logging(REGION)
    public int overgrow(Actor actor, EditSession editSession, @Selection Region region,
                        @Arg(desc = "Settings: amount (0-1), seed; numbers in order or name=value",
                            def = "", variable = true)
                            List<HintedArguments.SettingWord> settingWords,
                        @Switch(name = 'n', desc = "No flowers, just green")
                            boolean noFlowers,
                        @Switch(name = 'l', desc = "No glow lichen")
                            boolean noLichen) throws WorldEditException {
        List<String> settings = HintedArguments.strings(settingWords, HintedArguments.SettingWord::value);
        AgeSettings parsed = settings(actor, region, settings, "nl");
        if (parsed == null) {
            return 0;
        }
        EditSessionAgeWorld world = new EditSessionAgeWorld(editSession, region);
        OvergrowGenerator.Result result = OvergrowGenerator.overgrow(world, EditSessionAgeWorld.boxOf(region),
            new OvergrowGenerator.Options(parsed.amount(), parsed.seed(),
                !(noFlowers || parsed.has('n')), !(noLichen || parsed.has('l'))));
        world.rethrow();
        actor.printInfo(TextComponent.of("Overgrown (amount " + parsed.amountText() + ", seed " + parsed.seed()
            + "): " + Counts.of(result.vines(), "vine") + ", " + Counts.number(result.bushes()) + " leaves, "
            + Counts.number(result.moss()) + " moss, " + Counts.of(result.plants(), "plant") + ", "
            + Counts.number(result.hanging()) + " hanging, " + Counts.number(result.lichen()) + " lichen; "
            + Counts.of(result.changed(), "block") + " changed."));
        return result.changed();
    }
}
