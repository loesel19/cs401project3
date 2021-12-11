import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

class Connection extends Thread
{
    Socket socket;
    ObjectInputStream inputStream;
    ObjectOutputStream outputStream;
    int peerPort;
    int peer_listen_port;
    int peerID;
    InetAddress peerIP;
    char FILE_VECTOR[];
    ArrayList<Connection> connectionList;
    boolean runFlag=true;

    //peer connection parameters
    boolean isClient = false; //is this a peer to peer connection
    Client recClient;
    boolean messHash = true; //if true the hash for the file will be messed up by the server

    public Connection(Socket socket, ArrayList<Connection> connectionList) throws IOException
    {
        this.connectionList=connectionList;
        this.socket=socket;
        this.outputStream=new ObjectOutputStream(socket.getOutputStream());
        this.inputStream=new ObjectInputStream(socket.getInputStream());
        this.peerIP=socket.getInetAddress();
        this.peerPort=socket.getPort();
        
    }

    /**
     * no args constructor, called for client Connection
     */
    public Connection(Socket clientSocket, Client client) throws IOException {
        socket = clientSocket;
        this.recClient = client;
        isClient = true;
        this.peerID = client.peerID;//
        this.inputStream = new ObjectInputStream(clientSocket.getInputStream());
        this.outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
        this.peerIP = clientSocket.getInetAddress();
        this.peerPort = clientSocket.getPort();
        System.out.println("Client connection opened");
    }

    @Override
    public void run() {
        //wait for register packet.
        if (!isClient) {
            Packet p = new Packet();

            try {
                p = (Packet) inputStream.readObject();
            } catch (Exception e) {
                System.out.println("Could not register client");
                return;
            }
            try {
                eventHandler(p);
            } catch (Exception e) {
                e.printStackTrace();
            }

            while (runFlag) {
                try {
                    //printConnections();
                    p = (Packet) inputStream.readObject();
                    System.out.println("received packet from " + p.sender);
                    eventHandler(p);
                    // p.printPacket();

                } catch (Exception e) {
                    break;
                }

            }
        }else{
            System.out.println("This is a clients connection");
            Packet p = new Packet();
            try{
                p = (Packet) inputStream.readObject();
                System.out.println("***received client packet");
                eventHandler(p);

            } catch (Exception e) {
                e.printStackTrace();
            }
            while(runFlag){
                try {
                    p = (Packet) inputStream.readObject();
                    eventHandler(p);
                } catch (Exception e) {
                    System.out.println("Connection has been closed");
                    break;
                }

            }
        }

    }

    public void printConnections()
    {
        System.out.println("---------------"); 
        for(int i = 0; i < connectionList.size(); i++) {  
            
            System.out.println("Peer ID :"+connectionList.get(i).peerID);
            System.out.println("FILE_VECTOR :"+String.valueOf(connectionList.get(i).FILE_VECTOR));
            System.out.println("---------------"); 
        }
    }

    public void send_packet_to_client(Packet p)
    {
        try
        { 
            outputStream.writeObject(p);
            System.out.println("Packet Sent ");
            //p.printPacket();
        }
        catch(Exception e){
            System.out.println ("Could not send packet! ");
        }
    }
    public void closeConnection()
    {
        try { 
                outputStream.close();
                inputStream.close();
                socket.close();
                System.out.println("Closed clientSocket");
            }
            catch (Exception e) { System.out.println("Couldn't close socket!");
            //e.printStackTrace(); 
                
        }
    }

    void send_quit_message()
    {
        Packet p = new Packet();
        p.event_type=6;
        send_packet_to_client(p);
    }

    void send_client_quit_message(){
        Packet p = new Packet();
        p.event_type = 5;
        send_packet_to_client(p);
    }

