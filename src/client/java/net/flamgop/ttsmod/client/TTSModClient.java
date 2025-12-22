package net.flamgop.ttsmod.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.flamgop.espeak4j.ESpeak;
import net.flamgop.espeak4j.ESpeakPositionType;
import net.flamgop.espeak4j.ESpeakVoice;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TTSModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((commandDispatcher, commandBuildContext) -> {
            commandDispatcher.register(ClientCommandManager.literal("synth")
                    .then(ClientCommandManager.literal("say").then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(context -> {
                                String str = context.getArgument("text", String.class);
                                if (TTSPlugin.INSTANCE != null) {
                                    AudioManager audioManager = TTSPlugin.INSTANCE.audioManager();
                                    audioManager.cancel();
                                    audioManager.dispatch(() -> {
                                        audioManager.getESpeak().synth(str, 0, ESpeakPositionType.CHARACTER, 0, ESpeak.SSML, null, null);
                                    });
                                    context.getSource().sendFeedback(Component.literal("Saying \"" + str + "\""));
                                    return Command.SINGLE_SUCCESS;
                                }
                                context.getSource().sendError(Component.literal("TTSPlugin had no instance, sorry. (BUG)"));
                                return 0;
                            }))
                    )
                    .then(ClientCommandManager.literal("voice").then(ClientCommandManager.argument("voice", StringArgumentType.string())
                            .executes(context -> {
                                String str = context.getArgument("voice", String.class);
                                if (TTSPlugin.INSTANCE != null) {
                                    TTSPlugin.INSTANCE.audioManager().getESpeak().setVoiceByName(str);
                                    return Command.SINGLE_SUCCESS;
                                }
                                context.getSource().sendError(Component.literal("TTSPlugin had no instance, sorry. (BUG)"));
                                return 0;
                            }))
                    )
                    .then(ClientCommandManager.literal("voices").executes(context -> {
                        if (TTSPlugin.INSTANCE != null) {
                            ESpeakVoice[] voices = TTSPlugin.INSTANCE.audioManager().getESpeak().listVoices(null);
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
