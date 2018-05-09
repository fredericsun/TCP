import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Timer;
import java.util.TimerTask;


public class TCPClient {
	private DatagramSocket socket; 
	private int port_num; 
	private InetAddress inet_remote_ip ;
	private int remote_port;
	private byte[] dataBuffer;
	private int mtu;
	private int sws;
	private String fileName;
	
	public static Integer swsPackets;
	
	
	boolean handShook = false;
	boolean connectionTerminated = false;
	
	int lastSeq = 0;
	
    //private Map<Integer, Long> sequence_timeout_map;
	private ArrayList<TCPPacket> dataPackets;
    private ConcurrentHashMap<Integer, TCPPacket> packetsNotAcked;
    private ConcurrentHashMap<Integer, Integer> retransmitPackets; // map to keep track of retransmission of packets
    ReentrantLock lock;
	
	TCPClient(DatagramSocket socket, int port_num, InetAddress inet_remote_ip , int remote_port,
            byte[] buffer, int mtu, int sws, String fileName){
		this.socket = socket;
		this.port_num = port_num;
		this.inet_remote_ip = inet_remote_ip;
		this.remote_port = remote_port;
		this.dataBuffer = buffer;
		this.mtu = mtu;
		this.sws = sws;
		this.fileName = fileName;
		dataPackets = new ArrayList<TCPPacket>();
		initialize();
	}
	
	private void initialize() {
		
		this.swsPackets = (int) Math.ceil((double)this.sws/(double)(mtu - 24));// At a given time, number of packets that can be transmitted
		timeOutMap = new ConcurrentHashMap<Integer, Long>();
		retransmitPackets = new ConcurrentHashMap<Integer, Integer>();
		packetsNotAcked = new ConcurrentHashMap<Integer, TCPPacket>();
		ackedPackets = new ArrayList<Integer>();
        int seqNum = 1; 
        int bytesLoaded = 0;
        int numSegments = (int)Math.ceil((double)dataBuffer.length/(double)(mtu - 24));
        
        for(int segCount = 0; segCount < numSegments; segCount++){
            byte[] dataToBeSent;
            if(segCount == numSegments - 1) //corner case
                dataToBeSent = new byte[dataBuffer.length - 1 - bytesLoaded];
            else
                dataToBeSent = new byte[mtu-24]; // 24 bytes reserved for the header
            int dataLoadedForSeg;
            for(dataLoadedForSeg = 0; dataLoadedForSeg < (mtu-24) && bytesLoaded < dataBuffer.length-1; dataLoadedForSeg++) {
                dataToBeSent[dataLoadedForSeg] = dataBuffer[bytesLoaded];
                bytesLoaded++;
            }
            dataPackets.add(new TCPPacket(seqNum, 1, System.nanoTime(), dataLoadedForSeg, (short) 0, dataToBeSent, "D"));
            dataPackets.get(segCount).serialize(); //computes the checksum
            seqNum += dataLoadedForSeg;
        }
	}

	public void handshake() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		
		
		Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
               
