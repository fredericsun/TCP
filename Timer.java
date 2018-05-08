public class Timer extends Thread {
    public long cur_time;
    public int nextByteToSend;
    public static long TIMEOUT = 4000;
    public Timer(long cur_time, int nextByteToSend) {
        this.cur_time = cur_time;
        this.nextByteToSend = nextByteToSend;
        
    }
    public void run() {
    		while(true) {
	        if (System.currentTimeMillis() - cur_time < TIMEOUT) {
	            try {
					sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	        else {
	        	Client.isRetransmitted = true;
	        }
    		}
    }
}
