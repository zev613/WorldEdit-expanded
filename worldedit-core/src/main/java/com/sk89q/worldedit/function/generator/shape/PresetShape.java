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

package com.sk89q.worldedit.function.generator.shape;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.math.Vector3;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A saved shortcut: a built-in shape with some of its settings changed.
 * Presets are made from a shape name and numbers only, never formulas.
 */
public final class PresetShape extends NamedShape {

    private final NamedShape base;
    private final ShapeParameters settings;

    /**
     * Create a preset.
     *
     * @param name the preset name
     * @param base the shape it is based on
     * @param arguments the settings, as the player typed them (numbers or name=value)
     * @throws IllegalArgumentException if the settings are not valid for the base shape
     */
    public PresetShape(String name, NamedShape base, List<String> arguments) {
        this(name, checkNotNull(base), ShapeParameters.parse(base.getParameters(), arguments));
    }

    private PresetShape(String name, NamedShape base, ShapeParameters settings) {
        super(name, List.of(), "Presets", "Preset: " + describe(base, settings),
            base.getParameters().stream()
                .map(p -> new ShapeParameter(p.name(), settings.get(p.name()), p.min(), p.max(), p.description()))
                .collect(ImmutableList.toImmutableList()));
        this.base = base;
        this.settings = settings;
    }

    private static String describe(NamedShape base, ShapeParameters settings) {
        String values = settings.asMap().entrySet().stream()
            .map(e -> e.getKey() + "=" + ShapeParameter.format(e.getValue()))
            .collect(Collectors.joining(" "));
        return base.getName() + (values.isEmpty() ? "" : " " + values);
    }

    public NamedShape getBase() {
        return base;
    }

    /**
     * The settings in name=value form, suitable for saving.
     *
     * @return the settings
     */
    public List<String> getSettingArguments() {
        return settings.asMap().entrySet().stream()
            .map(e -> e.getKey() + "=" + ShapeParameter.format(e.getValue()))
            .collect(ImmutableList.toImmutableList());
    }

    public Map<String, Double> getSettings() {
        return settings.asMap();
    }

    @Override
    public boolean isCustom() {
        return true;
    }

    @Override
    public ShapeInstance create(ShapeParameters parameters, Vector3 radii) {
        return base.create(parameters, radii);
    }
}
