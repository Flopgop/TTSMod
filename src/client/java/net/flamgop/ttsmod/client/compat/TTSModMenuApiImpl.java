package net.flamgop.ttsmod.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.flamgop.ttsmod.client.Config;

public class TTSModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parentScreen -> Config.HANDLER.generateGui().generateScreen(parentScreen);
    }
}
