import java.math.BigInteger;
import java.net.*;
import java.nio.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.BitSet;



public class Client {
    public int port_num;
    public InetAddress inet_remote_ip;
    public int remote_port;
    public int mtu_data;

    public static byte[] buffer;
    public static Integer sws;
    public static DatagramSocket socket;
    public static List<byte[]> sender_buffer = new ArrayList<>();
    public static int last_ack_num_received = 0;
    public static int counter = 0; // fast retransmit counter
    public static Integer lastacked = -1; // pointer for sender_buffer, point to nth packet (not include 0)
    public static Integer lastsent = -1; // pointer for sender_buffer;
    public static Integer nextbytetosend = 0; // sequence number
    public static int ack_num = 0; // acknowledgement number
    public static Integer expectedAckNum = -1;
    public static Boolean isRetransmitted = false;

    // global filed for timeout and related computation;
    public static long timeout = (long)(5 * Math.pow(10, 9)); // unit is nanosecond;
    public static long ERTT = 0;
    public static long EDEV = 0;
    public static long SRTT = 0;
    public static long SDEV = 0;

    // for output information
    public static int amount_data_transferred = 0;
    public static int num_packet_sent = 0;
    public static int retransmission = 0;
    public static int duplicate_ack = 0;

    public Client(DatagramSocket socket, int port_num, InetAddress inet_remote_ip , int remote_port,
                  byte[] buffer, int mtu, int sws) {
        //initialize parameters
        this.port_num = port_num;
        this.inet_remote_ip  = inet_remote_ip ;
        this.remote_port = remote_port;
        this.mtu_data = mtu - 24;

        //variables that need to be shared
        socket.connect(inet_remote_ip, remote_port);
        Client.socket = socket;
        Client.sws = sws;
        Client.buffer = buffer;

        // process the data
        int mtu_num = 1;
        if (buffer.length >= mtu_data) {
            mtu_num = (int)(Math.ceil((double)buffer.length / mtu_data));
        }
        else {
            //do nothing
        }
        for (int i = 1; i <= mtu_num; i++) {
            if (i == mtu_num) {
                int length = buffer.length - mtu_data * (mtu_num - 1);
                byte[] segment = new byte[length];
                for (int j = (mtu_num - 1) * mtu_data; j < buffer.length; j++) {
                    segment[j - (mtu_num - 1) * mtu_data] = buffer[j];
                }
                Client.sender_buffer.add(segment);
            }
            else {
                byte[] segment = new byte[mtu_data];
                for (int j = (i - 1) * mtu_data; j < i * mtu_data; j++) {
                    segment[j - (i - 1) * mtu_data] = buffer[j];
                }
                Client.sender_buffer.add(segment);
            }
        }

        //execution
        Client_OutThread out = new Client_OutThread(port_num, mtu_data);
        Client_InThread in = new Client_InThread(port_num, mtu_data);
        out.start();
        in.start();
    }
}


class Client_OutThread extends Thread {
    public int port_num;
    public int mtu_data;

    public Client_OutThread(int port_num, int mtu_data) {
        //initialize parameters
        this.port_num = port_num;
        this.mtu_data = mtu_data;
    }

