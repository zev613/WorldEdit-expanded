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


package com.sk89q.worldedit.function.generator;

import java.util.List;
import java.util.Locale;

/**
 * Lets players type a name instead of a number for settings that pick one of a few choices.
 */
public final class NamedSettings {

    private NamedSettings() {
    }

    /**
     * Turn named choices into numbers: a bare word such as {@code flat}, or {@code setting=flat},
     * becomes {@code setting=<its number>}. Other settings are left alone.
     *
     * @param arguments the settings
     * @param setting the setting the names are for
     * @param names the names, by number (several names for one number may be joined with {@code |})
     * @return the settings with names replaced
     */
    public static List<String> replaceNames(List<String> arguments, String setting, List<String> names) {
        String prefix = setting + "=";
        return arguments.stream().map(argument -> {
            String word = argument.trim().toLowerCase(Locale.ROOT);
            if (word.startsWith(prefix)) {
                word = word.substring(prefix.length());
            }
            for (int i = 0; i < names.size(); i++) {
                for (String name : names.get(i).split("\\|")) {
                    if (name.equals(word)) {
                        return prefix + i;
                    }
                }
            }
            return argument;
        }).toList();
    }

    /**
     * The names of the choices, for help text, e.g. {@code 0 round, 1 flat, 2 bell}.
     *
     * @param names the names, by number
     * @return the text
     */
    public static String describe(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(i).append(' ').append(names.get(i).split("\\|")[0]);
        }
        return text.toString();
    }
}
