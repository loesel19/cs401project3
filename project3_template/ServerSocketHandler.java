
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

class ServerSocketHandler extends Thread
{

    Server s;
    ArrayList<Connection> connectionList;

    //p2p connection
    Client client;
    boolean isClient = false;

    public ServerSocketHandler(Server s, ArrayList<Connection> connectionList){
        this.s=s;
        this.connectionList=connectionList;
    }

    /**
     * this will be the constructor to open a server socket for a client, takes the client as input
     * @param client
     */
    public ServerSocketHandler(Client client){
        System.out.println("clients constructor for ServerSocketHandler");
        this.client = client;
        isClient = true;
    }

    public void run() {
        Socket clientSocket;
        if (isClient) {
            this.connectionList = new ArrayList<>();
            System.out.println("client " + client.peerID + " opened up a listening socket");
            while (true) {
                clientSocket = null;
                try{
                    clientSocket = client.ss.accept();
                    System.out.println("Connecting with client at port : " + clientSocket.getPort());
                    System.out.println("IP : " + clientSocket.getInetAddress().toString());
                    //start the connection
                    Connection conn = new Connection(clientSocket, client);
                    connectionList.add(conn);
                    conn.start();

                } catch (SocketException e) {
                    System.out.println("Shutting down Client....");
                    quit();
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            while (true) {
                clientSocket = null;
                try {
                    clientSocket = s.listener.accept();
                    System.out.println("A new client is connecting.. : " + clientSocket);
                    System.out.println("Port : " + clientSocket.getPort());
                    System.out.println("IP : " + clientSocket.getInetAddress().toString());
                    Connection conn = new Connection(clientSocket, s.connectionList);
                    //connectionList.add(conn);
                    conn.start();

                } catch (SocketException e) {
                    System.out.println("Shutting down Server....");
                    // send a message to all clients that I want to quit.
                    for (Connection c : connectionList) {
                        c.send_quit_message();
                        c.closeConnection();
                    }
                    connectionList.clear();
                    break;

                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    public void quit() {
        // send a message to all peers that I want to quit.
        for (Connection c : connectionList) {
            if(c == null) return;
            c.send_client_quit_message();
            c.closeConnection();
        }
        connectionList.clear();
    }
}