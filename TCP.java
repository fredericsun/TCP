import java.net.*;
import java.io.*;


public class TCP {
    public static void main(String args []) {
        if (args.length == 12) {
            if (!args[0].equals("-p") || !args[2].equals("-s") || !args[4].equals("-a")
                    || !args[6].equals("-f") || !args[8].equals("-m") || !args[10].equals("-c")) {
                System.out.println("Error: missing or additional arguments");
            }
            else {
                // initiator tcpend
                int port_num = Integer.parseInt((args[1]));
                String remote_ip = args[3];
                int remote_port = Integer.parseInt(args[5]);
                String file_name = args[7];
                int mtu = Integer.parseInt(args[9]);
                int sws = Integer.parseInt(args[11]);
                try {
                    DatagramSocket socket = new DatagramSocket(port_num);
                    InetAddress inet_remote_ip = InetAddress.getByName(remote_ip);
                    // process file
                    File file = new File(file_name);
                    FileInputStream data = new FileInputStream(file);
                    byte[] buffer = new byte[(int)file.length()];
                    data.read(buffer, 0, (int) file.length());
                    Client client = new Client(socket, port_num, inet_remote_ip, remote_port, buffer, mtu, sws);
                } catch (IOException e) {
                    System.out.println("Fail to create a socket");
                }
            }
        }
        else if (args.length == 6) {
            if (!args[0].equals("-p") || !args[2].equals("-m") || !args[4].equals("-c")) {
                System.out.println("Error: missing or additional arguments");
            } else {
                // remote end
                int port_num = Integer.parseInt((args[1]));
                int mtu = Integer.parseInt(args[3]);
                int sws = Integer.parseInt(args[5]);
                try {
                    DatagramSocket socket = new DatagramSocket(port_num);
                    Server server = new Server(port_num, mtu, sws, socket);
                }
                catch(IOException e) {
                    System.out.println("Fail to create a socket");
                }

            }
        }
    }
}

