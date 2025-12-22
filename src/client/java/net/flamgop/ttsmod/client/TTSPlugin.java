package net.flamgop.ttsmod.client;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TTSPlugin implements VoicechatPlugin {

    public static @Nullable TTSPlugin INSTANCE;

    private AudioManager manager;
    private VoicechatClientApi clientApi;
    private ClientStaticAudioChannel ttsEchoChannel;

    public AudioManager audioManager() {
        return manager;
    }

    @Override
    public String getPluginId() {
        return "text_to_voice";
    }

    @Override
    public void initialize(VoicechatApi api) {
        INSTANCE = this;
        manager = new AudioManager(
                NativeHelper.extractNative("native/libespeak-ng.dll").toAbsolutePath().toString(),
                NativeHelper.extractData("native/espeak-ng-data").toAbsolutePath().toString()
        );
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, packet -> {
            clientApi = packet.getVoicechat();
            ttsEchoChannel = clientApi.createStaticAudioChannel(UUID.randomUUID());
        });
        registration.registerEvent(MergeClientSoundEvent.class, packet -> {
            if (manager.frameAvailable()) {
                short[] frame = manager.getFrame();
                packet.mergeAudio(frame);
                if (ttsEchoChannel != null) ttsEchoChannel.play(frame);
            }
        });
    }
}
