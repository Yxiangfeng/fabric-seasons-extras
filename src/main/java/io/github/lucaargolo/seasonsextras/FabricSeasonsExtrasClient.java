package io.github.lucaargolo.seasonsextras;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.lucaargolo.seasons.utils.Season;
import io.github.lucaargolo.seasonsextras.mixin.GuiBookEntryAccessor;
import io.github.lucaargolo.seasonsextras.patchouli.PageBiomeSearch;
import io.github.lucaargolo.seasonsextras.patchouli.PageSeasonalBiome;
import io.github.lucaargolo.seasonsextras.utils.ModIdentifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import vazkii.patchouli.client.book.ClientBookRegistry;

import java.util.*;

public class FabricSeasonsExtrasClient implements ClientModInitializer {

    public static RegistryKey<Biome> multiblockBiomeOverride = null;
    public static Season multiblockSeasonOverride = null;

    public static final HashMap<RegistryKey<World>, Set<RegistryEntry<Biome>>> worldValidBiomes = new HashMap<>();
    public static final HashMap<RegistryKey<World>, HashMap<Identifier, Set<Identifier>>> worldBiomeMultiblocks = new HashMap<>();
    public static HashMap<Identifier, JsonObject> multiblocks = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientBookRegistry.INSTANCE.pageTypes.put(new ModIdentifier("biome_search"), PageBiomeSearch.class);
        ClientBookRegistry.INSTANCE.pageTypes.put(new ModIdentifier("seasonal_biome"), PageSeasonalBiome.class);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(client.currentScreen == null) {
                FabricSeasonsExtrasClient.multiblockBiomeOverride = null;
                FabricSeasonsExtrasClient.multiblockSeasonOverride = null;
            }else{
                if(client.currentScreen instanceof GuiBookEntryAccessor bookEntry) {
                    //TODO: Make TickablePage
                    if(bookEntry.getLeftPage() instanceof PageBiomeSearch page) {
                        page.tick();
                    }
                    if(bookEntry.getRightPage() instanceof PageBiomeSearch page) {
                        page.tick();
                    }
                    if(bookEntry.getLeftPage() instanceof PageSeasonalBiome page) {
                        page.tick();
                    }
                    if(bookEntry.getRightPage() instanceof PageSeasonalBiome page) {
                        page.tick();
                    }
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricSeasonsExtras.SEND_VALID_BIOMES_S2C, (client, handler, buf, responseSender) -> {
            HashSet<Identifier> validBiomes = new HashSet<>();
            Identifier worldId = buf.readIdentifier();
            RegistryKey<World> worldKey = RegistryKey.of(Registry.WORLD_KEY, worldId);
            int size = buf.readInt();
            for(int i = 0; i < size; i++) {
               validBiomes.add(buf.readIdentifier());
            }
            client.execute(() -> {
                worldValidBiomes.computeIfPresent(worldKey, (key, list) -> new HashSet<>());
                validBiomes.stream().sorted(Comparator.comparing(Identifier::getPath)).forEach(biome -> {
                    handler.getRegistryManager().get(Registry.BIOME_KEY).getEntry(RegistryKey.of(Registry.BIOME_KEY, biome)).ifPresent(entry -> {
                        worldValidBiomes.computeIfAbsent(worldKey, (key) -> new HashSet<>()).add(entry);
                    });
                });
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricSeasonsExtras.SEND_BIOME_MULTIBLOCKS_S2C, (client, handler, buf, responseSender) -> {
            HashMap<Identifier, Set<Identifier>> biomeMultiblocks = new HashMap<>();
            Identifier worldId = buf.readIdentifier();
            RegistryKey<World> worldKey = RegistryKey.of(Registry.WORLD_KEY, worldId);
            int mapSize = buf.readInt();
            for(int m = 0; m < mapSize; m++) {
                HashSet<Identifier> set = new HashSet<>();
                Identifier biomeId = buf.readIdentifier();
                int setSize = buf.readInt();
                for(int s = 0; s < setSize; s++) {
                    set.add(buf.readIdentifier());
                }
                biomeMultiblocks.put(biomeId, set);
            }
            client.execute(() -> {
                worldBiomeMultiblocks.put(worldKey, biomeMultiblocks);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(FabricSeasonsExtras.SEND_MULTIBLOCKS_S2C, (client, handler, buf, responseSender) -> {
            HashMap<Identifier, JsonObject> serverMultiblocks = new HashMap<>();
            int size = buf.readInt();
            for(int m = 0; m < size; m++) {
                serverMultiblocks.put(buf.readIdentifier(), JsonParser.parseString(buf.readString()).getAsJsonObject());
            }
            client.execute(() -> {
                multiblocks = serverMultiblocks;
            });
        });
        BlockRenderLayerMap.INSTANCE.putBlock(Registry.BLOCK.get(new ModIdentifier("greenhouse_glass")), RenderLayer.getTranslucent());
    }

}
