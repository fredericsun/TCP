import java.math.BigInteger;
import java.net.*;
import java.nio.*;
import java.io.*;
import java.util.HashMap;
import java.util.BitSet;

public class Server {
    public int port_num;
    public int sws;

    public static int mtu;
    public static boolean data_loop = true;
    public static boolean handshake = true;
    public static boolean handshake_received = false;
    public static long handshake_timestamp = 0;
    public static boolean fin = false;
    public static long fin_timestamp = 0;
    public static int fin_seq_num = 0;
    public static int fin_ack_num = 0;
    public static DatagramSocket socket;
    public static HashMap<Integer, byte[]> receiver_buffer = new HashMap<>();
    public static int lastacked = -1;
    public static int lastreceived = -1;

    // output information
    public static int amount_data_received = 0;
    public static int no_packets_received = 0;
    public static int no_out_of_sequence = 0;
    public static int no_wrong_checksum = 0;

    public Server(int port_num, int mtu, int sws, DatagramSocket socket) {
        this.port_num = port_num;
        this.mtu = mtu;
        this.sws = sws;

        Server.socket = socket;

        Server.receiver_buffer.put(-1, new byte[] {0});

        Server_InThread in = new Server_InThread();
        Server_OutThread out = new Server_OutThread();
        in.start();
        out.start();
    }
}

class Server_InThread extends Thread {
    public void run() {
        boolean loop = true;
        while(loop) {
            System.out.print("");
            try {
                System.out.print("");
                byte[] data_received = new byte[Server.mtu];
                DatagramPacket packet = new DatagramPacket(data_received, data_received.length);
                Server.socket.receive(packet);
                byte[] data = packet.getData();
                Server.socket.connect(packet.getAddress(), packet.getPort());
                if (getFlag(data).get(0)) {
                    // it is a handshaking SYN data
                    Server.handshake_timestamp = getTimestamp(data);
                    Server.handshake_received = true;
                    Server.no_packets_received += 2;
                }
                else if (getFlag(data).get(1)) {
                    // it is a termination FIN data
                    Server.fin_timestamp = getTimestamp(data);
                    Server.fin_seq_num = getSequenceNumber(data);
                    Server.fin_ack_num = getAcknowledgment(data);
                    Server.fin = true;
                    Server.no_packets_received += 2;
                    loop = false;
                }
                else {
                    System.out.print("");
                    int seq_num = getSequenceNumber(data);
                    short curr_checksum = getChecksum(data);
                    System.out.printf("rcv %d ---D %d %d %d \n", System.nanoTime(), getSequenceNumber(data), getDataLength(data), 1);
                    if (computeChecksum(seq_num) != curr_checksum) {
                        // checksum corrupts then drop the packet
                        Server.no_wrong_checksum ++;
                        Client.sws = Client.sws + data.length;
                        System.out.println("Drop the received packets checksum");
                        continue;
                    }
                    int position = (seq_num - 1) / (Server.mtu - 24);
                    if (Server.receiver_buffer.containsKey(position)) {
                        // received unordered data then drop the packet
                        Server.no_out_of_sequence ++;
                        System.out.println("Drop the received packets " + getSequenceNumber(data));
                        Client.sws = Client.sws + data.length;
                        System.out.println("Window " + Client.sws);
                        continue;
                    }
                    Server.amount_data_received += getDataLength(data);
                    Server.no_packets_received += 1;
                    Server.receiver_buffer.put(position, data);
                    Server.lastreceived = position;

                    System.out.println("lastacked and lastreceived " + Server.lastacked + Server.lastreceived);
                }
            }
            catch (IOException e) {
                System.out.println("Receive data failure");
            }
        }
    }

    public short computeChecksum(int seq_num) {
        String seq_string = String.format("%32s", Integer.toBinaryString(seq_num)).replace(' ', '0');
        String half_1 = seq_string.substring(0, 16);
        String half_2 = seq_string.substring(16, seq_string.length());
        String sum = new BigInteger(half_1, 2).add(new BigInteger(half_2, 2)).toString(2);
        if (sum.length() <= 16) {
            sum = String.format("%16s", sum).replace(' ', '0');
        }
        else {
            String num3 = sum.substring(1, sum.length());
            String num4 = "1";
            sum = new BigInteger(num3, 2).add(new BigInteger(num4, 2)).toString(2);
        }
        if (sum.length() <= 16) {
            sum = String.format("%16s", sum).replace(' ', '0');
        }
        else {
            sum = sum.substring(1);
        }
        String checksum = "";
        for (int i = 0; i < sum.length(); i++) {
            if (sum.charAt(i) == '1') {
                checksum += '0';
            }
            else {
                checksum += '1';
            }
        }
        return (short)Integer.parseInt(checksum, 2);
    }

