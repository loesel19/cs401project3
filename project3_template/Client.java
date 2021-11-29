
import java.io.*;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.*;
import java.lang.*; 

public class Client {

     int serverPort;
     InetAddress ip=null; 
     Socket s;
     Socket peerSocket;
     ServerSocket ss; //listener socket for p2p connection
     ObjectOutputStream outputStream ;
     ObjectInputStream inputStream ;
     int peerID;
     int peer_listen_port;
     char FILE_VECTOR[];
    // To do , create each peers own ServerSocket listener to monitor for incoming peer requests. start a listener
    // thread in main() I used the ServerSocketHandler to handle both client-server and peer-to-peer listeners.
    // You can use a separate class. 90% of the code is repeated. For the individual connections, again you can
    // re-use the Connection class, and add some event handlers to process event codes that will be used to
    // distinguish between peer-to-peer or client-server communications, or create a separate class called
    // peerConnection. It is completely your choice.
    public Client(){


    }
    public static void main(String args[])
    {
        //TODO: Test with args at SVSU, delete next line
      //  args = new String[]{"-c", "project3_template/clientconfig1.txt"};
        Client client = new Client();
        boolean runClient=true;
        Scanner input = new Scanner(System.in);
        if (args.length==0 || args.length % 2 == 1){
            System.out.println("Parameters Required/Incorrect Format. See usage list");
            System.exit(0);
        }

        client.cmdLineParser(args);

        try
        { 
            if (client.ip==null)
                client.ip = InetAddress.getByName("localhost"); 

            client.s = new Socket(client.ip, client.serverPort); 
            client.outputStream = new ObjectOutputStream(client.s.getOutputStream());
            client.inputStream = new ObjectInputStream(client.s.getInputStream());
            client.ss = new ServerSocket(client.peer_listen_port);
            System.out.println("Connected to Server ..." +client.s); 

            Packet p = new Packet();
            p.event_type=0;
            p.sender=client.peerID;
            p.peer_listen_port=client.peer_listen_port;
            p.FILE_VECTOR = client.FILE_VECTOR;

            client.send_packet_to_server(p);

            System.out.println("Packet Sent");
            
            Thread r = new PacketHandler(client);
            r.start();

            //create a new Server socket to listen for incoming client connections
            try {
                //start a new socket handler
                System.out.println("listen port: " + client.peer_listen_port);
                ServerSocketHandler clientHandler = new ServerSocketHandler(client);
                clientHandler.start();
            }catch (Exception e){
                e.printStackTrace();
        }



            
            while (runClient){
                
                System.out.println ("Enter query");
                char cmd=input.next().charAt(0);
                switch(cmd)
                {
                    case 'q':
                    System.out.println("Getting ready to quit ..." +client.s); 
                    client.send_quit_to_server();
                    runClient=false;
                    break;

                    case 'f':
                    System.out.println("Enter the file index you want ");
                    int findex = input.nextInt();
                    client.send_req_for_file(findex);

                    break;

                    default:
                    System.out.println("Command not recognized. Try again ");

                }
                //Packet p = (Packet) inputStream.readObject();
                //p.printPacket();
            }
        }
        catch(Exception e){ 
            e.printStackTrace(); 
        }

    }

    void send_packet_to_server(Packet p)
    {
        try
        { 
            outputStream.writeObject(p);
        }
        catch(Exception e){
            System.out.println ("Could not send packet! ");
        }
    }

    void send_quit_to_server()
    {
        Packet p = new Packet();
        p.sender=peerID;
        p.event_type=5;
        p.port_number=peer_listen_port;
        send_packet_to_server(p);
        
    }

    public void cmdLineParser(String args[])
    {
        int i;
        for (i=0;i<args.length;i+=2)
        {
            String option=args[i];
            switch (option)
            {
                case "-c": //config_file
                File file = new File(args[i+1]);
                read_config_from_file(file);
                break;

                case "-i": //my ID
                peerID=Integer.parseInt(args[i+1]);
                break;

                case "-p": // my listen port
                peer_listen_port=Integer.parseInt(args[i+1]);
                break;

                case "-s": //server port
                serverPort=Integer.parseInt(args[i+1]);
                break;

                case "-n":
                try{ip = InetAddress.getByName(args[i+1]);} catch(Exception e){
                System.out.println ("Could not resolve hostname! " +args[i+1]);}
                break;

                default: System.out.println("Unknown flag "+args[i]);

            }

        }
    }

