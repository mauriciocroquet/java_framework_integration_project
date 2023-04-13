import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;
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
    private final int INFINITY= 1000000;
    private FTable forwardingTable;

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;
    private boolean addressConflict;
    private double p = 0.25;
    private List<Integer> directions = new ArrayList<>();
    public void TUI(){
        System.out.println(
                "COMMANDS:" + "\n" +
                        "!help              Print the list of commands again." + "\n" +
                        "!exit              Exit the program and any chatroom you may be in. \n" +
                        "Any other input will be sent as a message through the broadcast for others to see.");
    }

    public List<Byte> addressing(Client client){
        List<Byte> initail = new ArrayList<>();
        Random rand = new Random();
        int serialNumber = rand.nextInt(255);
        client.setAddress(serialNumber);
        initail.add((byte) serialNumber);
        return initail;
    }

    public void updateTable(FTable neighbourTable, int costWithNeighbour, int neighbourAddress){
        for(int i = 0; i < 4; i++){ // number of rows
            if (forwardingTable.get(i, 1) > neighbourTable.get(i, 1) + costWithNeighbour){
                forwardingTable.set(i, 1, neighbourTable.get(i, 1) + costWithNeighbour); // update cost
                forwardingTable.set(i, 2, neighbourAddress); // update next hop
            }
        }
    }

    public void MAC(Message msg) throws InterruptedException {
        boolean out = true;
        while(out) {
            long start = System.currentTimeMillis();
            while(System.currentTimeMillis()-start < 400){

            }
            if (new Random().nextInt(100) < p * 100) {
                sendingQueue.put(msg);
                out = false;
            }
        }
    }

    public MyProtocol(String server_ip, int server_port, int frequency) throws InterruptedException {
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();
        addressConflict = true;

        Client client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use


        // Routing and addressing stage
        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        // Create an initial forwarding table: should only contain address, cost, nextHop, in said order on the table


        while(addressConflict){
            ByteBuffer msg = ByteBuffer.allocate(1);
            Random rand = new Random();
            int address = rand.nextInt(31);
            directions.add(address);
            byte[] addressPkt = new byte[2];
            byte start = (byte) ((0b010 << 5) | address);
            byte ttl = 0b100;
            addressPkt[0] = start;
            addressPkt[1] = ttl;
            msg = ByteBuffer.wrap(addressPkt);

            long timer = System.currentTimeMillis();

            while(System.currentTimeMillis()-timer < 1000000){
                MAC(new Message(MessageType.DATA_SHORT, msg));
            }
            if(directions.size() == 4){
                directions.sort(new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        return Math.max(o1,o2);
                    }
                });
                System.out.println("Turning off addressConflict");
                addressConflict = false;
                client.setAddress(directions.indexOf(address)); // makes the addresses smaller (from 0 - 3)
                ByteBuffer finish = ByteBuffer.allocate(3);

                byte header = (byte) ((0b010 << 5) | directions.get(0));
                byte second = (byte) ((byte) (directions.get(1) << 3) | directions.get(2) >> 2);
                byte third = (byte) ((byte) (directions.get(2) << 6) | directions.get(3));
                finish.put(third);
                finish.put(second);
                finish.put(header);

                MAC(new Message(MessageType.DATA, finish));

            }else{
                System.out.println("EMpty list");
                directions.clear();
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


                    MAC(msg);
                }
            }
        } catch (InterruptedException e){
            System.exit(2);
        } catch (IOException e){
            System.exit(2);
        }
    }

    public static void main(String args[]) throws InterruptedException {
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

                        if(m.getData().get(0) >> 5 == 0b010 & addressConflict){ // 010 is the identifier for the addressing
                            addressConflict = false;
                            // parse all of the 4 addresses to directions

                            int first = (m.getData().get(0) & 0b11111); // to erase the 010 at the start
                            int second = (m.getData().get(1) >> 3);
                            int third = ((m.getData().get(1) & 0b111) <<2) |  (m.getData().get(2) & 0b11000000) >>6; //z
                            int fourth = (m.getData().get(2) & 0b00111110) >>1; //p

                                    // 010-xxxxx yyyyy-zzz zz-ppppp0
                            MAC(m);
                        }

                        String newContent = new String(message.array());
                        System.out.println(newContent);
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        if(m.getData().get(1) != 0b1){
                            if(m.getData().get(0) >> 5 == 0b010){
                                int neighbour = (m.getData().get(0) & 0b11111); // to erase the 010 at the start
                                if(!directions.contains(neighbour) && directions.size() < 5){
                                    directions.add(neighbour);
                                    for(Integer direction: directions){
                                        System.out.print(direction + ", ");
                                    }
                                    addressConflict = false;
                                }
                                if(addressConflict){ // decrease the ttl
                                    byte[] newpkt = new byte[2];
                                    byte start = m.getData().get(0);
                                    int ttl = m.getData().get(1) - 1;
                                    newpkt[0] = start;
                                    newpkt[1] = (byte)ttl;

                                    MAC(new Message(MessageType.DATA, ByteBuffer.wrap(newpkt)));
                                }

                            }
                        }
                        // propagate it again

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

