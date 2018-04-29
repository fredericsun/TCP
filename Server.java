public class Server {
    public int port_num;
    public int mtu;
    public int sws;

    public Server(int port_num, int mtu, int sws) {

        this.port_num = port_num;
        this.mtu = mtu;
        this.sws = sws;
    }

}

class Server_InThread extends Thread {
    public void run() {

    }
}

class Server_OutThread extends Thread {
    public void run() {

    }
}

