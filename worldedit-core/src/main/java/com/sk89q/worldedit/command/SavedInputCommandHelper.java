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

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.factory.parser.saved.SavedInputParser;
import com.sk89q.worldedit.extension.factory.parser.saved.SavedInputStore;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.internal.registry.AbstractFactory;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.session.request.RequestExtent;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

/**
 * The shared logic of {@code //pattern} and {@code //mask} (save, delete, list, info).
 *
 * @param <E> pattern or mask
 */
final class SavedInputCommandHelper<E> {

    private final AbstractFactory<E> factory;
    private final String kind;
    private final String useCommand;

    /**
     * Create a helper.
     *
     * @param factory the pattern or mask factory
     * @param kind "pattern" or "mask"
     * @param useCommand a command to suggest when a name is clicked, e.g. "//set "
     */
    SavedInputCommandHelper(AbstractFactory<E> factory, String kind, String useCommand) {
        this.factory = factory;
        this.kind = kind;
        this.useCommand = useCommand;
    }

    @SuppressWarnings("unchecked")
    private SavedInputParser<E> parser() {
        for (InputParser<E> parser : factory.getParsers()) {
            if (parser instanceof SavedInputParser<?> saved) {
                return (SavedInputParser<E>) saved;
            }
        }
        throw new IllegalStateException("Saved " + kind + "s are not registered");
    }

    private SavedInputStore store() {
        return parser().getStore();
    }

    static ParserContext createContext(Actor actor, LocalSession session) {
        ParserContext context = new ParserContext();
        context.setActor(actor);
        if (actor instanceof Player player) {
            context.setWorld(player.getWorld());
            context.setExtent(new RequestExtent());
        } else if (session.hasWorldOverride()) {
            World world = session.getWorldOverride();
            context.setWorld(world);
            context.setExtent(new RequestExtent());
        }
        context.setSession(session);
        context.setRestricted(true);
        return context;
    }

    void save(Actor actor, LocalSession session, String rawName, List<String> words) {
        String name = SavedInputStore.normalize(rawName);
        if (!SavedInputStore.isValidName(name)) {
            actor.printError(TextComponent.of("Names can only use letters, digits, _ and - (up to 32 characters)."));
            return;
        }
        String value = String.join(" ", words).trim();
        if (value.isEmpty()) {
            actor.printError(TextComponent.of("Give the " + kind + " to save, e.g. //" + kind + " save " + name + " "
                + ("pattern".equals(kind) ? "50%stone_bricks,30%cracked_stone_bricks,20%mossy_stone_bricks"
                : "stone,andesite,diorite")));
            return;
        }
        if (value.length() > SavedInputStore.MAX_VALUE_LENGTH) {
            actor.printError(TextComponent.of("That " + kind + " is too long (at most "
                + SavedInputStore.MAX_VALUE_LENGTH + " characters)."));
            return;
        }
        SavedInputParser<E> parser = parser();
        ParserContext context = createContext(actor, session);
        if (parser.isReserved(name, context)) {
            actor.printError(TextComponent.of("#" + name + " is already a built-in " + kind
                + "; pick another name."));
            return;
        }
        try {
            parser.validate(name, value, context);
        } catch (InputParseException e) {
            actor.printError(TextComponent.of("Not saved, that " + kind + " doesn't work: ").append(e.getRichMessage()));
            return;
        }
        String previous = parser.getStore().put(name, value);
        actor.printInfo(TextComponent.of((previous == null ? "Saved " : "Replaced ") + kind + " ")
            .append(useLink(name, value))
            .append(TextComponent.of(" = " + value + ". Use it as #" + name + " anywhere a " + kind + " is accepted.")));
    }