    public int getAcknowledgment(byte[] data) {
        int ack_num;
        byte[] bytes_ack_num = new byte[4];
        for (int i = 4; i <= 7; i++) {
            bytes_ack_num[i - 4] = data[i];
        }
        ByteBuffer ack_num_buffer = ByteBuffer.wrap(bytes_ack_num);
        ack_num = ack_num_buffer.getInt();
        return ack_num;
    }

    public long getTimestamp(byte[] data) {
        long timestamp;
        byte[] bytes_timestamp = new byte[8];
        for (int i = 8; i <= 15; i++) {
            bytes_timestamp[i - 8] = data[i];
        }
        ByteBuffer timestamp_buffer = ByteBuffer.wrap(bytes_timestamp);
        timestamp = timestamp_buffer.getLong();
        return timestamp;
    }

    public int getSequenceNumber(byte[] data) {
        int sequence_num;
        byte[] bytes_sequence_num = new byte[4];
        for (int i = 0; i <= 3; i++) {
            bytes_sequence_num[i] = data[i];
        }
        ByteBuffer sequence_num_buffer = ByteBuffer.wrap(bytes_sequence_num);
        sequence_num = sequence_num_buffer.getInt();
        return sequence_num;
    }

    public int getLength(byte[] data) {
        int length;
        byte[] bytes_length = new byte[4];
        for (int i = 16; i <= 19; i++) {
            bytes_length[i - 16] = data[i];
        }
        ByteBuffer length_buffer = ByteBuffer.wrap(bytes_length);
        length = length_buffer.getInt();
        return length;
    }

    public int getDataLength(byte[] data) {
        int data_length;
        int length = getLength(data);
        data_length = length >>> 3;
        return  data_length;
    }

    public BitSet getFlag(byte[] data) {
        BitSet flag = new BitSet(3);
        int length = getLength(data);
        if ((length & 1) == 1) {
            flag.set(0);
        }
        if ((length & 2) == 2) {
            flag.set(1);
        }
        if ((length & 4) == 4) {
            flag.set(2);
        }
        return flag;
    }

    public short getChecksum(byte[] data) {
        short checksum;
        byte[] bytes_checksum = new byte[2];
        for (int i = 22; i <= 23; i++) {
            bytes_checksum[i - 22] = data[i];
        }
        ByteBuffer checksum_buffer = ByteBuffer.wrap(bytes_checksum);
        checksum = checksum_buffer.getShort();
        return checksum;
    }
}

class Server_OutThread extends Thread {
    public void run() {
        // hand-shaking
        while (Server.handshake) {
            System.out.print("");
            if (Server.handshake_received) {
                System.out.print("");
                long timestamp;
                int seq_num = 0;
                int ack_num;
                int data_length = 0;
                int length = data_length << 3;
                length = length | 4;
                length = length | 1;
                byte[] all_zero = new byte[2];
                short checksum = 0;
                timestamp = Server.handshake_timestamp;
                ack_num = 1;
                byte[] packet_data = toByteArray(seq_num, ack_num, timestamp, length, all_zero, checksum);
                DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
                try {
                    Server.socket.send(packet);
                    Server.handshake = false;
                }
                catch (IOException e) {
                    System.out.println("Data send failure");
                }
            }
            else {
               System.out.print("");
            }
        }

        while (Server.data_loop) {
            System.out.print("");
            if (Server.fin) {
                // termination
                long timestamp;
                int data_length = 0;
                int length = data_length << 3;
                length = length | 4;
                length = length | 2;
                byte[] all_zero = new byte[2];
                short checksum = 0;
                timestamp = Server.fin_timestamp;
                byte[] packet_data = toByteArray(Server.fin_ack_num, Server.fin_seq_num, timestamp, length, all_zero, checksum);
                DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
                try {
                    Server.socket.send(packet);
                }
                catch (IOException e) {
                    System.out.println("Data send failure");
                }
                Server.socket.close();
                break;
            }
            if (Server.lastacked < Server.lastreceived) {
                // data transmit
                int origin = Server.lastacked;
                while (Server.receiver_buffer.containsKey(Server.lastacked)) {
                    Server.lastacked++;
                }
                Server.lastacked = Server.lastacked - 1;
                int nexttoack = Server.lastacked;
                if (nexttoack == origin) {
                    continue;
                }
                long timestamp;
                int seq_num = 0;
                int ack_num = 0;
                int data_length = 0;
                int length = data_length << 3;
                length = length | 4;
                byte[] all_zero = new byte[2];
                short checksum = 0;
                timestamp = getTimestamp(Server.receiver_buffer.get(nexttoack));
                seq_num = getAcknowledgment(Server.receiver_buffer.get(nexttoack));
                ack_num = getSequenceNumber(Server.receiver_buffer.get(nexttoack)) +
                        getDataLength(Server.receiver_buffer.get(nexttoack));
                byte[] packet_data = toByteArray(seq_num, ack_num, timestamp, length, all_zero, checksum);
                DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
                try {
                    Server.socket.send(packet);
                    System.out.printf("snd %d -A-- %d %d %d \n", System.nanoTime(), seq_num, 0, ack_num);
                }
                catch (IOException e) {
                    System.out.println("Data send failure");
                }
            }
            else {
                System.out.print("");
            }
        }

        // output file
        // int length = (Server.mtu - 24) * Server.lastacked + Server.receiver_buffer.get(Server.lastacked).length;
        if (Server.socket.isClosed()) {
            System.out.println("Amount of Data Received: " + Server.amount_data_received + " bytes");
            System.out.println("No of Packets Received: " + Server.no_packets_received);
            System.out.println("No of Packets discarded (out of sequence): " + Server.no_out_of_sequence);
            System.out.println("No of Packets discarded (wrong checksum): " + Server.no_wrong_checksum);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (int count = 0; count <= Server.lastacked; count ++) {
                try {
                    if (Server.receiver_buffer.containsKey(count)) {
                        outputStream.write(getRealData(Server.receiver_buffer.get(count)));
                    }
                    else {

                    }
                }
                catch (IOException e) {
                    System.out.println("IOException");
                }
            }
            try {
                System.out.println(outputStream.toByteArray().length);
                FileOutputStream output = new FileOutputStream(new File("myFile"));
                output.write(outputStream.toByteArray());
                output.close();
            }
            catch (IOException e) {
                System.out.println("output error");
            }
        }
    }

