import java.math.BigInteger;
import java.nio.ByteBuffer;

public class TCPPacket {
	public int seq;
	public int ack;
	public long timsStamp;
	public int length; //use the last three bits for flags: S = SYN, A = ACK, F = FIN
	public short checksum;
	public byte[] data;
	
	TCPPacket(int seq, int ack, long timeStamp, int length, short checksum, byte[] data, String flag){
		this.seq = seq;
		this.ack = ack;
		this.timsStamp = timeStamp;
		this.length = length;
		this.checksum = checksum;
		this.data = data;
		setFlags(flag);
	}
	
	TCPPacket(){
		this.seq = 0;
		this.ack = 0;
		this.timsStamp = 0;
		this.length = 0;
		this.checksum = 0;
		this.data = null;
	}

	public void setFlags(String flag) {
		int len = Integer.reverse(this.length);
		//int len = this.length;
		if (flag.contains("A")) len += 1;
		if (flag.contains("F")) len += 2;
		if (flag.contains("S")) len += 4;
		this.length = Integer.reverse(len);
		//this.length = len;
	}
	
	public String getFlags() {
		int len = Integer.reverse(this.length);
		//int len = this.length;
		int relBits = len % 8; //get the relevant bits
		String flag = "";
		//System.out.println("Relevant Bits : "+relBits);
		switch(relBits){
        case 0:
            flag = "---D"; // flag for data
            break;
        case 1:
            flag = "-A--"; // flag for ACK
            break;
        case 2:
            flag = "--F-"; // flag for FIN
            break;
        case 3:
            flag = "-AF-"; // flag for ACK + FIN
            break;
        case 4:
            flag = "S---"; //FLAG for SYN
            break;
        case 5:
            flag = "SA--"; // FLAG for SYN + ACK
            break;
    }
		
		
		return flag;
		
	}
	
	
	/**
	 * @return the seq
	 */
	public int getSeq() {
		return seq;
	}

	/**
	 * @param seq the seq to set
	 */
	public void setSeq(int seq) {
		this.seq = seq;
	}

	/**
	 * @return the ack
	 */
	public int getAck() {
		return ack;
	}

	/**
	 * @param ack the ack to set
	 */
	public void setAck(int ack) {
		this.ack = ack;
	}

	/**
	 * @return the timsStamp
	 */
	public long getTimsStamp() {
		return timsStamp;
	}

	/**
	 * @param timsStamp the timsStamp to set
	 */
	public void setTimsStamp(long timsStamp) {
		this.timsStamp = timsStamp;
	}

	/**
	 * @return the length
	 */
	public int getLength() {
		int rShift = this.length << 3;
        int lShift = rShift >> 3;
        return lShift;
	}

	/**
	 * @param length the length to set
	 */
	public void setLength(int length) {
		this.length = length;
	}

	/**
	 * @return the checksum
	 */
	public short getChecksum() {
		return checksum;
	}

	/**
	 * @param checksum the checksum to set
	 */
	public void setChecksum(short checksum) {
		this.checksum = checksum;
	}

	/**
	 * @return the data
	 */
	public byte[] getData() {
		return this.data;
	}

	/**
	 * @param data the data to set
	 */
	public void setData(byte[] data) {
		this.data = data;
	}
	
    public byte[] serialize(){
        byte[] transUnit = new byte[this.getLength() + 24]; // add 24 for the header
        ByteBuffer byteBuffer = ByteBuffer.wrap(transUnit);
        byteBuffer.putInt(this.seq);                     //index: 0 - 3
        byteBuffer.putInt(this.ack);               //index: 4 - 7
        byteBuffer.putLong(this.timsStamp);                   //index: 8 - 15
        //byteBuffer.putInt(Integer.reverse(this.length));      //index: 16 - 19
        byteBuffer.putInt((this.length));
        short Zero = 0;                            //concat 16 bits of zeros
        byteBuffer.putShort(Zero);                         //index: 20 - 21
        this.checksum = computeChecksum(this.seq);
        byteBuffer.putShort(this.checksum);                   //index: 22 - 23
        byteBuffer.put(this.data);                            //index: 24

        return transUnit;
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

    public TCPPacket deserialize(byte[] recUnit) {
    	
    	//TCPPacket packet = new TCPPacket();
    	//System.out.println("In the deserialize function");
    	ByteBuffer byteBuffer = ByteBuffer.wrap(recUnit, 0, recUnit.length);
    		if (byteBuffer == null) {
    			System.out.println("No data in buffer");
    			System.exit(0);
    		}
        this.seq = byteBuffer.getInt();
        this.ack= byteBuffer.getInt();
        this.timsStamp= byteBuffer.getLong();
        this.length = (byteBuffer.getInt());
        //this.length = Integer.reverse(byteBuffer.getInt());
        short zero = byteBuffer.getShort();
        assert(zero == 0);
        this.checksum = byteBuffer.getShort();
        this.data = new byte[this.getLength()];
        //System.out.println("RecUnit Length : "+ recUnit.length + " data length : "+ this.getLength());
        try {
	        for(int i = 0; i < this.getLength(); i++) {
	            this.data[i] = byteBuffer.get();
	            String str = new String(this.data);
	        }
        }
        catch (Exception e) {
        		System.out.println("RecUnit Length : "+ recUnit.length + " data length : "+ this.getLength());
        		e.printStackTrace();
        }
        //TCPPacket packet = new TCPPacket(sequence, acknowledgment, time, len, chcksum, Data,"" );
        return this;
    }
    
}