				try {
					 //Receive SYNACK
					TCPPacket ack = receiveACKPacket();// no need for a while loop here, receive stalls until it gets the packet
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				try {
					//Send the final ACK in handshake
					sendEmptyPacket("A",  1);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("HandShake finished");
                handShook = true;
            }

        });

        thread1.start();
        while(!handShook) {
            sendEmptyPacket("S", 0); // send the SIN packet until you get a response
            Thread.sleep(1000);
        	}
        
        
	}

	private TCPPacket receiveACKPacket() throws IOException {
		// TODO Auto-generated method stub
		byte[] temp = new byte[mtu];
        DatagramPacket ackpacket = new DatagramPacket(temp, temp.length);
        socket.receive(ackpacket);
        TCPPacket ACKPacket = new TCPPacket(0, 0, 0, 0, (short) 0, null, "E");
        ACKPacket = ACKPacket.deserialize(ackpacket.getData());
        System.out.println("rcv " + System.nanoTime() / 1000000000 + " " + ACKPacket.getFlags() +
                " " + ACKPacket.getSeq() + " " + ACKPacket.getData().length + " " + ACKPacket.getAck());
        return ACKPacket;
	}
	
	private void sendEmptyPacket(String flag, int ack) throws IOException {
		byte[] emptyBuffer = new byte[0];
        TCPPacket segment = new TCPPacket(0, ack, System.nanoTime(), 0, (short) 0, emptyBuffer, flag);
        segment.serialize(); 
        DatagramPacket packet = new DatagramPacket(segment.serialize(), 0, segment.getLength() + 24, this.inet_remote_ip, this.remote_port);
        //DatagramPacket packet = new DatagramPacket(packet_data, packet_data.length);
        socket.send(packet);
        System.out.println("snd " + System.nanoTime() / 1000000000 + " " + segment.getFlags() +
                " " + segment.getSeq() + " " + segment.getData().length + " " + segment.getAck());
	}
		
	
	public void sendData() throws IOException, InterruptedException {
		Thread sendDataThread = new Thread(new Runnable() {
			@Override
			public void run() {
				TCPPacket tempPacket;
				int packetsSent = 0;
				lock = new ReentrantLock(); 
				while(packetsSent < dataPackets.size()) {
					lock.lock(); // need to use locks because .size() of concurrentHashMap is not very accurate unless the full things is locked.
					if(packetsNotAcked.size() < swsPackets) { //can be changed to sws for testing
						tempPacket = dataPackets.get(packetsSent);
						System.out.println("Flag of the packet : " + tempPacket.getFlags());
						dataLength = dataLength + tempPacket.getData().length;
						numPackets = numPackets + 1;
						tempPacket.setTimsStamp(System.nanoTime());
						try {
							sendDataPacket(tempPacket);
							packetsNotAcked.put(tempPacket.getSeq(),tempPacket);
							packetsSent++;
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						finally {
							lock.unlock();
						}
					}
				}
				lastSeq = dataPackets.get(packetsSent -1).getSeq(); // for the final packet which contains the name of the file
			}
		});
		
		sendDataThread.start();
		
		Timer scheduler = new Timer(true) ;
		scheduler.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				if(packetsNotAcked.size() > 0) {
					try {
						timeOutChecker(packetsNotAcked);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}, 0, 1);
		
		int totalAcks = 0;
		while(ackedPackets.size() < dataPackets.size()) {
			try {
				TCPPacket recPacket = receiveACKPacket();
				totalAcks += 1;
				updateTimeOut(recPacket);
				timeOutMap.remove(recPacket.getAck() - 1);
				lock.lock();
				packetsNotAcked.remove(recPacket.getAck() - 1);
				ackedPackets.add(recPacket.getAck());
			}
			catch(IOException e) {
				e.printStackTrace();
			}
			finally {
				lock.unlock();
			}
		}
		sendDataThread.join();
		numDuplicateAck = totalAcks - ackedPackets.size();
		
		
		
	}

	private ArrayList<Integer> ackedPackets;
	public void timeOutChecker(ConcurrentHashMap<Integer, TCPPacket> packetsNotAcked) throws IOException {
		for(Integer seq: timeOutMap.keySet()) {
			//If the packet has not been ACKED and the time runs out, send it again
			if(packetsNotAcked.containsKey(seq) && timeOutMap.get(seq) < System.nanoTime()) {
				packetsNotAcked.get(seq).setTimsStamp(System.nanoTime());
				sendDataPacket(packetsNotAcked.get(seq));
				numDiscardedPacketsSeq++;
				numRetransmission++;
			}
		}
		
	}
	
	private void sendDataPacket(TCPPacket packet) throws IOException {
		packet.setTimsStamp(System.nanoTime());
        packet.serialize();
        DatagramPacket pack = new DatagramPacket(packet.serialize(), 0, packet.getLength() + 24, this.inet_remote_ip, this.remote_port);
        timeOutMap.put(packet.getSeq(), this.timeout + System.nanoTime());
        if(retransmitPackets.containsKey(packet.getSeq())) {
        		if(retransmitPackets.get(packet.getSeq()) > 16) {
        			System.out.println("Number of retransmission exceeded the prescribed number");
        			System.exit(1);
        		}
        		retransmitPackets.put(packet.getSeq(), retransmitPackets.get(packet.getSeq()) + 1);
        }
        else {
        	retransmitPackets.put(packet.getSeq(), 1);
        }
        socket.send(pack);
        System.out.println("snd " + System.nanoTime() / 1000000000 + " " + packet.getFlags() +
                " " + packet.getSeq() + " " + packet.getData().length + " " + packet.getAck());

	}
	public void terminateConnection() throws IOException {
		// TODO Auto-generated method stub
		Thread receiveTeardown = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                        //Receive ACK
                			receiveACKPacket();

                        //Receive FINACK
                        TCPPacket finalAck = receiveACKPacket();

                        //Send ack
                        sendEmptyPacket("A", finalAck.getSeq() + 1);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                connectionTerminated = true;
            }
        });


        receiveTeardown.start();

        while(!connectionTerminated) {
            //Send FIN
            byte[] filename = this.fileName.getBytes();
            TCPPacket finalSeg = new TCPPacket(lastSeq + filename.length, 0, System.nanoTime(), this.fileName.length(), (short) 0, filename, "F");
            sendDataPacket(finalSeg);
            System.out.println("\nAmount of Data Transferred/Received: " + dataLength);
            System.out.println("No of Packets Sent/Received: " + numPackets);
            System.out.println("No of Packets Discarded (Out Of Seq): " + numDiscardedPacketsSeq);
            System.out.println("No of Packets Discarded (Checksum): " + numDiscardedPacketsCheckSum);
            System.out.println("No of Retransmissions: " + numRetransmission);
            System.out.println("No of Duplicate Acknowledgements: " + numDuplicateAck);
            System.exit(0);
		
        }
	}
	
	private long ERTT;
	private long EDEV;
	private long SRTT;
	private long SDEV;
	public long timeout;
	
	public static double a = 0.875;
	public static double b = 0.75;
	
	public ConcurrentHashMap<Integer, Long> timeOutMap;
	
	public void updateTimeOut(TCPPacket packet) {
		long time = packet.getTimsStamp();
		int seqNum = packet.getSeq() - 1;
		long currTime = System.nanoTime();
		if (seqNum == 0) {
			this.ERTT = time - currTime;
			this.EDEV = 0;
			this.timeout = 2*this.ERTT;
		}
		else {
			this.SRTT = time - currTime;
			this.SDEV = Math.abs(SRTT - ERTT);
			this.ERTT =(long) (a*this.ERTT) + (long)(1-a)*this.SRTT;
			this.EDEV = (long) (b*this.EDEV )+ (long) (1-b)*SDEV;
			this.timeout = this.ERTT + 4*this.EDEV;		
		}
	}
	
	
	// variables for printing at the end
	public int dataLength = 0;
	public int numPackets = 0;
	public int numDiscardedPacketsSeq = 0;
	public int numDiscardedPacketsCheckSum = 0;
	public int numRetransmission = 0;
	public int numDuplicateAck = 0;
}