    public void run() {
        byte[] all_zero = new byte[2];

        // hand-shaking
        while (Client.nextbytetosend == 0) {
            System.out.print("");
            int data_length = 0; // length of the data
            //BitSet flag = new BitSet(8); // flag information, index 0, 1, 2, 3 are respectively SYN, FIN, ACK, DATA;
            //flag.set(0);
            int length = data_length << 3;
            length = length | 1; // set flag S
            short checksum = computeChecksum(Client.nextbytetosend);
            //short checksum = 0;
            long timestamp = System.nanoTime();
            byte[] packet_data = toByteArray(Client.nextbytetosend, Client.ack_num, timestamp, length,
                    all_zero, checksum, null);
            DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
            try {
                System.out.print("");
                System.out.printf("snd %d S--- %d %d %d \n", timestamp, Client.nextbytetosend, 0, Client.ack_num);
                Client.num_packet_sent++;
                Client.socket.send(packet);
             
                try {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e) {
                    System.out.println("The program is interrupted while sleep");
                }
            }
            catch (IOException e) {
                System.out.println("SYN send failure");
            }
        }

        // Data transmit
        while (Client.lastacked <= (Client.sender_buffer.size() - 1)) {
            System.out.print("");
            
                

                	if (Client.lastacked > Client.lastsent) {
                		Client.lastsent = Client.lastacked;
	                synchronized(Client.nextbytetosend) {
	                		Client.nextbytetosend = (Client.lastsent + 1) * mtu_data + 1;
	                }
	            }


            int curr = Client.lastsent;
            if (curr >= Client.sender_buffer.size() - 1) {
                continue;
            }
            int data_length;
            long timestamp = System.nanoTime();
            short checksum = computeChecksum(Client.nextbytetosend);
            //short checksum = 0;
            if (Client.lastsent == Client.sender_buffer.size() - 2) {
                data_length = Client.sender_buffer.get(Client.sender_buffer.size() - 1).length;
            }
            else {
                data_length = mtu_data;
            }
            int length = data_length << 3;
            byte[] packet_data = toByteArray(Client.nextbytetosend, Client.ack_num, timestamp, length,
                    all_zero, checksum, Client.sender_buffer.get(Client.lastsent + 1));
            DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
            try {
                if (Client.sws >= (data_length + 24)) {
                    System.out.print("");
                    Timer timer = new Timer(System.nanoTime(), Client.nextbytetosend, Client.lastsent, Client.lastacked);
                    System.out.printf("snd %d ---D %d %d %d \n", timestamp, Client.nextbytetosend, data_length, Client.ack_num);
                    Client.amount_data_transferred += data_length;
                    Client.num_packet_sent++;
                    synchronized(Client.sws) {
                    Client.sws = Client.sws - data_length - 24;
                    }
                    synchronized(Client.nextbytetosend) {
                    Client.nextbytetosend += data_length;
                    }
                    Client.lastsent += 1;
                    Client.socket.send(packet);
                    timer.start();
                    System.out.println("lastsent " + Client.lastsent);
                    System.out.println("lastacked " + Client.lastacked);
                    System.out.println("Window " + Client.sws);
                }
                else {
                    System.out.print("");
                }
            }
            catch (IOException e) {
                System.out.println("Data send failure");
            }
        }

        // Termination
        if (Client.lastacked == (Client.sender_buffer.size() - 1)) {
            System.out.print("");
            int data_length = 0; // length of the data
            //BitSet flag = new BitSet(8); // flag information, index 0, 1, 2, 3 are respectively SYN, FIN, ACK, DATA;
            //flag.set(1);
            int length = data_length << 3;
            length = length | 2; // set the FIN flag
            short checksum = computeChecksum(Client.nextbytetosend);
            //short checksum = 0;
            long timestamp = System.nanoTime();
            byte[] packet_data = toByteArray(Client.last_ack_num_received, Client.ack_num, timestamp, length,
                    all_zero, checksum, null);
            DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
            try {
                System.out.printf("snd %d --F- %d %d %d \n", timestamp, Client.last_ack_num_received, 0, Client.ack_num);
                Client.socket.send(packet);
            }
            catch (IOException e) {
                System.out.println("FIN send failure");
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


    public byte[] toByteArray(int seq_num, int ack_num, long timestamp,
                              int length, byte[] all_zero, short checksum, byte[] data) {
        int data_length = length >>> 3;

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

        if (data_length != 0) {
            ByteBuffer concatenate = ByteBuffer.allocate(24 + data.length);
            concatenate.put(bytes_seq_num);
            concatenate.put(bytes_ack_num);
            concatenate.put(bytes_timestamp);
            concatenate.put(bytes_length);
            concatenate.put(all_zero);
            concatenate.put(bytes_checksum);
            concatenate.put(data);
            return concatenate.array();
        }
        else {
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
}

class Client_InThread extends Thread {
    public int port_num;
    public int mtu_data;

    public Client_InThread(int port_num, int mtu_data) {
        //initialize parameters
        this.port_num = port_num;
        this.mtu_data = mtu_data;
    }

    public void run() {
        boolean loop = true;
        while(loop) {
            System.out.print("");
            try {
                System.out.print("");
                byte[] data_received = new byte[24];
                DatagramPacket packet_received = new DatagramPacket(data_received, data_received.length);
                Client.socket.receive(packet_received);
                byte[] ack_data = packet_received.getData();
                if (getFlag(ack_data).get(2)) {
                    updateTimeout(getTimestamp(ack_data), System.nanoTime(), getSequenceNumber(ack_data));
                    // Received data is ACK
                    // Then update timeout
                    if (getFlag(ack_data).get(0)) {
                        // the ACK is the hand-shaking ACK
                        Client.nextbytetosend += getAcknowledgment(ack_data);
                        Client.ack_num = getAcknowledgment(ack_data);
                        System.out.printf("rcv %d SA-- %d %d %d \n", System.nanoTime(), getSequenceNumber(ack_data), 0, Client.ack_num);
                        System.out.printf("snd %d -A-- %d %d %d \n", System.nanoTime(), 1, 0, Client.ack_num);
                        Client.num_packet_sent++;
                    }
                    else if (getFlag(ack_data).get(1) && getFlag(ack_data).get(2)) {
                        // the ACK is the FIN
                        System.out.printf("rcv %d -AF- %d %d %d \n", System.nanoTime(), getSequenceNumber(ack_data), 0, getAcknowledgment(ack_data) + 1);
                        System.out.printf("snd %d -A-- %d %d %d \n", System.nanoTime(), getAcknowledgment(ack_data), 0, getSequenceNumber(ack_data) + 1);
                        Client.num_packet_sent++;
                        Client.socket.close();
                        loop = false;
                    }
                    else {
                        // the ACK is the data ACK
                        System.out.print("");
                        System.out.printf("rcv %d -A-- %d %d %d \n", System.nanoTime(), getSequenceNumber(ack_data), 0, getAcknowledgment(ack_data));
                        Client.sws = Client.sws + mtu_data + 24;
                        int ack_received = getAcknowledgment(ack_data);
                        if (Client.last_ack_num_received == ack_received) {
                            Client.duplicate_ack++;
                            Client.counter++;
                        }
                        else {
                            Client.counter = 0;
                        }
                        Client.last_ack_num_received = ack_received;
                        synchronized(Client.lastacked) {
                        Client.lastacked = (int)Math.ceil((double)(ack_received - 1) / mtu_data) - 1;
                        }
                        if (Client.counter == 3) {
                            Client.counter = 0;
                            Client.retransmission ++;
                            synchronized(Client.lastsent) {
                            Client.lastsent = Client.lastacked;
                            }
                            synchronized(Client.nextbytetosend) {
                            Client.nextbytetosend = (Client.lastsent + 1) * mtu_data + 1;
                            }
                            synchronized(Client.isRetransmitted) {
                            	Client.isRetransmitted = true;
                            }
                        }
//                        if (Client.lastacked > Client.lastsent) {
//                        	 synchronized(Client.lastsent) {
//                            Client.lastsent = Client.lastacked;
//                        	 }
//                        	 synchronized(Client.nextbytetosend) {
//                            Client.nextbytetosend = (Client.lastsent + 1) * mtu_data + 1;
//                       
//                        }
//                        }
                    }
                }
            }
            catch (IOException e) {
                System.out.println("Receive data failure");
            }
        }
        if (Client.socket.isClosed()) {
            System.out.println();
            System.out.println("Amount of Data Transferred: " + Client.amount_data_transferred + " bytes");
            System.out.println("No of Packets Sent: " + Client.num_packet_sent);
            System.out.println("No of Retransmissions: " + Client.retransmission);
            System.out.println("No of Duplicate Acknowledgements: " + Client.duplicate_ack);
        }
    }

    public void updateTimeout(long timestamp, long currenttime, int received_seq) {
        if (received_seq == 0) {
            Client.ERTT = currenttime - timestamp;
            Client.EDEV = 0;
            Client.timeout = 2 * Client.ERTT;
        }
        else {
            Client.SRTT = currenttime - timestamp;
            Client.SDEV = Math.abs(Client.SRTT - Client.ERTT);
            Client.ERTT = (long)(0.875 * Client.ERTT + (1 - 0.875) * Client.SRTT);
            Client.EDEV = (long)(0.75 * Client.EDEV + (1 - 0.75) * Client.SDEV);
            Client.timeout = Client.ERTT + 4 * Client.EDEV;
        }
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
}
