package com.touhoumaid.construction.duoduo;

import com.touhoumaid.construction.duoduo.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ConstructionDuoduoSupplyMod.MOD_ID)
public class ConstructionDuoduoSupplyMod {
    public static final String MOD_ID = "construction_duoduo_supply";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ConstructionDuoduoSupplyMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("{} common setup", MOD_ID);
    }
}
