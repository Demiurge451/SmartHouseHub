import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

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
        protected int size = 0;
        public abstract byte[] toBytes();
        protected byte[] combineByteArrays(int size, byte[] ... k) {
            byte[] res = new byte[size];
            ByteBuffer buff = ByteBuffer.wrap(res);
            for (byte[] b: k) {
                buff.put(b);
            }
            return buff.array();
        }

        protected byte[] asArray(byte b) {
            return new byte[]{b};
        }
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
            return combineByteArrays(size, asArray((byte)this.length), this.payload.toBytes(), asArray((byte)crc8));
        }
    }

    static abstract class Device extends struct{
        String devName;
        public void setName(byte[] bytes) {
            int len = byteToInt(bytes[0]);
            this.devName = new String(Arrays.copyOfRange(bytes, 0, len), StandardCharsets.UTF_8);
        }
    }

    static class DeviceFabric {
        public Device createDevice(int devType, int cmd, byte[] bytes) {
            Device device = null;
            switch (devType) {
                case 1 -> device = new SmartHub(cmd, bytes);
                case 2 -> device = new EnvSensor(cmd, bytes);
                case 3 -> device = new Switch(cmd, bytes);
                case 4 -> device = new Lamp(cmd, bytes);
                case 5 -> device = new Socket(cmd, bytes);
                case 6 -> device = new Clock(cmd, bytes);
            }
            return device;
        }
    }

    static class SmartHub extends Device{

        public SmartHub(int cmd, byte[] bytes) {
            this.size = bytes.length;
            setName(bytes);
        }
        @Override
        public byte[] toBytes() {
            return combineByteArrays(this.size, devName.getBytes());
        }
    }

    static class EnvSensor extends Device {
        int sensors;
        Trigger[] triggers;
        long[] values;
        public EnvSensor(int cmd, byte[] bytes) {
            this.size = bytes.length;

        }

        @Override
        public byte[] toBytes() {
            return new byte[0];
        }
    }

    static class Switch extends Device {
        int turnOn;
        String[] devNames;
        public Switch(int cmd, byte[] bytes) {
            this.size = bytes.length;
        }

        @Override
        public byte[] toBytes() {
            return new byte[0];
        }
    }

    static class Lamp extends Device {
        int turnOn;
        public Lamp(int cmd, byte[] bytes) {
            this.size = bytes.length;

        }

        @Override
        public byte[] toBytes() {
            return new byte[0];
        }
    }

    static class Socket extends Device {
        int turnOn;
        public Socket(int cmd, byte[] bytes) {
            this.size = bytes.length;
        }

        @Override
        public byte[] toBytes() {
            return new byte[0];
        }
    }

    static class Clock extends Device {
        long timestamp;
        public Clock(int cmd, byte[] bytes) {
            this.size = bytes.length;
            if (cmd == 1) {
                setName(bytes);
            } else if (cmd == 6) {
                timestamp = ULEB128Coder.decode(bytes);
            } else {
                //TODO write exception
            }
        }

        @Override
        public byte[] toBytes() {
            //TODO rewrite convert to bytes
            return combineByteArrays(this.size, ULEB128Coder.encode(timestamp));
        }
    }

    static class Payload extends struct{
        private final DeviceFabric deviceFabric = new DeviceFabric();
        long src;
        long dst;
        int serial;
        int devType;
        int cmd;
        Device device;
        public Payload(byte[] bytes) {
            this.size = bytes.length;
            this.src = ULEB128Coder.decode(Arrays.copyOfRange(bytes, 0, 2));
            this.dst = ULEB128Coder.decode(Arrays.copyOfRange(bytes, 2, 4));
            this.serial = byteToInt(bytes[4]);
            this.devType = byteToInt(bytes[5]);
            this.cmd = byteToInt(bytes[6]);
            this.device = deviceFabric.createDevice(devType, cmd, Arrays.copyOfRange(bytes,7, bytes.length));
        }
        @Override
        public byte[] toBytes() {
            return combineByteArrays(size, ULEB128Coder.encode(this.src), ULEB128Coder.encode(this.dst),
                    asArray((byte)this.serial), asArray((byte) this.devType),
                    asArray((byte)this.cmd),this.device.toBytes());
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
        System.out.println();
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(res);
        System.out.println(s);
        res = Base64.getUrlDecoder().decode(s);
        for (byte b: res) {
            System.out.print(byteToHex(b) + " ");
        }
    }
}