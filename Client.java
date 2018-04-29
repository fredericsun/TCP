import java.net.*;
import java.nio.*;
import java.io.*;


public class Client {
    public int port_num;
    public InetAddress inet_remote_ip;
    public int remote_port;
    public byte[] data;
    public int mtu;

    public static int sws;
    public static DatagramSocket socket;
    public static long timestamp_lastsent;
    public static long nextbytetosend = 0;
    public static long timeout;
    public static boolean ACKed;

    public Client(DatagramSocket socket, int port_num, InetAddress inet_remote_ip , int remote_port,
                  byte[] file, int mtu, int sws) {
        //initialize parameters
        this.port_num = port_num;
        this.inet_remote_ip  = inet_remote_ip ;
        this.remote_port = remote_port;
        this.data = file;
        this.mtu = mtu;

        //variables that need to be shared
        socket.connect(inet_remote_ip, remote_port);
        this.socket = socket;
        this.sws = sws;

        //excution
        Client_OutThread out = new Client_OutThread(port_num, file, mtu);
        Client_InThread in = new Client_InThread(port_num, file, mtu);
    }
}


class Client_OutThread extends Thread {
    public int port_num;
    public byte[] data;
    public int mtu;

    public Client_OutThread(int port_num, byte[] file, int mtu) {
        //initialize parameters
        this.port_num = port_num;
        this.data = file;
        this.mtu = mtu;
    }

    public void run() {
        int mtu_num = 1;
        if (this.data.length <= mtu) {
            double a = Math.ceil((double)this.data.length / mtu);
            mtu_num = (int)a;
        }
        else {
            //do nothing
        }

        // define header information
        long ack_num = 0;
        long timestamp;
        long length = 0;
        byte[] s_f_a = new byte[3];
        byte[] all_zero = new byte[16];
        long checksum = 0;

        // hand-shaking
        if (Client.nextbytetosend == 0) {
            s_f_a[0] = 1;
            timestamp = System.nanoTime();
            byte[] packet_data = toByteArray(Client.nextbytetosend, ack_num, timestamp, length,
                    s_f_a, all_zero, checksum, null);
            DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
            try {
                Client.socket.send(packet);
                System.out.printf("snd %d S --- %d %d %d \n", timestamp, Client.nextbytetosend, 0, ack_num);
            }
            catch (IOException e) {
                System.out.println("Data send failure");
            }
        }
        // *******Do we need to implement the third leg of hand-shaking?*******
        // Data transmit
        else {
            for (int i = 1; i <= mtu_num; i++) {
                s_f_a[0] = 1;
                timestamp = System.nanoTime();
            }
        }
    }

    public byte[] toByteArray(long seq_num, long ack_num, long timestamp,
                              long length, byte[] s_f_a, byte[] all_zero,
                              long checksum, byte[] data) {
        byte[] bytes_seq_num = new byte[32];
        byte[] bytes_ack_num = new byte[32];
        byte[] bytes_timestamp = new byte[64];
        byte[] bytes_length = new byte[29];
        byte[] bytes_checksum = new byte[16];

        ByteBuffer seq = ByteBuffer.wrap(bytes_seq_num);
        seq.putLong(seq_num);
        ByteBuffer ack = ByteBuffer.wrap(bytes_ack_num);
        ack.putLong(ack_num);
        ByteBuffer time = ByteBuffer.wrap(bytes_timestamp);
        time.putLong(timestamp);
        ByteBuffer l = ByteBuffer.wrap(bytes_length);
        l.putLong(length);
        ByteBuffer check = ByteBuffer.wrap(bytes_checksum);
        check.putLong(checksum);

        if (length != 0) {
            ByteBuffer concatenate = ByteBuffer.allocate(6 * 32 + data.length);
            concatenate.put(bytes_seq_num);
            concatenate.put(bytes_ack_num);
            concatenate.put(bytes_timestamp);
            concatenate.put(bytes_length);
            concatenate.put(s_f_a);
            concatenate.put(all_zero);
            concatenate.put(bytes_checksum);
            concatenate.put(data);
            return concatenate.array();
        }
        else {
            ByteBuffer concatenate = ByteBuffer.allocate(6 * 32);
            concatenate.put(bytes_seq_num);
            concatenate.put(bytes_ack_num);
            concatenate.put(bytes_timestamp);
            concatenate.put(bytes_length);
            concatenate.put(s_f_a);
            concatenate.put(all_zero);
            concatenate.put(bytes_checksum);
            return concatenate.array();
        }
    }
}

class Client_InThread extends Thread {
    public int port_num;
    public byte[] data;
    public int mtu;

    public Client_InThread(int port_num, byte[] file, int mtu) {
        //initialize parameters
        this.port_num = port_num;
        this.data = file;
        this.mtu = mtu;
    }

    public void run() {
        byte[] data_received = new byte[6*32];
        DatagramPacket packet_received = new DatagramPacket(data_received, data_received.length);
        try {
            Client.socket.receive(packet_received);
        }
        catch (IOException e) {
            System.out.println();
        }
    }
}