    public void read_config_from_file(File file )
    {
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String opt[]= line.split(" ",2);
                switch(opt[0])
                {
                    case "SERVERPORT": serverPort=Integer.parseInt(opt[1]);break;
                    case "CLIENTID": peerID=Integer.parseInt(opt[1]);break;
                    case "MYPORT": peer_listen_port=Integer.parseInt(opt[1]);break;
                    case "FILE_VECTOR": FILE_VECTOR = opt[1].toCharArray();break;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public void send_req_for_file(int findex)
    {
        if (FILE_VECTOR[findex] =='1'){
            System.out.println("I already have this file block!");
            return;
        }
        System.out.println(" I don't have this file. Let me contact server...");
        //request file from server
        Packet p = new Packet();
        p.sender=peerID;
        p.event_type=1;
        p.peer_listen_port=peer_listen_port;
        p.req_file_index=findex;
        send_packet_to_server(p);
        //disconnect();

    }

    void disconnect()
    {
        try { 
                outputStream.close();
                inputStream.close();
                s.close();
                System.out.println("Closed Socket");
            }
        catch (Exception e) { System.out.println("Couldn't close socket!");}
    }
}

class PacketHandler extends Thread
{
    Client client;

    public PacketHandler(Client client)
    {
        this.client=client;
    }

    public void run()
    {
        Packet p;

        while(true){
        try { 
            p = (Packet) client.inputStream.readObject();
            process_packet_from_server(p);
        }
        catch (Exception e) { 
             //e.printStackTrace();
             break;
        }
    }

    }

    void process_packet_from_server(Packet p) throws IOException, ClassNotFoundException {
     int e = p.event_type;

     switch (e)
     {
        case 2: //server reply for req. file
        if (p.peerID==-1)
            System.out.println("Server says that no client has file "+p.req_file_index);
        else{
            System.out.println("Server says that peer "+p.peerID+" on listening port "+p.peer_listen_port+" has file "+p.req_file_index);
            System.out.println(p.fileHash);
            PeerToPeerHandler(p.peerIP,p.peer_listen_port,p.req_file_index,p.req_file_index, p.fileHash); // TO DO
            }
        break;
         case 3:
             client.FILE_VECTOR = p.FILE_VECTOR;

        case 6: //server wants to quit. I should too.
            System.out.println("Server wants to quit. I should too! ");
            client.disconnect();
           System.exit(0);

     }

    }
    void process_packet_from_client(Packet p, int i){
        switch (p.event_type){
            case(4):
                System.out.println("received packet " + i + " from " + p.sender);
        }
    }
    
    void PeerToPeerHandler(InetAddress remotePeerIP, int remotePortNum, int remotePeerID, int findex, String fileHash) throws IOException, ClassNotFoundException {
        // To implement.

System.out.println("inside peerToPeerHandler");
        // while file not received correctly
        client.peerSocket = new Socket(remotePeerIP, remotePortNum);
        System.out.println("created socket to peer " + client.peerSocket);
        //now we ask the client for the file we want
        Packet p = makeRequestPacket(remotePeerIP, remotePortNum, remotePeerID, findex);
        // request_file_from_peer
        ObjectOutputStream os = new ObjectOutputStream(client.peerSocket.getOutputStream());
        ObjectInputStream is= new ObjectInputStream(client.peerSocket.getInputStream());
        os.writeObject(p);
        os.flush();
        System.out.println("Wrote out packet");
        // receive_file_from_peer
        Packet pack = null;
        for (int i = 0; i < 20; i++) {
            pack = (Packet) is.readObject();
            process_packet_from_client(pack, i);
            // verify file_hash
            if (i == 19 && fileHash.equals(find_file_hash(generate_file(findex, 64)))) {
                // if correct, send positve ack, break
                Packet res = new Packet();
                res.sender = pack.recipient;
                res.recipient = pack.sender;
                res.peerIP = client.peerSocket.getInetAddress();
                res.event_type = 4;
                res.gotFile = true;
                os.writeObject(res);
                System.out.println("got file, sent positive ACK");
                //now update file vector so we dont ask for this file again
                client.FILE_VECTOR[findex] = '1';
                break;
            } else if (i == 19 && !(fileHash.equals(find_file_hash(generate_file(findex,64))))){
                // if incorrect, send negative ack, loop back
                Packet res = new Packet();
                res.sender = client.peerID;
                res.recipient = pack.sender;
                res.peerIP = client.peerSocket.getInetAddress();
                res.event_type = 4;
                res.gotFile = false;
                os.writeObject(res);
                System.out.println("incorrect File Hash, sending negative ACK");
                i = -1;
            }
        }


            
        //once, file has been received, send update file request to server.
        
    }
public Packet makeRequestPacket(InetAddress remotePeerIP, int remotePortNum, int remotePeerID, int findex){
    Packet p = new Packet();
    p.event_type = 4;
    p.sender = client.peerID;
    p.recipient = remotePeerID;
    p.peerIP = client.ip;
    p.peer_listen_port = client.peer_listen_port;
    p.FILE_VECTOR = client.FILE_VECTOR;
    p.req_file_index = findex;
    return p;
}
    public byte[] generate_file(int findex, int length)
    {
        byte[] buf= new byte[length];
        Random r = new Random();
        r.setSeed(findex);
        r.nextBytes(buf);
        try{
            System.out.println(SHAsum(buf));
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

    public String SHAsum(byte[] convertme) throws NoSuchAlgorithmException{
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
