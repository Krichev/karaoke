package com.karaoke.util;

import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;

import javax.sound.sampled.AudioInputStream;
import java.io.IOException;

public class UniversalAudioInputStream implements TarsosDSPAudioInputStream {

    private final AudioInputStream underlyingStream;
    private final TarsosDSPAudioFormat tarsosDSPAudioFormat;

    public UniversalAudioInputStream(AudioInputStream underlyingStream) {
        this.underlyingStream = underlyingStream;
        javax.sound.sampled.AudioFormat underlyingFormat = underlyingStream.getFormat();
        this.tarsosDSPAudioFormat = new TarsosDSPAudioFormat(
                underlyingFormat.getSampleRate(),
                underlyingFormat.getSampleSizeInBits(),
                underlyingFormat.getChannels(),
                underlyingFormat.getEncoding() == javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                underlyingFormat.isBigEndian()
        );
    }

    @Override
    public long skip(long bytesToSkip) throws IOException {
        return underlyingStream.skip(bytesToSkip);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return underlyingStream.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        underlyingStream.close();
    }

    @Override
    public TarsosDSPAudioFormat getFormat() {
        return tarsosDSPAudioFormat;
    }

    @Override
    public long getFrameLength() {
        return underlyingStream.getFrameLength();
    }
}
