package net.flamgop.ttsmod.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.flamgop.espeak4j.ESpeak;
import net.flamgop.espeak4j.ESpeakAudioOutput;
import net.flamgop.espeak4j.ESpeakEvent;
import net.flamgop.espeak4j.ESpeakEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AudioManager.class);

    private static final int IN_RATE = 22050; // hz
    private static final int OUT_RATE = 48000; // hz
    private static final int OUT_FRAME_SAMPLES = 960; // 20ms at 48khz
    private static final int RING_CAPACITY = 65535;

    private final ESpeak eSpeak;
    private final MemorySegment audioBuffer;
    private SoundBufferLibrary soundBufferLibrary = null;

    private volatile boolean cancelled = false;
    private ExecutorService synthExecutor = createExecutor();

    private int writePos = 0;
    private int readPos = 0;
    private int available = 0;

    private double resampleOffset = 0.0;
    private short lastSample = 0;

    public AudioManager(String nativePath, String dataPath) {
        eSpeak = new ESpeak(nativePath);
        this.audioBuffer = Arena.ofAuto().allocate(ValueLayout.JAVA_SHORT, RING_CAPACITY);

        eSpeak.initialize(ESpeakAudioOutput.SYNCHRONOUS, 0, dataPath, 0);
        eSpeak.setSynthCallback(this::synthCallback);
        eSpeak.setUriCallback((type, uri, base) -> {
            ResourceLocation location = ResourceLocation.tryParse(uri);
            if (location != null) {
                return 0;
            } else {
                return 1;
            }
        });
    }

    private int synthCallback(MemorySegment wav, int numSamples, ESpeakEvent[] events) {
        if (cancelled) return 1;

        if (events != null) {
            for (ESpeakEvent event : events) {
                ESpeakEventType type = ESpeakEventType.valueOf(event.type());
                if (type == ESpeakEventType.PLAY) {
                    String uri = event.idName();
                    injectMinecraftSound(uri);
                }
            }
        }

        if (numSamples > 0 && wav != null) {
            MemorySegment audio = wav.reinterpret(numSamples * ValueLayout.JAVA_SHORT.byteSize());

            synchronized (audioBuffer) {
                for (int i = 0; i < numSamples; i++) {
                    while (available >= RING_CAPACITY) {
                        try {
                            audioBuffer.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return 1;
                        }
                    }

                    short s = audio.getAtIndex(ValueLayout.JAVA_SHORT, i);
                    audioBuffer.setAtIndex(ValueLayout.JAVA_SHORT, writePos, s);
                    writePos = (writePos + 1) % RING_CAPACITY;
                    available++;
                }
                audioBuffer.notifyAll();
            }
        }
        return 0;
    }

    private void injectMinecraftSound(String uri) {
        if (this.soundBufferLibrary == null) {
            soundBufferLibrary = new SoundBufferLibrary(Minecraft.getInstance().getResourceManager());
        }
        ResourceLocation location = ResourceLocation.tryParse(uri);
        if (location == null) {
            LOGGER.info("Invalid sound location {}", uri);
            return;
        }

        Optional<Holder.Reference<SoundEvent>> optionalSound = BuiltInRegistries.SOUND_EVENT.get(location);
        if (optionalSound.isEmpty()) {
            LOGGER.info("Sound {} is not in SoundEvent registry", location);
            return;
        }
        SoundEvent event = optionalSound.get().value();

        SoundManager manager = Minecraft.getInstance().getSoundManager();
        WeighedSoundEvents events = manager.getSoundEvent(event.location());

        CompletableFuture<SoundBuffer> bufferFuture = soundBufferLibrary.getCompleteBuffer(events.getSound(RandomSource.createNewThreadLocalInstance()).getPath());
        SoundBuffer buffer = bufferFuture.join();
        ByteBuffer bytes = buffer.data;
        AudioFormat format = buffer.format;

        if (bytes != null) injectRawSamples(bytes, format);
        else LOGGER.info("Sound {} has no data", location);
    }

    private void injectRawSamples(ByteBuffer rawData, AudioFormat format) {
        rawData.order(ByteOrder.LITTLE_ENDIAN);

        float originalSampleRate = format.getSampleRate();
        int channels = format.getChannels();

        float step = originalSampleRate / 22050f;

        int totalFrames = rawData.remaining() / (2 * channels);

        synchronized (audioBuffer) {
            float inputFrameIndex = 0;

            while (inputFrameIndex < totalFrames) {
                while (available >= RING_CAPACITY) {
                    try { audioBuffer.wait(); } catch (InterruptedException e) { return; }
                }

                int byteIdx = (int)inputFrameIndex * channels * 2;

                short sample;
                if (channels == 2) {
                    short left = rawData.getShort(byteIdx);
                    short right = rawData.getShort(byteIdx + 2);
                    sample = (short) ((left + right) / 2);
                } else {
                    sample = rawData.getShort(byteIdx);
                }

                audioBuffer.setAtIndex(ValueLayout.JAVA_SHORT, writePos, sample);
                writePos = (writePos + 1) % RING_CAPACITY;
                available++;

                inputFrameIndex += step;
            }
            audioBuffer.notifyAll();
        }
    }

    public void cancel() {
        cancelled = true;

        synthExecutor.shutdownNow();
        synthExecutor = createExecutor();

        synchronized (audioBuffer) {
            writePos = 0;
            readPos = 0;
            available = 0;
            resampleOffset = 0.0;
            lastSample = 0;

            audioBuffer.notifyAll();
        }

        cancelled = false;
    }

    private ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "eSpeak-Synthesizer-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void dispatch(Runnable task) {
        synthExecutor.execute(task);
    }

    public void stop() {
        synthExecutor.shutdownNow();
    }

    public boolean frameAvailable() {
        synchronized (audioBuffer) {
            return available >= 2;
        }
    }

    public short[] getFrame() {
        short[] out = new short[OUT_FRAME_SAMPLES];
        double step = (double) IN_RATE / OUT_RATE;

        synchronized (audioBuffer) {
            for (int i = 0; i < OUT_FRAME_SAMPLES; i++) {
                if (available < 2) {
                    out[i] = lastSample;
                    continue;
                }

                int nextPos = (readPos + 1) % RING_CAPACITY;
                short s0 = audioBuffer.getAtIndex(ValueLayout.JAVA_SHORT, readPos);
                short s1 = audioBuffer.getAtIndex(ValueLayout.JAVA_SHORT, nextPos);

                out[i] = (short) (s0 + (s1 - s0) * resampleOffset);
                lastSample = out[i];

                resampleOffset += step;
                while (resampleOffset >= 1.0) {
                    resampleOffset -= 1.0;
                    readPos = (readPos + 1) % RING_CAPACITY;
                    available--;
                    if (available < 2) break;
                }
            }
            audioBuffer.notifyAll();
        }
        return out;
    }

    public ESpeak getESpeak() {
        return eSpeak;
    }
}
