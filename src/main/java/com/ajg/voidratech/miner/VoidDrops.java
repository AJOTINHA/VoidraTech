package com.ajg.voidratech.miner;

import com.ajg.voidratech.VoidraTech;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What Void Drills pull out of the void, from datapack files in {@code data/<namespace>/voidratech/void_drops/}:
 * <pre>{"item": "minecraft:redstone_ore", "weight": 50, "lens": "red"}</pre>
 * or, for ores from other mods, a tag (the first item in it is used; an empty tag is skipped):
 * <pre>{"tag": "c:ores/tin", "weight": 40, "min_tier": 1, "lens": "gray"}</pre>
 * <ul>
 *   <li>{@code lens}: the dye colour of the lens that focuses this ore, usually the ore's own colour; with that lens
 *       above the lens mount its weight is multiplied by {@link #FOCUS_MULTIPLIER}, more with focus modifiers in the
 *       drill's floor (see {@link #focusMultiplier});</li>
 *   <li>{@code dimensions}: where it can be drilled (any dimension when left out). A dimension that no drop names,
 *       like a modded one, drills like the Overworld.</li>
 * </ul>
 * The weighted table of each tier, lens, focus and dimension is built once and reused until the next reload or tag update.
 * Clients get a copy of the drops when they join and after every reload, for JEI.
 */
public final class VoidDrops {
    public static final int FOCUS_MULTIPLIER = 8;
    /**
     * Extra lens boost (what the lens adds on top of the plain weight) of each focus modifier, in step with the speed
     * and energy modifiers.
     */
    public static final int FOCUS_PERCENT_PER_MODIFIER = 10;

    public record Entry(Optional<Identifier> item, Optional<TagKey<Item>> tag, int weight, int minTier, Optional<DyeColor> lens,
                        List<Identifier> dimensions) {
        private static final Codec<Entry> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("item").forGetter(Entry::item),
                TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(Entry::tag),
                Codec.intRange(1, 1_000_000).fieldOf("weight").forGetter(Entry::weight),
                Codec.intRange(1, DrillTier.values().length).optionalFieldOf("min_tier", 1).forGetter(Entry::minTier),
                DyeColor.CODEC.optionalFieldOf("lens").forGetter(Entry::lens),
                Identifier.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(Entry::dimensions)
        ).apply(instance, Entry::new));
        public static final Codec<Entry> CODEC = RAW_CODEC.validate(entry -> entry.item.isPresent() != entry.tag.isPresent()
                ? DataResult.success(entry)
                : DataResult.error(() -> "a void drop needs exactly one of \"item\" or \"tag\""));

        private boolean drillableIn(Identifier dimension) {
            return dimensions.isEmpty() || dimensions.contains(dimension);
        }

        private int weightWith(@Nullable DyeColor installed, int focusModifiers) {
            return installed != null && lens.isPresent() && lens.get() == installed
                    ? (int) Math.round(weight * focusMultiplier(focusModifiers)) : weight;
        }

        private @Nullable Item resolve() {
            if (item.isPresent()) {
                return BuiltInRegistries.ITEM.getOptional(item.get()).orElse(null);
            }
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag.orElseThrow())) {
                return holder.value();
            }
            return null;
        }
    }

    /**
     * How likely one drop of a drill is to be {@code item}, in {@code dimension}: with no lens, and with the lens that
     * focuses it ({@code lens}, null when no lens does).
     */
    public record Chance(Item item, @Nullable DyeColor lens, Identifier dimension, double plain, double focused) {
    }

    /** Items and the running total of their weights, for a binary search on a roll. */
    private record Table(Item[] items, int[] cumulative) {
        int total() {
            return cumulative.length == 0 ? 0 : cumulative[cumulative.length - 1];
        }

        double chance(Item item) {
            int index = Arrays.asList(items).indexOf(item);
            return index < 0 ? 0 : (double) (cumulative[index] - (index == 0 ? 0 : cumulative[index - 1])) / total();
        }

        @Nullable Item roll(RandomSource random) {
            if (items.length == 0) {
                return null;
            }
            int index = Arrays.binarySearch(cumulative, random.nextInt(total()) + 1);
            return items[index >= 0 ? index : -index - 1];
        }
    }

    private record Key(DrillTier tier, @Nullable DyeColor lens, int focusModifiers, Identifier dimension) {
    }

    /** A set of drops with its tables: the server's, from datapacks, or a client's copy of it. */
    public static final class Drops {
        private final List<Entry> entries;
        private final Map<Key, Table> tables = new HashMap<>();

        private Drops(List<Entry> entries) {
            this.entries = List.copyOf(entries);
        }

        private synchronized Table table(DrillTier tier, @Nullable DyeColor lens, int focusModifiers, Identifier dimension) {
            // Focus modifiers do nothing without a coloured lens: share the plain table.
            int focus = lens == null ? 0 : focusModifiers;
            // Drops open to every dimension (like the crystals) don't count as a dimension's own.
            return tables.computeIfAbsent(new Key(tier, lens, focus, dimension), key -> buildTable(tier, lens, focus,
                    entries.stream().anyMatch(entry -> entry.dimensions().contains(dimension)) ? dimension : OVERWORLD));
        }

        private synchronized void clearTables() {
            tables.clear();
        }

        /**
         * Every drop of a drill of this tier, in each dimension some drop names (the Overworld first), most likely
         * first.
         */
        public List<Chance> chances(DrillTier tier) {
            Set<Identifier> dimensions = new LinkedHashSet<>();
            dimensions.add(OVERWORLD);
            entries.forEach(entry -> dimensions.addAll(entry.dimensions()));
            List<Chance> chances = new ArrayList<>();
            for (Identifier dimension : dimensions) {
                Table plain = table(tier, null, 0, dimension);
                List<Chance> here = new ArrayList<>();
                for (Item item : plain.items()) {
                    DyeColor lens = lensOf(item);
                    double focused = lens == null ? plain.chance(item) : table(tier, lens, 0, dimension).chance(item);
                    here.add(new Chance(item, lens, dimension, plain.chance(item), focused));
                }
                here.sort(Comparator.comparingDouble(Chance::plain).reversed());
                chances.addAll(here);
            }
            return chances;
        }

        private @Nullable DyeColor lensOf(Item item) {
            for (Entry entry : entries) {
                if (entry.lens().isPresent() && entry.resolve() == item) {
                    return entry.lens().get();
                }
            }
            return null;
        }

        private Table buildTable(DrillTier tier, @Nullable DyeColor lens, int focusModifiers, Identifier dimension) {
            List<Item> items = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.minTier() > tier.level() || !entry.drillableIn(dimension)) {
                    continue;
                }
                Item item = entry.resolve();
                if (item == null) {
                    continue;
                }
                int weight = entry.weightWith(lens, focusModifiers);
                int index = items.indexOf(item);
                if (index >= 0) {
                    weights.set(index, weights.get(index) + weight);
                } else {
                    items.add(item);
                    weights.add(weight);
                }
            }
            int[] cumulative = new int[weights.size()];
            int total = 0;
            for (int i = 0; i < cumulative.length; i++) {
                total += weights.get(i);
                cumulative[i] = total;
            }
            return new Table(items.toArray(Item[]::new), cumulative);
        }
    }

    /** Sends the server's drops to a client. */
    private record SyncPayload(List<Entry> entries) implements CustomPacketPayload {
        static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, "void_drops"));
        static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC =
                ByteBufCodecs.fromCodec(Entry.CODEC.listOf()).map(SyncPayload::new, SyncPayload::entries).cast();

        @Override
        public Type<SyncPayload> type() {
            return TYPE;
        }
    }

    private static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(VoidraTech.MOD_ID, "void_drops");
    private static final Identifier OVERWORLD = Level.OVERWORLD.identifier();
    private static Drops server = new Drops(List.of());
    private static Drops client = new Drops(List.of());

    private VoidDrops() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> event.addListener(LISTENER_ID, new Loader()));
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            server.clearTables();
            client.clearTables();
        });
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.getRelevantPlayers()
                .forEach(player -> PacketDistributor.sendToPlayer(player, new SyncPayload(server.entries))));
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) -> client = new Drops(payload.entries())));
    }

    /**
     * How much a lens multiplies the weight of its own drops, with this many focus modifiers: x8 with none, x9.4 with a
     * tier-1 floor full of them, x22 with a tier-6 one (the lens's boost 3x as big, like the speed of a floor full of
     * speed modifiers).
     */
    public static double focusMultiplier(int focusModifiers) {
        return 1 + (FOCUS_MULTIPLIER - 1) * (100 + FOCUS_PERCENT_PER_MODIFIER * focusModifiers) / 100.0;
    }

    /** Rolls one drop for a drill of this tier, with this lens (or none), in this dimension; empty when nothing applies. */
    public static ItemStack roll(DrillTier tier, @Nullable DyeColor lens, Identifier dimension, RandomSource random) {
        return roll(tier, lens, 0, dimension, random);
    }

    /** Rolls one drop, with focus modifiers sharpening the lens. */
    public static ItemStack roll(DrillTier tier, @Nullable DyeColor lens, int focusModifiers, Identifier dimension, RandomSource random) {
        Item item = server.table(tier, lens, focusModifiers, dimension).roll(random);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Whether this tier has anything to drill in this dimension at all. */
    public static boolean hasDrops(DrillTier tier, Identifier dimension) {
        return server.table(tier, null, 0, dimension).items.length > 0;
    }

    /** The drops as the server last sent them to this client (empty outside a world). */
    public static Drops client() {
        return client;
    }

    /** The server's drops, from datapacks. */
    public static Drops server() {
        return server;
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<Entry> {
        Loader() {
            super(Entry.CODEC, FileToIdConverter.json(VoidraTech.MOD_ID + "/void_drops"));
        }

        @Override
        protected void apply(Map<Identifier, Entry> loaded, ResourceManager manager, ProfilerFiller profiler) {
            server = new Drops(List.copyOf(loaded.values()));
            VoidraTech.LOGGER.info("Loaded {} void drops", loaded.size());
        }
    }
}