    public byte[] getRealData(byte[] data) {
        byte[] realdata = new byte[getDataLength(data)];
        for (int i = 24; i <= 24 + getDataLength(data) - 1; i++) {
            realdata[i - 24] = data[i];
        }
        return realdata;
    }

    public long getTimestamp(byte[] data) {
        long timestamp;
        byte[] bytes_timestamp = new byte[8];
        for (int i = 8; i <= 15; i++) {
            bytes_timestamp[i - 8] = data[i];
        }
        ByteBuffer timestamp_buffer = ByteBuffer.wrap(bytes_timestamp);
        timestamp = timestamp_buffer.getLong();
        return timestamp;
    }

    public int getAcknowledgment(byte[] data) {
        int ack_num;
        byte[] bytes_ack_num = new byte[4];
        for (int i = 4; i <= 7; i++) {
            bytes_ack_num[i - 4] = data[i];
        }
        ByteBuffer ack_num_buffer = ByteBuffer.wrap(bytes_ack_num);
        ack_num = ack_num_buffer.getInt();
        return ack_num;
    }

    public int getSequenceNumber(byte[] data) {
        int sequence_num;
        byte[] bytes_sequence_num = new byte[4];
        for (int i = 0; i <= 3; i++) {
            bytes_sequence_num[i] = data[i];
        }
        ByteBuffer sequence_num_buffer = ByteBuffer.wrap(bytes_sequence_num);
        sequence_num = sequence_num_buffer.getInt();
        return sequence_num;
    }

    public int getLength(byte[] data) {
        int length;
        byte[] bytes_length = new byte[4];
        for (int i = 16; i <= 19; i++) {
            bytes_length[i - 16] = data[i];
        }
        ByteBuffer length_buffer = ByteBuffer.wrap(bytes_length);
        length = length_buffer.getInt();
        return length;
    }

    public int getDataLength(byte[] data) {
        int data_length;
        int length = getLength(data);
        data_length = length >>> 3;
        return  data_length;
    }

    public byte[] toByteArray(int seq_num, int ack_num, long timestamp,
                              int length, byte[] all_zero, short checksum) {
        byte[] bytes_seq_num = new byte[4];
        byte[] bytes_ack_num = new byte[4];
        byte[] bytes_timestamp = new byte[8];
        byte[] bytes_length = new byte[4];
        byte[] bytes_checksum = new byte[2];

        ByteBuffer seq = ByteBuffer.wrap(bytes_seq_num);
        seq.putInt(seq_num);
        ByteBuffer ack = ByteBuffer.wrap(bytes_ack_num);
        ack.putInt(ack_num);
        ByteBuffer time = ByteBuffer.wrap(bytes_timestamp);
        time.putLong(timestamp);
        ByteBuffer l = ByteBuffer.wrap(bytes_length);
        l.putInt(length);
        ByteBuffer check = ByteBuffer.wrap(bytes_checksum);
        check.putShort(checksum);

        ByteBuffer concatenate = ByteBuffer.allocate(24);
        concatenate.put(bytes_seq_num);
        concatenate.put(bytes_ack_num);
        concatenate.put(bytes_timestamp);
        concatenate.put(bytes_length);
        concatenate.put(all_zero);
        concatenate.put(bytes_checksum);
        return concatenate.array();
    }
}

