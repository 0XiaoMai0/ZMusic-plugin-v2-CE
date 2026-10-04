package me.zhenxin.zmusic.audio;

import de.sciss.jump3r.lowlevel.LameEncoder;
import net.sourceforge.jaad.SampleBuffer;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.MP4InputStream;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 纯 Java AAC 解码和 MP3 编码；兼容 B 站 DASH 分片，所有音频只在有界内存中存在。 */
public final class AacToMp3 {
    public static final int MAX_BYTES = 24 * 1024 * 1024;
    private static final int MAX_FRAMES = 180000;
    private AacToMp3() { }

    public static byte[] convert(byte[] input) throws IOException {
        if (input.length > MAX_BYTES) throw new IOException("音频超过 24 MiB 限制");
        List<Box> top = boxes(input, 0, input.length);
        if (top.isEmpty() || !"ftyp".equals(top.get(0).type) || top.stream().noneMatch(box -> "moov".equals(box.type))) {
            throw new IOException("音频没有有效 MP4 元数据");
        }
        try (MP4InputStream stream = new MemoryMp4Stream(input)) {
            Movie movie = new MP4Container(stream).getMovie();
            if (movie == null) throw new IOException("音频没有 MP4 元数据");
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
            if (tracks.size() != 1) throw new IOException("需要一个独立 AAC 音轨");
            Track track = tracks.get(0);
            if (track.getProtection() != null) throw new IOException("不支持加密音频");
            if (track.getDecoderSpecificInfo() == null) throw new IOException("AAC 解码参数缺失");
            try (Encoder encoder = new Encoder(track.getDecoderSpecificInfo().getData())) {
                boolean fragmented = false;
                for (Box box : top) {
                    if (!"moof".equals(box.type)) continue;
                    fragmented = true;
                    Box next = top.stream().filter(b -> b.start >= box.end && "mdat".equals(b.type)).findFirst()
                            .orElseThrow(() -> new IOException("分片数据缺失"));
                    decodeFragment(input, box, next, encoder);
                }
                if (!fragmented) {
                    while (track.hasMoreFrames()) encoder.accept(track.readNextFrame().getData());
                }
                return encoder.finish();
            }
        } catch (RuntimeException error) {
            // 解码器异常不包含媒体地址；避免把失效媒体当成有效歌曲。
            throw new IOException("AAC 音频解码失败: " + error.getClass().getSimpleName(), error);
        }
    }

    private static void decodeFragment(byte[] input, Box moof, Box mdat, Encoder encoder) throws IOException {
        List<Box> children = boxes(input, moof.payload, moof.end);
        int count = 0;
        for (Box traf : children) {
            if (!"traf".equals(traf.type)) continue;
            if (++count > 1) throw new IOException("独立音频分片包含多个音轨");
            long base = moof.start;
            int defaultSize = 0;
            List<Box> entries = boxes(input, traf.payload, traf.end);
            Box tfhd = entries.stream().filter(b -> "tfhd".equals(b.type)).findFirst()
                    .orElseThrow(() -> new IOException("分片缺少 tfhd"));
            ByteBuffer header = payload(input, tfhd);
            int flags = header.getInt() & 0xffffff;
            header.getInt();
            if ((flags & 0x1) != 0) base = header.getLong();
            if ((flags & 0x2) != 0) header.getInt();
            if ((flags & 0x8) != 0) header.getInt();
            if ((flags & 0x10) != 0) defaultSize = header.getInt();
            long offset = mdat.payload;
            for (Box trun : entries) {
                if (!"trun".equals(trun.type)) continue;
                ByteBuffer run = payload(input, trun);
                int runFlags = run.getInt() & 0xffffff;
                int samples = run.getInt();
                if (samples < 0 || samples > 8192) throw new IOException("分片样本数无效");
                if ((runFlags & 0x1) != 0) offset = base + run.getInt();
                if ((runFlags & 0x4) != 0) run.getInt();
                for (int i = 0; i < samples; i++) {
                    if ((runFlags & 0x100) != 0) run.getInt();
                    int size = (runFlags & 0x200) != 0 ? run.getInt() : defaultSize;
                    if ((runFlags & 0x400) != 0) run.getInt();
                    if ((runFlags & 0x800) != 0) run.getInt();
                    if (size <= 0 || size > 16384 || offset < mdat.payload || offset + size > mdat.end) {
                        throw new IOException("AAC 分片样本范围无效");
                    }
                    encoder.accept(Arrays.copyOfRange(input, (int) offset, (int) offset + size));
                    offset += size;
                }
            }
        }
        if (count != 1) throw new IOException("分片音轨缺失");
    }

