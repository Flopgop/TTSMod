package net.flamgop.ttsmod.client;


import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.CustomDescription;
import dev.isxander.yacl3.config.v2.api.autogen.StringField;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

public class Config {
    public static ConfigClassHandler<Config> HANDLER = ConfigClassHandler.createBuilder(Config.class)
            .id(ResourceLocation.fromNamespaceAndPath(TTSMod.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve(TTSMod.MOD_ID + ".json5"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .setJson5(true)
                    .build())
            .build();

    @AutoGen(category = "tts")
    @Boolean(colored = true)
    @SerialEntry
    public boolean hearSelf = true;

    @AutoGen(category = "tts")
    @CustomDescription(value = "yacl3.config.ttsmod:config.synthToChat.description")
    @Boolean(colored = true)
    @SerialEntry
    public boolean synthToChat = true;

    @AutoGen(category = "tts")
    @CustomDescription(value = "yac3l.config.ttsmod:config.voice.description")
    @StringField
    @SerialEntry
    public String voice = "English (America)";
}
