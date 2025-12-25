package net.flamgop.ttsmod.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sun.jna.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.flamgop.espeak4j.ESpeak;
import net.flamgop.espeak4j.ESpeakPositionType;
import net.flamgop.espeak4j.ESpeakVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class TTSMod implements ClientModInitializer {

    public static final String MOD_ID = "ttsmod";

    public static TTSMod INSTANCE;
    private AudioManager audioManager;
    private Config config;

    public AudioManager audioManager() {
        return audioManager;
    }

    public Config config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        Config.HANDLER.load();
        this.config = Config.HANDLER.instance();

        String arch = System.getProperty("os.arch");
        if (!arch.equals("amd64") && !arch.equals("aarch64")) throw new RuntimeException("Unknown Arch: " + arch + ", we only support aarch64 and amd64!");

        String osName = System.getProperty("os.name");
        String os;

        if (Platform.isWindows()) os = "windows";
        else if (Platform.isMac()) os = "mac";
        else if (Platform.isLinux()) os = "linux";
        else throw new RuntimeException("Unknown os: " + osName + ", We only support Linux, Mac, and Windows!");

        audioManager = new AudioManager(
                NativeHelper.extractNative("native" + File.separator + os + File.separator + arch + File.separator + "libespeak-ng.dll").toAbsolutePath().toString(),
                NativeHelper.extractData("native" + File.separator + "espeak-ng-data").toAbsolutePath().toString()
        );

        ClientCommandRegistrationCallback.EVENT.register((commandDispatcher, commandBuildContext) -> {
            commandDispatcher.register(ClientCommandManager.literal("synth")
                    .then(ClientCommandManager.literal("say").then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(context -> {
                                String str = context.getArgument("text", String.class);
                                if (this.audioManager != null) {
                                    audioManager.cancel();
                                    audioManager.dispatch(() -> {
                                        audioManager.getESpeak().synth(str, 0, ESpeakPositionType.CHARACTER, 0, ESpeak.SSML, null, null);
                                    });
                                    context.getSource().sendFeedback(Component.literal("Saying \"" + str + "\""));
                                    if (config.synthToChat) {
                                        Minecraft client = context.getSource().getClient();
                                        if (client != null && client.player != null)
                                            client.player.connection.sendChat("(Text To Speech) " + str);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }
                                context.getSource().sendError(Component.literal("TTSPlugin had no instance, sorry. (BUG)"));
                                return 0;
                            }))
                    )
                    .then(ClientCommandManager.literal("voice").then(ClientCommandManager.argument("voice", StringArgumentType.string())
                            .executes(context -> {
                                String str = context.getArgument("voice", String.class);
                                if (this.audioManager != null) {
                                    int ret = audioManager.getESpeak().setVoiceByName(str);;
                                    if (ret == ESpeak.EE_OK) {
                                        context.getSource().sendFeedback(Component.literal("Set voice to \""+ str +"\""));
                                        return Command.SINGLE_SUCCESS;
                                    } else {
                                        context.getSource().sendError(Component.literal("Couldn't set voice to \""+ str +"\""));
                                        return 0;
                                    }
                                }
                                context.getSource().sendError(Component.literal("TTSPlugin had no instance, sorry. (BUG)"));
                                return 0;
                            }))
                    )
                    .then(ClientCommandManager.literal("voices").executes(context -> {
                        if (this.audioManager != null) {
                            ESpeakVoice[] voices = audioManager.getESpeak().listVoices(null);
                            context.getSource().sendFeedback(Component.literal("Valid voice names: " + Arrays.stream(voices).map(ESpeakVoice::name).collect(Collectors.joining(", "))));
                            return Command.SINGLE_SUCCESS;
                        }
                        context.getSource().sendError(Component.literal("TTSPlugin had no instance, sorry. (BUG)"));
                        return 0;
                    }))
            );
        });
    }
}
