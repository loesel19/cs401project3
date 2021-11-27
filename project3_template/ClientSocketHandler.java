import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

public class ClientSocketHandler extends Thread{
    Client client;
    int hostID;
    InetAddress hostAdd;
    int hostPort;

    public ClientSocketHandler(Client client, InetAddress hostAdd, int hostPort, int hostID){
        this.client = client;
        this.hostAdd = hostAdd;
        this.hostPort = hostPort;
        this.hostID = hostID;
    }
    public void run(){
        Socket hostSocket;
        while(true){
            hostSocket = null;
            try {
                hostSocket = client.clientListener.accept();
                System.out.println("connecting with host...");
                System.out.println("port: " + hostSocket.getPort());
                System.out.println("IP: " + hostSocket.getInetAddress().toString());
                //TODO: make new packet to send/ request file from client/host connection
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