    void delete(Actor actor, String rawName) {
        String name = SavedInputStore.normalize(rawName);
        SavedInputStore store = store();
        if (!store.remove(name)) {
            actor.printError(TextComponent.of("There is no saved " + kind + " called #" + name
                + ". See //" + kind + " list."));
            return;
        }
        actor.printInfo(TextComponent.of("Deleted saved " + kind + " #" + name + "."));
        List<String> users = usersOf(store.getAll(), name);
        if (!users.isEmpty()) {
            actor.printError(TextComponent.of("Note: " + String.join(", ", users) + " used #" + name
                + " and won't work until you save it again."));
        }
    }

    void list(Actor actor) {
        SortedMap<String, String> all = store().getAll();
        if (all.isEmpty()) {
            actor.printInfo(TextComponent.of("No saved " + kind + "s yet. Save one with //" + kind
                + " save <name> <" + kind + ">."));
            return;
        }
        actor.printInfo(TextComponent.of("Saved " + kind + "s (" + all.size()
            + "; click one to use it, shift-click to insert #name):"));
        for (Map.Entry<String, String> entry : all.entrySet()) {
            actor.print(TextComponent.of(" ")
                .append(useLink(entry.getKey(), entry.getValue()))
                .append(TextComponent.of(" = " + shorten(entry.getValue(), 50), TextColor.GRAY))
                .append(TextComponent.of(" [info]", TextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("//" + kind + " info " + entry.getKey()))
                    .hoverEvent(HoverEvent.showText(TextComponent.of("Show details")))));
        }
    }

    void info(Actor actor, LocalSession session, String rawName) {
        String name = SavedInputStore.normalize(rawName);
        SavedInputStore store = store();
        String value = store.get(name).orElse(null);
        if (value == null) {
            actor.printError(TextComponent.of("There is no saved " + kind + " called #" + name
                + ". See //" + kind + " list."));
            return;
        }
        actor.printInfo(TextComponent.of("Saved " + kind + " ").append(useLink(name, value)));
        actor.print(TextComponent.of("  " + value, TextColor.WHITE));
        try {
            parser().parseFromInput("#" + name, createContext(actor, session));
            actor.print(TextComponent.of("  Works right now.", TextColor.GRAY));
        } catch (InputParseException e) {
            actor.printError(TextComponent.of("  Doesn't work right now: ").append(e.getRichMessage()));
        }
        List<String> users = usersOf(store.getAll(), name);
        if (!users.isEmpty()) {
            actor.print(TextComponent.of("  Used by " + String.join(", ", users), TextColor.GRAY));
        }
        String example = useCommand + "#" + name;
        actor.print(TextComponent.of("  Example: ", TextColor.GRAY)
            .append(TextComponent.of(example, TextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.suggestCommand(example))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click to put this in your chat box"))))
            .append(TextComponent.of("  [delete]", TextColor.RED)
                .clickEvent(ClickEvent.suggestCommand("//" + kind + " delete " + name))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click to put the delete command in your chat box")))));
    }

    private Component useLink(String name, String value) {
        return TextComponent.builder("#" + name, TextColor.YELLOW)
            .clickEvent(ClickEvent.suggestCommand(useCommand + "#" + name))
            .hoverEvent(HoverEvent.showText(TextComponent.of(value + "\nClick: " + useCommand + "#" + name
                + "\nShift-click: insert #" + name)))
            .insertion("#" + name)
            .build();
    }

    /**
     * Find saved entries whose text mentions {@code #name}.
     */
    private static List<String> usersOf(Map<String, String> all, String name) {
        List<String> users = new ArrayList<>();
        String needle = "#" + name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String text = entry.getValue().toLowerCase(Locale.ROOT);
            int index = text.indexOf(needle);
            while (index >= 0) {
                int end = index + needle.length();
                boolean startOk = index == 0 || text.charAt(index - 1) != '#';
                boolean endOk = end >= text.length() || !isNameChar(text.charAt(end));
                if (startOk && endOk) {
                    users.add("#" + entry.getKey());
                    break;
                }
                index = text.indexOf(needle, index + 1);
            }
        }
        return users;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