    public void eventHandler(Packet p) throws IOException, ClassNotFoundException, InterruptedException {
        int event_type = p.event_type;
        switch (event_type)
        {
            case 0: //client register
            clientRegister(p);break;
            
            case 1: // client is requesting a file 
            clientReqFile(p);break;

            case 5:
            clientWantsToQuit(p);break;
            
            case 3:
                System.out.println("case 3");
            clientGotFile(p);break; // To Do
            
            case 4:
             clientReqFileFromPeer(p);break;
        };
    }
public void clientReqFileFromPeer(Packet p) throws IOException, ClassNotFoundException, InterruptedException {
        System.out.println("Client " + p.sender + " is requesting file " + p.req_file_index);
        int findex = p.req_file_index;
        boolean fileReceived = false;

        while(!fileReceived) {
            int lastBysteIndex = 0; //holds the last byte sent of the file
            byte temp[] = generate_file(findex, 20000); //generate a file of length 20000 bytes
            Packet packet = null;
            try {
                for (int i = 0; i <= 20; i++) {
                    if (i != 20) {
                        packet = new Packet();
                        packet.sender = this.peerID;
                        packet.recipient = p.sender;
                        byte send[] = new byte[1000];
                        int sendIndex = 0;
                        while (sendIndex < p.data_block_size) {
                            send[sendIndex] = temp[lastBysteIndex];
                            sendIndex++;
                            lastBysteIndex++;
                        }
                        packet.DATA_BLOCK = send;
                        packet.event_type = 4;
                        outputStream.writeObject(packet);
                        outputStream.flush();
                        System.out.println("sent packet " + i + " to " + packet.recipient);
                        Thread.sleep(1000);
                    } else if (i == 20) {
                        System.out.println("awaiting ack...");
                        packet = (Packet) inputStream.readObject();
                        System.out.println("got response from sender");
                        if (packet.gotFile) {
                            System.out.println("Positive Ack Received!");
                            System.out.println("Closing peer connection....");
                            socket.close();
                            fileReceived = true;
                            break;
                        } else {
                            System.out.println("Negative Ack Received, retransmitting File");
                        }
                    }
                }
            }catch (SocketException e){
                System.out.println("Peer disconnected, stopping transmission");
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
}
    public void clientRegister(Packet p)
    {
        FILE_VECTOR=p.FILE_VECTOR;
        peer_listen_port=p.peer_listen_port;
        peerID=p.sender;
        connectionList.add(this);
        System.out.println("Client connected. Total Registered Clients : "+connectionList.size() );
        printConnections();
    }

    public void clientReqFile(Packet p)
    {
       System.out.println("Client "+p.sender+" is requesting file "+p.req_file_index);
       int findex = p.req_file_index;
       Packet packet = new Packet();
        packet.event_type=2;
        packet.req_file_index=findex;
//        if (!messHash)
        packet.fileHash = find_file_hash(generate_file(findex, 20000));
//        else{
//            while(true) {
//                String temp = find_file_hash(generate_file(findex, 20000));
//                temp = temp + "ff";
//                if (!temp.equals(find_file_hash(generate_file(findex,20000)))) {
//                    System.out.println("***Generated incorrect file hash");
//                    messHash = false;
//                    packet.fileHash = temp;
//                    break;
//                }
//            }
//        }
        System.out.println(packet.fileHash);

         for (int i=0;i<connectionList.size();i++)
        {
            if (connectionList.get(i).FILE_VECTOR[findex]=='1')
            {
                packet.peerID=connectionList.get(i).peerID;
                packet.peer_listen_port=connectionList.get(i).peer_listen_port;
                break;
                
            }
        }
        send_packet_to_client(packet);

        

    }

    public void clientWantsToQuit(Packet p)
    {
        //remove client from list. close thread.
        Connection clientConnection = searchForClientConnection(p.sender);
        if(clientConnection == null) return;
        System.out.println("Removing client "+p.sender);
        connectionList.remove(clientConnection);
        System.out.println("Total Registered Clients : "+connectionList.size() );
        closeConnection();

    }

    public Connection searchForClientConnection(int ID)
    {
        for (int i=0;i<connectionList.size();i++)
        {
            if (connectionList.get(i).peerID==ID)
                return connectionList.get(i);
        }
        return null;
    }

    public int searchForClient(int ID)
    {
        for (int i=0;i<connectionList.size();i++)
        {
            if (connectionList.get(i).peerID==ID)
                return i;
        }
        return -1;
    }
    
     public void clientGotFile(Packet p)
    {
        System.out.println("Updating FileVector index " + p.req_file_index + " for client " + p.peerID);
       // To implement
        int index = searchForClient(p.peerID);
        connectionList.get(index).FILE_VECTOR[p.req_file_index] = '1';

    }
    public byte[] generate_file(int findex, int length)
    {
        byte[] buf= new byte[length];
        Random r = new Random();
        r.setSeed(findex);
        r.nextBytes(buf);
        try{
            //System.out.println(SHAsum(buf));
        }
        catch (Exception e){System.out.println("SHA1 error!");}
        return buf;
    }

    public String find_file_hash(byte [] buf)
    {
        String h = "";
        try {
            h = SHAsum(buf);
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return h;
    }

    public String SHAsum(byte[] convertme) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return byteArray2Hex(md.digest(convertme));
    }

    private static String byteArray2Hex(byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

}
