import client.*;
import org.w3c.dom.ls.LSOutput;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.security.MessageDigestSpi;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
* This is just some example code to show you how to interact 
* with the server using the provided client and two queues.
* Feel free to modify this code in any way you like!
*/

public class MyProtocol{

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 6900;
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/

    private HashMap<Integer, MyRoute> forwardingTable = new HashMap<>();

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    public void TUI(){
        System.out.println(
                "COMMANDS:" + "\n" +
                        "!help              Print the list of commands again." + "\n" +
                        "!exit              Exit the program and any chatroom you may be in. \n" +
                        "Any other input will be sent as a message through the broadcast for others to see.");
    }

//    public ByteBuffer messageHeader(ByteBuffer payload){
//        ByteBuffer temp = ByteBuffer.allocate(256);
//
//    }

    public MyProtocol(String server_ip, int server_port, int frequency) throws InterruptedException {
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        Client client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        Scanner scan = new Scanner(System.in);
        System.out.println("Enter your address: ");
        int address = scan.nextInt();
        client.setAddress(address);

        // Routing and addressing stage
        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        boolean complete = false;

        while(!complete){
            // flooding for routing
            // packet format: src, dst,
            ByteBuffer forwardingPacket = ByteBuffer.allocate(1);
            forwardingPacket.put(((byte) address)); // should contain
            //
            while(!(System.currentTimeMillis() % 4 == address)){
            }
            sendingQueue.put(new Message(MessageType.DATA_SHORT, forwardingPacket));
            long startTime = System.currentTimeMillis();                        //needs endtime FOR TIME OUT LATER
            while (true){
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.DATA_SHORT) {
                        //m.getData().getInt()
                        // forwarding table construction
                    }
                    byte pkt = m.getData().get();
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: " + e);
                }
            }

            if(forwardingTable.size() == 4){
                complete = true;
            }
        }

        TUI();
        // handle sending from stdin from this thread.
        try{
            ByteBuffer temp = ByteBuffer.allocate(1024);
            int read = 0;
            int new_line_offset = 0;
            while(true){
                read = System.in.read(temp.array()); // Get data from stdin, hit enter to send!

                if(read > 0){
                    if (temp.get(read-1) == '\n' || temp.get(read-1) == '\r' ) new_line_offset = 1; //Check if last char is a return or newline so we can strip it
                    if (read > 1 && (temp.get(read-2) == '\n' || temp.get(read-2) == '\r') ) new_line_offset = 2; //Check if second to last char is a return or newline so we can strip it
                    ByteBuffer toSend = ByteBuffer.allocate(read-new_line_offset); // copy data without newline / returns
                    toSend.put( temp.array(), 0, read-new_line_offset ); // enter data without newline / returns

                    String newContent = new String(toSend.array());
                    if (newContent.equals("!help")) {
                        TUI();
                        continue;
                    }
                    else if (newContent.equals("!exit")) {
                        System.exit(0);
                        return;
                    }

                    Message msg;
                    if( (read-new_line_offset) > 2 ){
                        // Header + toSend
                        msg = new Message(MessageType.DATA, toSend);
                    } else {
                        // usually just used for the ACKs
                        msg = new Message(MessageType.DATA_SHORT, toSend);
                    }
                    sendingQueue.put(msg);
                }
            }
        } catch (InterruptedException e){
            System.exit(2);
        } catch (IOException e){
            System.exit(2);
        }
    }

    public static void main(String args[]) {
        if(args.length > 0){
            frequency = Integer.parseInt(args[0]);
        }

        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);        
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue){
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printByteBuffer(ByteBuffer bytes, int bytesLength){
            for(int i=0; i<bytesLength; i++){
                System.out.print( Byte.toString( bytes.get(i) )+" " );
            }
            System.out.println();
        }

        public void run(){
            while(true) {
                try{
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.BUSY){
                        System.out.println("BUSY");
                    } else if (m.getType() == MessageType.FREE){
                        System.out.println("FREE");
                    } else if (m.getType() == MessageType.DATA){
                        System.out.print("DATA: ");
                        ByteBuffer message = m.getData();
                        String newContent = new String(message.array());
                        System.out.println(newContent);
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                    } else if (m.getType() == MessageType.DONE_SENDING){
                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO){
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING){
                        System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END){
                        System.out.println("END");
                        System.exit(0);
                    }
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }
            }
        }


    }
}

