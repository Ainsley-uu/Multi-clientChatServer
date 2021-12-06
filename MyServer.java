// A server that uses multithreading to handle 
// any number of clients.
import java.io.*;
import java.net.*;
import java.util.Queue;
import java.util.ArrayList;
import java.util.LinkedList;

interface ClientListener{
    void msgRcvd(String msg);
    void clientQuit(Client c);
}

class Client extends Thread{
    private Socket socket;
    InetAddress addr;
    String hostName;
    String ip;
    private PrintWriter out;
    private BufferedReader in;
    private boolean running = true;

    public Client( Socket socket ){
        this.socket = socket;
        addr = socket.getInetAddress();
        hostName = addr.getHostName();
        ip = addr.getHostAddress();
        try {
            in = 
                new BufferedReader(
                new InputStreamReader(
                    socket.getInputStream()));
            // Enable auto-flush:
            out = 
                new PrintWriter(
                new BufferedWriter(
                    new OutputStreamWriter(
                    socket.getOutputStream())), true);
        } catch (IOException e){}
    }

    public void stopRx(){
        running = false;
    }

    public void run() {
        try {
            while (running) {  
                String str = in.readLine();
                String msg = "hostName:" + hostName + " ip" + ip + " send message: " + str + "\n"; 
                notifyMsgRcvd(msg);
                if (str.equals("END")) break;
                // System.out.println("Echoing: " + str);
                // out.println(str);
            }
            System.out.println("closing...");
        } catch (IOException e) {
        } finally {
            notifyQuit();
            try {
                System.out.println("close");
                socket.close();
            } catch(IOException e) {}
        }
    }

    public void startListen(){
        start();
    }

    public void sendMsg(String line){
        System.out.println("send msg to client");
        out.println(line);
    } 
    
    ArrayList<ClientListener> lstListener = new ArrayList<ClientListener>();
    public synchronized void addClientListener( ClientListener l ){
        lstListener.add(l);
    }

    synchronized void notifyMsgRcvd( String msg ){
        for( ClientListener ml : lstListener ){
            ml.msgRcvd(msg);
        }
    }

    synchronized void notifyQuit(){
        for( ClientListener ml: lstListener ){
            ml.clientQuit(this);
        }
    }
}

public class MyServer implements ClientListener {  
    static final int PORT = 8081;
    LinkedList<Client> lstClient = new LinkedList<Client>();
    private MyServer(){}
    SendMsg send = new SendMsg();
    // private static MyServer theServer = new MyServer();
    private Queue<String> queue = new LinkedList<String>();

    class SendMsg extends Thread{
        synchronized void sendMsg(String msg){
            for( Client c : lstClient )
                c.sendMsg(msg);
        }

        public synchronized void run(){
            while(true){
                if(queue.isEmpty()){
                    try{
                        this.wait();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                String msg = queue.poll();
                sendMsg(msg);
            }
        }
    }

    synchronized void sendMsg(String msg){
        for( Client t : lstClient ){
            t.sendMsg(msg);
        }
    }

    public synchronized void addClient(Client c){
        lstClient.add(c);
    }

    public void clientQuit(Client c){
        lstClient.remove(c);
        c.stopRx();
    }

    public void msgRcvd(String msg){
        queue.offer(msg);
        synchronized(send){
            send.notify();
        }
    }

    void go() throws IOException{
        ServerSocket s = new ServerSocket(PORT);
        System.out.println("Server Started");
        send.start();
        try{
            while(true) {
                Socket socket = s.accept();
                System.out.println(socket);
                Client c = new Client(socket);
                c.addClientListener(this);
                lstClient.add(c);
                c.startListen();
            }
        } finally {
            s.close();
        }
    }

    public static void main(String[] args) throws IOException {
        MyServer theServer = new MyServer();
        theServer.go();
    } 
} ///:~