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

package com.sk89q.worldedit.command.argument;

import com.sk89q.worldedit.function.generator.warp.BuiltinWarps;
import com.sk89q.worldedit.function.generator.warp.NamedWarp;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.converter.ArgumentConverter;
import org.enginehub.piston.converter.ConversionResult;
import org.enginehub.piston.converter.FailedConversion;
import org.enginehub.piston.converter.SuccessfulConversion;
import org.enginehub.piston.inject.InjectedValueAccess;
import org.enginehub.piston.inject.Key;

import java.util.List;

import static org.enginehub.piston.converter.SuggestionHelper.limitByPrefix;

/**
 * Converts warp names for {@code //warp}.
 */
public final class NamedWarpConverter implements ArgumentConverter<NamedWarp> {

    public static void register(CommandManager commandManager) {
        commandManager.registerConverter(Key.of(NamedWarp.class), new NamedWarpConverter());
    }

    private NamedWarpConverter() {
    }

    @Override
    public Component describeAcceptableArguments() {
        return TextComponent.of("a warp name, such as twist, bend, taper or melt (see //warps)");
    }

    @Override
    public List<String> getSuggestions(String input, InjectedValueAccess context) {
        return limitByPrefix(BuiltinWarps.all().stream().map(NamedWarp::getName), input);
    }

    @Override
    public ConversionResult<NamedWarp> convert(String argument, InjectedValueAccess context) {
        return BuiltinWarps.find(argument)
            .<ConversionResult<NamedWarp>>map(SuccessfulConversion::fromSingle)
            .orElseGet(() -> FailedConversion.from(new IllegalArgumentException(
                "Unknown warp '" + argument + "'. Use //warps to see them all.")));
    }
}
