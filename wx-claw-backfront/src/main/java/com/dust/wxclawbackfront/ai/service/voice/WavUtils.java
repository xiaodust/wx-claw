package com.dust.wxclawbackfront.ai.service.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class WavUtils {

    private WavUtils() {
    }

    public static WavAudio parse(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length < 44) {
            return null;
        }
        if (!matchAscii(wavBytes, 0, "RIFF") || !matchAscii(wavBytes, 8, "WAVE")) {
            return null;
        }

        int fmtOffset = findChunkOffset(wavBytes, "fmt ");
        int dataOffset = findChunkOffset(wavBytes, "data");
        if (fmtOffset < 0 || dataOffset < 0) {
            return null;
        }

        int fmtSize = readIntLE(wavBytes, fmtOffset + 4);
        int fmtBody = fmtOffset + 8;
        if (fmtSize < 16 || fmtBody + fmtSize > wavBytes.length) {
            return null;
        }

        int audioFormat = readShortLE(wavBytes, fmtBody) & 0xFFFF;
        int channels = readShortLE(wavBytes, fmtBody + 2) & 0xFFFF;
        int sampleRate = readIntLE(wavBytes, fmtBody + 4);
        int byteRate = readIntLE(wavBytes, fmtBody + 8);
        int bitsPerSample = readShortLE(wavBytes, fmtBody + 14) & 0xFFFF;

        if (audioFormat != 1 || channels <= 0 || sampleRate <= 0 || byteRate <= 0 || bitsPerSample <= 0) {
            return null;
        }

        int dataSize = readIntLE(wavBytes, dataOffset + 4);
        int dataBody = dataOffset + 8;
        if (dataSize < 0 || dataBody + dataSize > wavBytes.length) {
            return null;
        }

        int durationMs = (int) Math.max(0, Math.round((dataSize * 1000.0) / byteRate));
        return new WavAudio(sampleRate, bitsPerSample, channels, durationMs, wavBytes);
    }

    public static byte[] trimToDurationMs(byte[] wavBytes, int maxDurationMs) {
        if (wavBytes == null || wavBytes.length < 44 || maxDurationMs <= 0) {
            return wavBytes;
        }
        WavAudio wav = parse(wavBytes);
        if (wav == null || wav.durationMs() <= maxDurationMs) {
            return wavBytes;
        }

        int dataOffset = findChunkOffset(wavBytes, "data");
        if (dataOffset < 0) {
            return wavBytes;
        }
        int dataSize = readIntLE(wavBytes, dataOffset + 4);
        int dataBody = dataOffset + 8;

        int bytesPerSample = Math.max(1, (wav.bitsPerSample() / 8) * Math.max(1, wav.channels()));
        int bytesPerSecond = wav.sampleRate() * bytesPerSample;
        int maxDataBytes = (int) Math.min(dataSize, (long) bytesPerSecond * maxDurationMs / 1000L);
        maxDataBytes = (maxDataBytes / bytesPerSample) * bytesPerSample;
        maxDataBytes = Math.max(0, maxDataBytes);

        int newFileSizeMinus8 = (wavBytes.length - 8) - (dataSize - maxDataBytes);
        byte[] out = Arrays.copyOf(wavBytes, wavBytes.length - (dataSize - maxDataBytes));

        writeIntLE(out, 4, newFileSizeMinus8);
        writeIntLE(out, dataOffset + 4, maxDataBytes);

        int expectedLen = dataBody + maxDataBytes;
        if (out.length != expectedLen) {
            out = Arrays.copyOf(out, expectedLen);
            writeIntLE(out, 4, expectedLen - 8);
        }
        return out;
    }

    private static int findChunkOffset(byte[] bytes, String chunkId) {
        int i = 12;
        while (i + 8 <= bytes.length) {
            String id = ascii(bytes, i, 4);
            int size = readIntLE(bytes, i + 4);
            int body = i + 8;
            if (chunkId.equals(id)) {
                return i;
            }
            int next = body + size;
            next = (next % 2 == 1) ? next + 1 : next;
            i = next;
        }
        return -1;
    }

    private static boolean matchAscii(byte[] bytes, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > bytes.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != bytes[offset + i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] bytes, int offset, int len) {
        if (offset < 0 || offset + len > bytes.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) bytes[offset + i]);
        }
        return sb.toString();
    }

    private static int readIntLE(byte[] bytes, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt();
    }

    private static short readShortLE(byte[] bytes, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getShort();
    }

    private static void writeIntLE(byte[] bytes, int offset, int value) {
        ByteBuffer bb = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(value);
    }
}