    private static ByteBuffer payload(byte[] input, Box box) {
        return ByteBuffer.wrap(input, box.payload, box.end - box.payload).slice().order(ByteOrder.BIG_ENDIAN);
    }

    private static List<Box> boxes(byte[] input, int start, int end) throws IOException {
        List<Box> result = new ArrayList<>();
        for (int offset = start; offset < end;) {
            if (end - offset < 8) throw new IOException("MP4 数据截断");
            ByteBuffer header = ByteBuffer.wrap(input, offset, end - offset).slice().order(ByteOrder.BIG_ENDIAN);
            long size = Integer.toUnsignedLong(header.getInt());
            String type = new String(input, offset + 4, 4, StandardCharsets.US_ASCII);
            int prefix = 8;
            if (size == 1) { if (header.remaining() < 12) throw new IOException("MP4 扩展头截断"); size = header.getLong(8); prefix = 16; }
            if (size == 0) size = end - offset;
            if (size < prefix || size > end - offset) throw new IOException("MP4 box 范围无效");
            result.add(new Box(type, offset, offset + prefix, offset + (int) size));
            if (result.size() > 4096) throw new IOException("MP4 box 过多");
            offset += (int) size;
        }
        return result;
    }

    private static final class Box {
        final String type;
        final int start, payload, end;
        Box(String type, int start, int payload, int end) { this.type = type; this.start = start; this.payload = payload; this.end = end; }
    }

    /** JAAD 的默认 skipBytes 在截断输入上可能空转，这里越界直接失败。 */
    private static final class MemoryMp4Stream extends MP4InputStream {
        final byte[] data;
        int position;
        MemoryMp4Stream(byte[] data) { this.data = data; }
        @Override public int read() { return position < data.length ? data[position++] & 255 : -1; }
        @Override public int read(byte[] buffer, int offset, int length) {
            if (length == 0) return 0;
            if (position == data.length) return -1;
            int count = Math.min(length, data.length - position);
            System.arraycopy(data, position, buffer, offset, count); position += count; return count;
        }
        @Override public long skip(long length) throws IOException {
            if (length < 0 || length > data.length - position) throw new EOFException("MP4 数据截断");
            position += (int) length; return length;
        }
        @Override public void seek(long target) throws IOException {
            if (target < 0 || target > data.length) throw new EOFException("MP4 数据范围无效");
            position = (int) target;
        }
        @Override public long getOffset() { return position; }
        @Override public boolean seekSupported() { return true; }
        @Override public boolean hasLeft() { return position < data.length; }
        @Override public int available() { return data.length - position; }
        @Override public void close() { }
    }

    private static final class Encoder implements AutoCloseable {
        final Decoder decoder;
        final SampleBuffer pcm = new SampleBuffer();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        LameEncoder lame;
        byte[] encoded;
        int frames;
        Encoder(byte[] configuration) { decoder = Decoder.create(configuration); pcm.setBigEndian(false); }
        void accept(byte[] frame) throws IOException {
            if (++frames > MAX_FRAMES || Thread.currentThread().isInterrupted()) throw new IOException("音频适配已取消或超过时长限制");
            decoder.decodeFrame(frame, pcm);
            if (pcm.getData().length == 0) throw new IOException("AAC 解码返回空音频");
            if (lame == null) {
                if (pcm.getChannels() < 1 || pcm.getChannels() > 2 || pcm.getSampleRate() < 8000 || pcm.getSampleRate() > 48000) {
                    throw new IOException("不支持的 AAC 采样格式");
                }
                AudioFormat format = new AudioFormat(pcm.getSampleRate(), 16, pcm.getChannels(), true, false);
                lame = new LameEncoder(format, 128, LameEncoder.CHANNEL_MODE_AUTO, 5, false);
                encoded = new byte[lame.getMP3BufferSize()];
            }
            int count = lame.encodeBuffer(pcm.getData(), 0, pcm.getData().length, encoded);
            append(count);
        }
        private void append(int count) throws IOException {
            if (count < 0 || output.size() + count > MAX_BYTES) throw new IOException("MP3 编码失败或超过内存限制");
            output.write(encoded, 0, count);
        }
        byte[] finish() throws IOException {
            if (lame == null) throw new IOException("音频没有可解码帧");
            append(lame.encodeFinish(encoded));
            return output.toByteArray();
        }
        @Override public void close() { if (lame != null) lame.close(); }
    }
}
