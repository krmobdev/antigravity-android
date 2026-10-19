package com.agy.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Size-bounded ConnectRPC streaming envelope parser.
 * Each message is: 1 flag byte + 4-byte unsigned big-endian size + payload.
 */
public final class ConnectFrameReader {
    public static final int MAX_MESSAGE_BYTES = 4 * 1024 * 1024;

    public static final class Frame {
        public final int flags;
        public final byte[] payload;
        Frame(int flags, byte[] payload) { this.flags = flags; this.payload = payload; }
        public boolean isEndStream() { return (flags & 0x02) != 0; }
    }

    private ConnectFrameReader() {}

    /** Every Connect server-stream request uses the same 5-byte frame envelope. */
    public static void writeRequest(OutputStream output, byte[] payload) throws IOException {
        if (payload == null || payload.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Connect request exceeds size limit");
        }
        int size = payload.length;
        output.write(0); // uncompressed request
        output.write((size >>> 24) & 255);
        output.write((size >>> 16) & 255);
        output.write((size >>> 8) & 255);
        output.write(size & 255);
        output.write(payload);
        output.flush();
    }

    /** Returns null ONLY when no bytes are available at a clean EOF. */
    public static Frame next(InputStream stream) throws IOException {
        int flags = stream.read();
        if (flags == -1) return null;
        if ((flags & ~0x03) != 0) throw new IOException("Unsupported Connect frame flags");
        byte[] header = new byte[4];
        readFully(stream, header);
        long size = ((long)(header[0] & 255) * 16777216L)
                  + ((long)(header[1] & 255) * 65536L)
                  + ((long)(header[2] & 255) * 256L)
                  + (header[3] & 255);
        if (size > MAX_MESSAGE_BYTES) throw new IOException("Connect payload exceeds limit");
        byte[] message = new byte[(int)size];
        readFully(stream, message);
        return new Frame(flags, message);
    }

    private static void readFully(InputStream stream, byte[] target) throws IOException {
        int off = 0;
        while (off < target.length) {
            int n = stream.read(target, off, target.length - off);
            if (n < 0) throw new EOFException("Truncated Connect frame");
            if (n == 0) throw new IOException("Connect stream stalled");
            off += n;
        }
    }
}
