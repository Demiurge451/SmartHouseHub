import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    public static String byteToHex(byte b) {
        return Integer.toHexString(b & 0xff);
    }

    public static int byteToInt(byte b) {
        return b & 0xff;
    }

    static class ULEB128Coder{
        private final static int BIT_MASK = 0x7f;
        private final static int NEXT_MASK = 0x80;
        public static byte[] encode(long value) {
            ArrayList<Byte> bytes = new ArrayList<>();
            do {
                byte b = (byte) (value & BIT_MASK);
                long cur = b & 0xff;
                value >>= 7;
                if (value != 0) {
                    cur |= NEXT_MASK;
                }
                bytes.add((byte)cur);
            } while (value != 0);

            byte[] res = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                res[i] = bytes.get(i);
            }

            return res;
        }

        public static long decode(byte[] bytes) {
            long value = 0;
            int shift = 0;
            for (byte b: bytes) {
                long cur = b & 0xff;
                value |= (cur & BIT_MASK) << shift;
                shift += 7;
            }
            return value;
        }
    }

    static abstract class struct {
        int size = 0;
        abstract byte[] toBytes();
    }
    static class Packet extends struct{
        int length;
        Payload payload;
        int crc8;
        public Packet(byte[] bytes) {
            this.size = bytes.length;
            this.length = byteToInt(bytes[0]);
            this.crc8 = byteToInt(bytes[bytes.length - 1]);
            this.payload = new Payload(Arrays.copyOfRange(bytes, 1, bytes.length -1));
        }

        @Override
        public byte[] toBytes() {
            byte[] res = new byte[this.size];
            ByteBuffer buff = ByteBuffer.wrap(res);
            buff.put((byte)this.length);
            buff.put(this.payload.toBytes());
            buff.put((byte)crc8);
            return buff.array();
        }
    }

    static class Payload extends struct{
        long src;
        long dst;
        int serial;
        int devType;
        int cmd;
        struct cmdBody;

        public Payload(byte[] bytes) {
            this.size = bytes.length;
            this.src = ULEB128Coder.decode(Arrays.copyOfRange(bytes, 0, 2));
            this.dst = ULEB128Coder.decode(Arrays.copyOfRange(bytes, 2, 4));
            this.serial = byteToInt(bytes[4]);
            this.devType = byteToInt(bytes[5]);
            this.cmd = byteToInt(bytes[6]);
            initCmdBody(Arrays.copyOfRange(bytes, 7, bytes.length));
        }

        private void initCmdBody(byte[] bytes) {
            if (this.cmd == 6) {
                this.cmdBody = new TimeCmdBody(bytes);
            } else {
                this.cmdBody = new DeviceCmdBody(bytes);
            }
        }
        @Override
        public byte[] toBytes() {
            byte[] res = new byte[this.size];
            ByteBuffer buff = ByteBuffer.wrap(res);
            buff.put(ULEB128Coder.encode(this.src));
            buff.put(ULEB128Coder.encode(this.dst));
            buff.put(ULEB128Coder.encode((byte)serial));
            buff.put(ULEB128Coder.encode((byte)devType));
            buff.put(ULEB128Coder.encode((byte)cmd));
            buff.put(cmdBody.toBytes());
            return buff.array();
        }
    }
    static class TimeCmdBody extends struct{
        long timestamp;

        public TimeCmdBody(byte[] bytes) {
            this.size = bytes.length;
            this.timestamp = ULEB128Coder.decode(bytes);
        }

        @Override
        public byte[] toBytes() {
            byte[] res = new byte[this.size];
            ByteBuffer buff = ByteBuffer.wrap(res);
            buff.put(ULEB128Coder.encode(timestamp));
            return buff.array();
        }
    }

    static class DeviceCmdBody extends struct{
        int stringLen;
        String devName;
        EnvSensorProps devProps;
        public DeviceCmdBody(byte[] bytes) {
            this.size = bytes.length;
            this.stringLen = bytes[0];
            devName = Arrays.toString(Arrays.copyOfRange(bytes, 0, this.stringLen));
            devProps = new EnvSensorProps(Arrays.copyOfRange(bytes, this.stringLen, bytes.length));
        }

        @Override
        public byte[] toBytes() {
            byte[] res = new byte[this.size];
            ByteBuffer buff = ByteBuffer.wrap(res);
            buff.put((byte)this.stringLen);
            buff.put(this.devName.getBytes());
            buff.put(this.devProps.toBytes());
            return buff.array();
        }
    }

    static class EnvSensorProps extends struct{
        int sensors;
        Trigger[] triggers;

        public EnvSensorProps(byte[] bytes) {
            this.size = bytes.length;
            this.sensors = byteToInt(bytes[0]);
        }

        @Override
        public byte[] toBytes() {
            byte[] res = new byte[this.size];
            ByteBuffer buff = ByteBuffer.wrap(res);
            buff.put((byte)this.sensors);
            for (Trigger t: this.triggers) {
                buff.put(t.toBytes());
            }
            return buff.array();
        }
    }

    class Trigger extends struct {
        int op;
        long value;
        String name;

        @Override
        public byte[] toBytes() {
            byte[] res = new byte[this.size];
            ByteBuffer buff = ByteBuffer.wrap(res);

            return buff.array();
        }
    }
    public static void main(String[] args) {
        byte[] arr = new byte[]{0x0d, (byte) 0xb3, 0x06, (byte) 0xff, 0x7f, 0x01, 0x06, 0x06,
                (byte) 0x88, (byte) 0xd0, (byte) 0xab, (byte) 0xfa, (byte) 0x93, 0x31, (byte) 0x8a};
        Packet p = new Packet(arr);
        byte[] res = p.toBytes();
        for (byte b: res) {
            System.out.print(byteToHex(b) + " ");
        }
    }



}