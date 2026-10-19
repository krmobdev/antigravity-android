import com.agy.core.ConnectFrameReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ConnectFrameReaderTest {
    private static int checks;
    private static byte[] frame(int flags, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n = payload.length;
        out.write(flags);
        out.write((n / 16777216) % 256);
        out.write((n / 65536) % 256);
        out.write((n / 256) % 256);
        out.write(n % 256);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }
    private static void expect(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }
    private static void error(byte[] buffer) throws Exception {
        try {
            ConnectFrameReader.next(new ByteArrayInputStream(buffer));
            throw new AssertionError("invalid frame accepted");
        } catch (IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        byte[] p="{\"update\":{\"status\":1,\"fullyIdle\":true}}".getBytes(StandardCharsets.UTF_8);
        byte[] a=frame(0,p);
        ConnectFrameReader.Frame item=ConnectFrameReader.next(new ByteArrayInputStream(a));
        expect(item.flags==0,"data frame");
        expect(!item.isEndStream(),"data event");
        expect(new String(item.payload,StandardCharsets.UTF_8).contains("fullyIdle"),"payload");
        item=ConnectFrameReader.next(new ByteArrayInputStream(frame(2,new byte[0])));
        expect(item.isEndStream(),"end");
        expect(ConnectFrameReader.next(new ByteArrayInputStream(new byte[0]))==null,"EOF");
        error(new byte[] {0,0,0});
        error(new byte[] {0,0,0,0,2,12});
        error(new byte[] {8,0,0,0,0});
        error(new byte[] {0,127,-1,-1,-1});
        System.out.println("PASS ConnectFrameReader: "+checks+" assertions");
    }
}
