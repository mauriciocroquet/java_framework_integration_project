import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collector;

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

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    private List<Integer> directions = new ArrayList<>();

    private Client client;
    boolean endFlood = false;
    private ForwardingTable forwardingTable;

    public void MAC(Message msg) throws InterruptedException {
        boolean trying = true;
        double p = 0.25;
        while(trying){
            myWait(500);
            if (new Random().nextInt(100) < p * 100) {
                sendingQueue.put(msg);
                trying = false;
            }
        }
    }

    public void slottedMAC(Message msg){ // can only be used after dynamic addressing is done
        while(true){
            if(System.currentTimeMillis() % (client.getAddress() + 71) == 0){
                try{
                    sendingQueue.put(msg);
                }catch(InterruptedException e){
                    System.exit(2);
                }
                return;
            }
        }
    }
    // Java's own wait() was not working as intended.
    public void myWait(int ms) {
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start < ms){
        }
    }

    public void propagatePure(int ms, Message msg){
        try{
            long start = System.currentTimeMillis();
            while(System.currentTimeMillis()-start < ms){
                MAC(msg);
            }

        }catch (InterruptedException e){
            System.exit(2);
        }
    }

    public void propagateSlotted(int ms, Message msg){
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start < ms){
            slottedMAC(msg);
        }

    }

    public MyProtocol(String server_ip, int server_port, int frequency){
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!
        int address = 0;
        while(directions.size() < 4){
            Random rand = new Random();
            address = rand.nextInt(31);
            byte pkt = (byte) (0b01000000 | address);

            directions.add(address);

            ByteBuffer msg = ByteBuffer.allocate(1);
            msg.put(pkt);
            try{
                long timer = System.currentTimeMillis();
                while(System.currentTimeMillis() - timer < 100000){
                    MAC(new Message(MessageType.DATA_SHORT, msg));
                    if(directions.size() == 4 || endFlood){
                        break;
                    }
                }

            }catch (InterruptedException e){
                System.exit(2);
            }
            if(directions.size() == 4 || endFlood){
                break;
            }else{
                directions.clear();
            }

            //if the directions size is smaller than 4, wait a while for all to finish/reset and then start over
            myWait(800);


        }

        receivedQueue.clear();

        if(directions.size() == 4){

            // now end the while loops of others
            ByteBuffer end = ByteBuffer.allocate(1);
            byte endSignal = 0b01100000;
            end.put(endSignal);


            // the first node that gets all directions call others to ask them to stop flooding
            try{
                long start = System.currentTimeMillis();
                while(System.currentTimeMillis()-start < 1000){
                    MAC(new Message(MessageType.DATA_SHORT, end));
                }

            }catch (InterruptedException e){
                System.exit(2);
            }

            // propagate the directions for a while
            // type: DATA, Parsing: 110-xxxxx yyyyy-zzz zz-ooooo0

            byte[] addressaPkt = new byte[3];
            addressaPkt[0] = (byte) (0b01100000 | directions.get(0));
            addressaPkt[1] = (byte) ((byte) (directions.get(1) << 3) | (directions.get(2) >> 2));
            addressaPkt[2] = (byte) ((byte) (directions.get(2) << 6) | (directions.get(3) << 1));
            ByteBuffer msg = ByteBuffer.wrap(addressaPkt);

            propagatePure(2000, new Message(MessageType.DATA, msg));
            
        }

        // habia un receiving queue aca
        while(directions.size() != 4){
        }
        receivedQueue.clear();

        Collections.sort(directions); // sort the list so the index of all
                                      // of the nodes can be reduced to two by using their respective index

        for(Integer direction: directions){
            System.out.print(direction + ", ");
        }
        System.out.println(" Should be completed");
        client.setAddress(directions.indexOf(address));

        // Start of DVR
        forwardingTable = new ForwardingTable(client.getAddress());

        myWait(30000);


        byte[] dvrPkt =new byte[2];
        // header should contain: identifier (3 bits), src (2 bits), hops (2 bits), sender (2 bits) --- 10 bits of header
        // format: iii-ss-hh-d d0000000
        dvrPkt[0] = (byte) ((0b010 << 5) | (client.getAddress() << 3) | (client.getAddress() >> 1));
        dvrPkt[1] = (byte) ((client.getAddress() << 7));

        byte[] payload = forwardingTable.toBytes();
        byte[] fullpacket = new byte[dvrPkt.length+ payload.length];

        System.arraycopy(dvrPkt,0, fullpacket,0,dvrPkt.length);
        System.arraycopy(payload,0, fullpacket, dvrPkt.length, payload.length);

        ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);

        slottedMAC(new Message(MessageType.DATA, bufferPacket));


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
                    Message msg;
                    if( (read-new_line_offset) > 2 ){
                        msg = new Message(MessageType.DATA, toSend);
                    } else {
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
                System.out.print(Byte.toString( bytes.get(i) )+" " );
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
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        if(m.getData().get(0) >> 5 == 0b011 & directions.size() != 4){
                            MAC(m);
                            directions.clear();
                            // type: DATA, Parsing: 110-xxxxx yyyyy-zzz zz-ooooo0
                            directions.add(m.getData().get(0) & 0b00011111);
                            directions.add(m.getData().get(1) >> 3);
                            directions.add(((m.getData().get(1) & 0b111) << 2) | m.getData().get(2) >> 6 );
                            directions.add((m.getData().get(2) & 0b111110) >>1);
                        }else if(directions.size() == 4){
                            // all the rest and after DVR
                            if(m.getData().get(0) >> 5 == 0b010 && (Integer)((m.getData().get(0) & 0b00000110) >> 1) != 0b11){ // 010 is the identifier for DVR
                                int src = (m.getData().get(0) & 0b00011000) >> 3;
                                int hops = (m.getData().get(0) & 0b00000110) >> 1;
                                int sender = ((m.getData().get(0) & 0b1) << 1) | (m.getData().get(1) >> 7);
                                hops++;
                                ForwardingTable neighbour = new ForwardingTable(m.getData().array());
                                if(hops < forwardingTable.getCost(src)){
                                    forwardingTable.newRoute(src,hops,sender);
                                }
                                forwardingTable.mergeTables(neighbour);
                                forwardingTable.print();
                                // now change sender and put it in the sending queue
                                byte[] header =new byte[2];
                                header[0] = (byte) ((0b010 << 5) | (src << 3) | (hops << 1) | (client.getAddress() >> 1));
                                header[1] = (byte) (client.getAddress() << 7);
                                byte[] payload = neighbour.toBytes();
                                byte[] fullpacket = new byte[header.length+ payload.length];

                                System.arraycopy(header,0, fullpacket,0,header.length);
                                System.arraycopy(payload,0, fullpacket, header.length, payload.length);

                                ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);

                                slottedMAC(new Message(MessageType.DATA, bufferPacket));
                            }
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        if(m.getData().get(0) >> 5 == 0b010){
                            int neighbour = m.getData().get(0) & 0b00011111;
                            if(!directions.contains(neighbour)) {
                                directions.add(neighbour);
                                if (directions.size() < 4) {
                                    MAC(m);
                                }
                            }
                        }else if(m.getData().get(0) >> 5 == 0b011){
                            endFlood = true;
                        }
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

