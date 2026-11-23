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

import com.sk89q.worldedit.command.util.ShapeLibraryHolder;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
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
 * Converts shape names (and saved presets) for {@code //shape}.
 */
public final class NamedShapeConverter implements ArgumentConverter<NamedShape> {

    public static void register(CommandManager commandManager) {
        commandManager.registerConverter(Key.of(NamedShape.class), new NamedShapeConverter());
    }

    private NamedShapeConverter() {
    }

    @Override
    public Component describeAcceptableArguments() {
        return TextComponent.of("a shape name, such as heart, star, torus or knot (see //shapes)");
    }

    @Override
    public List<String> getSuggestions(String input, InjectedValueAccess context) {
        return limitByPrefix(ShapeLibraryHolder.get().getAllNames().stream(), input);
    }

    @Override
    public ConversionResult<NamedShape> convert(String argument, InjectedValueAccess context) {
        return ShapeLibraryHolder.get().find(argument)
            .<ConversionResult<NamedShape>>map(SuccessfulConversion::fromSingle)
            .orElseGet(() -> FailedConversion.from(new IllegalArgumentException(
                "Unknown shape '" + argument + "'. Use //shapes to see them all.")));
    }
}
