public class Timer extends Thread {
    public long cur_time;
    public int nextByteToSend;
    public static long TIMEOUT = 4000;
    public Timer(long cur_time, int nextByteToSend) {
        this.cur_time = cur_time;
        this.nextByteToSend = nextByteToSend;
        
    }
    public void run() {
    	
//        if (System.currentTimeMillis() - cur_time < TIMEOUT) {
//            sleep(1000);
//        }
//        if (System.nanoTime() - cur_time >= Client.timeout) {
////            Client.nextbytetosend = cur_seq;
////            Client.lastsent = last_sent;
////            Client.counter = 0;
////            Client.retransmission++;
//        }
    }
}
