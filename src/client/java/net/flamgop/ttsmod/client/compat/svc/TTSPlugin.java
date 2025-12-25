package net.flamgop.ttsmod.client.compat.svc;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import net.flamgop.ttsmod.client.TTSMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TTSPlugin implements VoicechatPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(TTSPlugin.class);

    private VoicechatClientApi clientApi;
    private ClientStaticAudioChannel ttsEchoChannel;

    @Override
    public String getPluginId() {
        return "text_to_voice";
    }

    @Override
    public void initialize(VoicechatApi api) {
        LOGGER.info("Loaded SimpleVoiceChat integration!");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, packet -> {
            clientApi = packet.getVoicechat();
            ttsEchoChannel = clientApi.createStaticAudioChannel(UUID.randomUUID());
        });
        registration.registerEvent(MergeClientSoundEvent.class, packet -> {
            if (TTSMod.INSTANCE.audioManager().frameAvailable()) {
                short[] frame = TTSMod.INSTANCE.audioManager().getFrame();
                packet.mergeAudio(frame);
                if (TTSMod.INSTANCE.config().hearSelf) {
                    if (ttsEchoChannel != null) ttsEchoChannel.play(frame);
                }
            }
        });
    }
}
