public class Timer extends Thread {
    public long cur_time;
    public int cur_seq;
    public int last_sent;
    public int last_ack;
    public Timer(long cur_time, int cur_seq, int last_sent, int last_ack) {
        this.cur_time = cur_time;
        this.cur_seq = cur_seq;
        this.last_sent = last_sent;
        this.last_ack = last_ack;
    }
    public void run() {
        while (System.nanoTime() - cur_time < Client.timeout) {
            if (Client.lastacked >= last_sent + 1) {
                break;
            }
        }
        if (System.nanoTime() - cur_time >= Client.timeout) {
            Client.nextbytetosend = cur_seq;
            Client.lastsent = last_sent;
            Client.counter = 0;
            Client.retransmission++;
        }
    }
}
