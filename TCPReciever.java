import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;

public class TCPReciever {
	private int mtu;
	private DatagramSocket socket;
	private String fileName;
	private int clientPort; //
    private InetAddress clientIP;
    private int ackNum;
    boolean isRunning;
	
    private HashMap<Integer,byte[]> receivedBuffer;
	
	public TCPReciever(int mtu, DatagramSocket socket) {
        this.mtu = mtu;
        Server.socket = socket;
        this.receivedBuffer = new HashMap<Integer,byte[]>();
	}



	public void recieve() throws IOException {
		// TODO Auto-generated method stub
		this.isRunning = true;
		int sequence;
		while(isRunning) {
			TCPPacket recPacket = recData(); //get the TCP packet
			//check the checksum
			short chcksum = recPacket.getChecksum();
			recPacket.serialize();
			if(recPacket.getChecksum() != chcksum) {
				//Checksum don't match, send multiple acks to allow for fast retransmission
				sendAcknowledgement("A", recPacket.getSeq() , recPacket.getTimsStamp());
				sendAcknowledgement("A", recPacket.getSeq() , recPacket.getTimsStamp());
				sendAcknowledgement("A", recPacket.getSeq() , recPacket.getTimsStamp());
				sendAcknowledgement("A", recPacket.getSeq() , recPacket.getTimsStamp());
				continue;
			}
			ackNum = recPacket.getSeq()+1; //next expected 
			sequence = recPacket.getSeq();
			if (recPacket.getFlags().contains("D")) {
				receivedBuffer.put(sequence, recPacket.getData());
				this.sendAcknowledgement("A", ackNum, recPacket.getTimsStamp());
			}
			else if(recPacket.getFlags().contains("F")) {
				//TO DO - initiate the teardown process and write the file here
				String filename = new String(recPacket.getData());
				writeToFile(filename);
				sendAcknowledgement("A", ackNum, recPacket.getTimsStamp());
				sendAcknowledgement("FA", ackNum, System.nanoTime());
				TCPPacket finalAck = recData();
				if(finalAck.getFlags().contains("A")) {
					isRunning = false;
					break;
				}
				
				
			}
			
		}
		System.exit(0);
	}

	
	private void writeToFile(String filename) throws IOException {
		this.fileName = filename;
		FileWriter writer = new FileWriter(this.fileName + "_copy");
		Object [] keysSet = receivedBuffer.keySet().toArray();
		Arrays.sort(keysSet);
		String stringToWrite;
		for (Object key : keysSet) {
			stringToWrite = new String(receivedBuffer.get(key));
			writer.write(stringToWrite);
			writer.flush();
			
		}
	}
	
	public void handshake() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		TCPPacket firstPacket = new TCPPacket();
		TCPPacket secondPacket= new TCPPacket();
		while(true) {
         //receive SYN. The function recData() stalls until it receives the a packet                                                                 
        try {
        		firstPacket = recData();
	        	if (firstPacket.getFlags().contains("S")) {
				sendAcknowledgement("SA", firstPacket.seq+1, firstPacket.timsStamp); // Send ACK + SYN
				break;
	        	}
	        	else {
	        		System.out.println("First packet is not SYN, Handshake cannot proceed. Exiting");
	        		System.exit(1);
	        	}
        	} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("No incoming packets");
			Thread.sleep(1000);
		}
		}
        secondPacket = recData(); // receive the last ACK. No need to do anything with the packet
		
	}
	
	public TCPPacket recData() throws IOException {
		byte[] data_received = new byte[mtu];
        DatagramPacket packet = new DatagramPacket(data_received, data_received.length);
        socket.receive(packet);
        TCPPacket recPacket = new TCPPacket(0, 0, 0, 0, (short) 0, new byte[0], "");
        recPacket = recPacket.deserialize(packet.getData());
        if(recPacket.seq == 0){
            this.clientPort = packet.getPort();
            this.clientIP = packet.getAddress();
        }
        System.out.println("rcv " + System.nanoTime() / 1000000000 + " " + recPacket.getFlags() +
                " " + recPacket.getSeq() + " " + recPacket.getData().length + " " + recPacket.getAck());
        

        return recPacket;
    }
	
	public void sendAcknowledgement(String flag, int nextExpectedSeq, long prevTimeStamp) throws IOException {
        byte[] empty = new byte[0];
        int seqNum = 0;
        if(flag.contains("F")) seqNum = 1; // ACK message always has seq as 0, except when it is a FIN + ACK message
        TCPPacket send = new TCPPacket(seqNum, nextExpectedSeq, prevTimeStamp, 0, (short) 0, empty, flag);
        send.serialize();
        DatagramPacket packet = new DatagramPacket(send.serialize(), send.getLength() + 24, clientIP, clientPort);
        socket.send(packet);
        System.out.println("snd " + System.nanoTime() / 1000000000 + " " + send.getFlags() +
                " " + send.getSeq() + " " + send.getData().length + " " + send.getAck());
    }
}
