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
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.argument.HintedArguments;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.CrosshairPlacement;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.image.BlockPalette;
import com.sk89q.worldedit.function.generator.image.ColorMatcher;
import com.sk89q.worldedit.function.generator.image.PixelArt;
import com.sk89q.worldedit.function.generator.parts.BuildBlock;
import com.sk89q.worldedit.function.generator.parts.BuildPlan;
import com.sk89q.worldedit.function.generator.parts.BuildingPart;
import com.sk89q.worldedit.function.generator.parts.BuildingParts;
import com.sk89q.worldedit.function.generator.parts.FacingPlane;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.text.BlockText;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;
import org.enginehub.piston.exception.StopExecutionException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Commands that build ready-made parts (towers, domes, bridges...), block letters and pixel art.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class BuildPartCommands {

    /**
     * The folder (relative to WorldEdit's working directory) that images for {@code //image} live in.
     */
    public static final String IMAGE_DIR = "images";

    /**
     * The largest image that is read, in pixels each way.
     */
    public static final int MAX_IMAGE_SIDE = 4096;

    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp");

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public BuildPartCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    /**
     * The horizontal direction the actor looks in; north for actors that aren't players.
     */
    private static Direction facingOf(Actor actor) {
        if (actor instanceof Player player) {
            return FacingPlane.cardinalFromYaw(player.getLocation().getYaw());
        }
        return Direction.NORTH;
    }

    private static Pattern parsePattern(String input, Actor actor, World world, LocalSession session,
                                        EditSession editSession) throws WorldEditException {
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        context.setSession(session);
        return WorldEdit.getInstance().getPatternFactory().parseFromInput(input, context);
    }

    @Command(
        name = "/build",
        aliases = { "/buildpart" },
        desc = "Builds a ready-made part such as a tower, dome, spiral staircase, wall or bridge",
        descFooter = "Use //builds to see every part and //builds <name> for its settings. "
            + "Settings are plain numbers, in order or as name=value, e.g. //build tower stone_bricks radius=5 height=24. "
            + "-w <pattern> fills windows, panes and railings; -s <stair block> puts stairs on spiral staircase steps."
    )
    @CommandPermissions("worldedit.generation.build")
    @Logging(PLACEMENT)
    public int build(Actor actor, World world, LocalSession session, EditSession editSession,
                     @Arg(desc = "The part (see //builds)")
                         HintedArguments.PartName partArg,
                     @Arg(desc = "The pattern of blocks to build with")
                         Pattern pattern,
                     @Arg(desc = "Settings: numbers in order, or name=value", def = "", variable = true)
                         List<HintedArguments.SettingWord> settingWords,
                     @ArgFlag(name = 'w', desc = "The pattern for windows, panes and railings")
                         String detailInput,
                     @ArgFlag(name = 's', desc = "A stair block for the front edge of spiral staircase steps")
                         String stairInput,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        String partName = partArg.value();
        List<String> settings = HintedArguments.strings(settingWords, HintedArguments.SettingWord::value);
        BuildingPart part = BuildingParts.find(partName).orElse(null);
        if (part == null) {
            actor.printError(TextComponent.of("Unknown part '" + partName + "'. Use //builds to see them all."));
            return 0;
        }
        ShapeParameters parameters;
        String detailText;
        String stairText;
        try {
            // flags typed after the settings end up in the settings list, so pick them out here
            ShapeFlags flags = ShapeFlags.parse(settings, "c", "", "ws");
            atCrosshair |= flags.has('c');
            detailText = flags.text('w', detailInput == null ? "" : detailInput);
            stairText = flags.text('s', stairInput == null ? "" : stairInput);
            parameters = ShapeParameters.parse(part.parameters(), flags.remaining());
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        for (Map.Entry<String, Double> entry : parameters.asMap().entrySet()) {
            String key = entry.getKey();
            if (key.equals("radius") || key.equals("width") || key.equals("height") || key.equals("arch")) {
                worldEdit.checkMaxRadius(key.equals("height") ? entry.getValue() / 2 : entry.getValue());
            }
        }

        if (detailText.isEmpty()) {
            detailText = part.defaultDetail();
        }
        Pattern detail = detailText.isEmpty() ? pattern : parsePattern(detailText, actor, world, session, editSession);
        Pattern stair = stairText.isEmpty() ? null : parsePattern(stairText, actor, world, session, editSession);

        BlockVector3 origin;
        BlockVector3 end = BlockVector3.ZERO;
        if (part.usesSelection()) {
            Region region = session.getSelection(world);
            if (!(region instanceof CuboidRegion cuboid)) {
                actor.printError(TextComponent.of("A " + part.name() + " runs from //pos1 to //pos2: "
                    + "use a cuboid selection (//sel cuboid)."));
                return 0;
            }
            origin = cuboid.getPos1();
            end = cuboid.getPos2().subtract(origin);
            worldEdit.checkMaxRadius(Math.hypot(end.x(), end.z()) / 2);
        } else {
            origin = CrosshairPlacement.placement(session, actor, atCrosshair);
        }

        BuildPlan plan;
        try {
            plan = part.plan(parameters, new BuildingPart.Context(end, facingOf(actor)));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }

        int changed = 0;
        for (Map.Entry<BlockVector3, BuildBlock> entry : plan.blocks().entrySet()) {
            BlockVector3 position = origin.add(entry.getKey());
            BuildBlock block = entry.getValue();
            boolean set = switch (block.role()) {
                case MAIN -> editSession.setBlock(position, pattern);
                case DETAIL -> editSession.setBlock(position, detail);
                case STAIR -> stair == null
                    ? editSession.setBlock(position, pattern)
                    : editSession.setBlock(position, withProperty(withProperty(stair.applyBlock(position),
                        "facing", block.facing().name().toLowerCase(Locale.ROOT)),
                        "half", block.upsideDown() ? "top" : "bottom"));
            };
            if (set) {
                changed++;
            }
        }
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Built a " + part.name() + ": " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "/builds",
        aliases = { "/buildparts" },
        desc = "Lists the parts for //build, or shows the settings of one"
    )
    @CommandPermissions("worldedit.generation.build")
    public void builds(Actor actor,
                       @Arg(desc = "A part to show the settings of", def = "")
                           HintedArguments.PartName partArg) {
        String name = partArg == null ? null : partArg.value();
        if (name != null && !name.isEmpty()) {
            BuildingParts.find(name).ifPresentOrElse(
                part -> printInfo(actor, part),
                () -> actor.printError(TextComponent.of("Unknown part '" + name + "'. Use //builds to see them all.")));
            return;
        }
        actor.printInfo(TextComponent.of("Parts for //build <name> <pattern> [settings] (click one for details):"));
        TextComponent.Builder line = TextComponent.builder("", TextColor.GRAY);
        boolean first = true;
        for (BuildingPart part : BuildingParts.all()) {
            if (!first) {
                line.append(TextComponent.of(", ", TextColor.GRAY));
            }
            first = false;
            line.append(TextComponent.of(part.name(), TextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("//builds " + part.name()))
                .hoverEvent(HoverEvent.showText(TextComponent.of(part.description()))));
        }
        actor.print(line.build());
    }

    private static void printInfo(Actor actor, BuildingPart part) {
        actor.printInfo(TextComponent.of(part.name(), TextColor.YELLOW)
            .append(TextComponent.of(" - " + part.description(), TextColor.WHITE)));
        if (!part.aliases().isEmpty()) {
            actor.print(TextComponent.of("Also called: " + String.join(", ", part.aliases()), TextColor.GRAY));
        }
        actor.print(TextComponent.of("Settings (in this order, or as name=value):", TextColor.GRAY));
        for (ShapeParameter parameter : part.parameters()) {
            actor.print(TextComponent.of("  " + parameter.name(), TextColor.AQUA)
                .append(TextComponent.of(" = " + ShapeParameter.format(parameter.defaultValue()), TextColor.WHITE))
                .append(TextComponent.of("  " + parameter.description() + " ("
                    + ShapeParameter.format(parameter.min()) + " to "
                    + ShapeParameter.format(parameter.max()) + ")", TextColor.GRAY)));
        }
        String example = "//build " + part.name() + " " + exampleBlock(part) + " " + part.exampleSettings();
        if (part.name().equals("spiralstair")) {
            example += " -s oak_stairs";
        }
        actor.print(TextComponent.of("Example: ", TextColor.GRAY)
            .append(TextComponent.of(example, TextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.suggestCommand(example))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click to put this in your chat box")))));
    }

    private static String exampleBlock(BuildingPart part) {
        return switch (part.name()) {
            case "spiralstair", "bridge" -> "oak_planks";
            case "dome" -> "white_concrete";
            case "column" -> "quartz_block";
            case "roundwindow" -> "stone_bricks -w light_blue_stained_glass";
            default -> "stone_bricks";
        };
    }

    /**
     * Sets a property on a block if the block has it and the value is valid.
     */
    @SuppressWarnings("unchecked")
    private static BaseBlock withProperty(BaseBlock block, String name, String value) {
        Property<Object> property = (Property<Object>) block.getBlockType().getPropertyMap().get(name);
        if (property == null) {
            return block;
        }
        Object parsed;
        try {
            parsed = property.getValueFor(value);
        } catch (IllegalArgumentException _) {
            return block;
        }
        if (parsed == null || !property.values().contains(parsed)) {
            return block;
        }
        return block.with(property, parsed);
    }

    @Command(
        name = "/text",
        aliases = { "/blocktext" },
        desc = "Builds block letters, standing up and facing you, starting at your placement position",
        descFooter = "Reads left to right as you look at it. Letters are 5x7 blocks (times the -s scale). "
            + "Lower case is built as capitals. Example: //text red_wool Hello world -s 2"
    )
    @CommandPermissions("worldedit.generation.text")
    @Logging(PLACEMENT)
    public int text(Actor actor, LocalSession session, EditSession editSession,
                    @Arg(desc = "The pattern of blocks to build with")
                        Pattern pattern,
                    @Arg(desc = "The text", def = "", variable = true)
                        List<String> words,
                    @ArgFlag(name = 's', desc = "Blocks per letter pixel", def = "1")
                        int scale,
                    @ArgFlag(name = 'd', desc = "How thick the letters are", def = "1")
                        int depth,
                    @Switch(name = 'f', desc = "Lay the text flat on the ground")
                        boolean flat,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        BlockText text;
        try {
            // flags typed after the text end up in the text, so pick them out here
            ShapeFlags flags = ShapeFlags.parse(words, "fc", "sd");
            flat |= flags.has('f');
            atCrosshair |= flags.has('c');
            scale = (int) flags.value('s', scale);
            depth = (int) flags.value('d', depth);
            checkCommandArgument(scale >= 1 && scale <= 16, "Scale must be between 1 and 16");
            checkCommandArgument(depth >= 1 && depth <= 32, "Depth must be between 1 and 32");
            text = BlockText.layout(String.join(" ", flags.remaining()));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        worldEdit.checkMaxRadius(text.width() * scale / 2.0);

        BlockVector3 origin = CrosshairPlacement.placement(session, actor, atCrosshair);
        FacingPlane plane = new FacingPlane(facingOf(actor), flat);
        int changed = 0;
        for (BlockVector3 offset : text.blocks(plane, scale, depth)) {
            if (editSession.setBlock(origin.add(offset), pattern)) {
                changed++;
            }
        }
        if (!text.unsupported().isEmpty()) {
            actor.printError(TextComponent.of("No letter shape for "
                + text.unsupported().stream().map(c -> "'" + c + "'").collect(Collectors.joining(" "))
                + "; built '?' instead."));
        }
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Built text " + text.width() * scale + " blocks wide; "
            + changed + " blocks changed."));
        return changed;
    }

    private File getImageDir() {
        return worldEdit.getWorkingDirectoryPath(IMAGE_DIR).toFile();
    }

    @Command(
        name = "/image",
        aliases = { "/pixelart" },
        desc = "Builds pixel art from a PNG or JPG in the images folder, facing you",
        descFooter = "Put images in the 'images' folder in WorldEdit's folder; //image list shows them. "
            + "Each pixel becomes the closest-coloured block; transparent pixels are skipped. "
            + "Palettes for -p: all, wool, concrete, terracotta, planks, stone, other (or several, e.g. wool,concrete)."
    )
    @CommandPermissions("worldedit.generation.image")
    @Logging(PLACEMENT)
    public int image(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The image file name in the images folder, or 'list'")
                         String filename,
                     @Arg(desc = "The width in blocks (default: the image's width, at most 128)", def = "")
                         Integer width,
                     @Switch(name = 'f', desc = "Lay the picture flat on the ground")
                         boolean flat,
                     @ArgFlag(name = 'p', desc = "The palette of blocks to use", def = "all")
                         String palette,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        File dir = getImageDir();
        if (filename.equalsIgnoreCase("list") && width == null && !new File(dir, "list.png").exists()) {
            listImages(actor, dir);
            return 0;
        }
        File file = worldEdit.getSafeOpenFile(actor, dir, filename, "png",
            IMAGE_EXTENSIONS.toArray(new String[0]));
        if (!file.isFile()) {
            actor.printError(TextComponent.of("There is no image '" + filename + "' in the " + IMAGE_DIR
                + " folder. Use //image list to see the images."));
            return 0;
        }
        BufferedImage image = readImage(file);

        int blocksWide = width == null ? Math.min(128, image.getWidth()) : width;
        checkCommandArgument(blocksWide >= 1 && blocksWide <= PixelArt.MAX_SIDE,
            "Width must be between 1 and " + PixelArt.MAX_SIDE);
        worldEdit.checkMaxRadius(blocksWide / 2.0);

        PixelArt art;
        try {
            List<BlockPalette.Entry> entries = BlockPalette.forName(palette, id -> BlockTypes.get(id) != null);
            art = PixelArt.create(image, blocksWide, new ColorMatcher(entries));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        worldEdit.checkMaxRadius(art.height() / 2.0);

        BlockVector3 origin = CrosshairPlacement.placement(session, actor, atCrosshair);
        FacingPlane plane = new FacingPlane(facingOf(actor), flat);
        Map<String, BlockType> types = new HashMap<>();
        int changed = 0;
        for (Map.Entry<BlockVector3, String> entry : art.blocks(plane).entrySet()) {
            BlockType type = types.computeIfAbsent(entry.getValue(), BlockTypes::get);
            if (type != null && editSession.setBlock(origin.add(entry.getKey()), type.getDefaultState())) {
                changed++;
            }
        }
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Built " + file.getName() + " as " + art.width() + "x" + art.height()
            + " blocks; " + changed + " blocks changed."));
        return changed;
    }

    private static void listImages(Actor actor, File dir) {
        File[] files = dir.listFiles((_, name) -> {
            int dot = name.lastIndexOf('.');
            return dot > 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        });
        if (files == null || files.length == 0) {
            actor.printInfo(TextComponent.of("No images yet. Put PNG or JPG files in " + dir.getAbsolutePath()));
            return;
        }
        Arrays.sort(files);
        actor.printInfo(TextComponent.of("Images (click one to build it):"));
        for (File file : files) {
            String name = file.getName();
            actor.print(TextComponent.of("  " + name, TextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand("//image " + name))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click to put //image " + name + " in your chat box"))));
        }
    }

    private static BufferedImage readImage(File file) throws WorldEditException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new StopExecutionException(TextComponent.of("Could not open " + file.getName() + "."));
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new StopExecutionException(TextComponent.of(file.getName() + " is not a supported image."));
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkCommandArgument(width > 0 && height > 0 && width <= MAX_IMAGE_SIDE && height <= MAX_IMAGE_SIDE,
                    "The image must be between 1x1 and " + MAX_IMAGE_SIDE + "x" + MAX_IMAGE_SIDE + " pixels");
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new StopExecutionException(TextComponent.of("Could not read the image: " + e.getMessage()));
        }
    }
}
