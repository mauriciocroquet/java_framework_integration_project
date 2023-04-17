import client.*;

import java.io.StringBufferInputStream;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    private List<Integer> directions = new ArrayList<>();

    private Client client;
    boolean endFlood = false;
    private ForwardingTable forwardingTable;
    private boolean ACK = false;

    private int sequenceNum = 0;
    private List<PacketACK> packetAck = new ArrayList<>();
    private List<Message> pending = new ArrayList<>();


    public void printMessage(Message msg){
        ByteBuffer bb = msg.getData();
        byte[] bytes = bb.array();
        int payloadLen = bytes[2] & 0b11111;
        int sender = bytes[1] >> 7;
        System.out.print("[" + sender + "]: ");
        for(int i = 0; i < payloadLen; i++){
            System.out.print((char)bytes[3+i]);
        }
        System.out.println();
    }
    
    public void fullSend(Message m){
        pending.add(m); // we remove this from the list when we get the respective ack
        while(!ACK){
            if(pending.contains(m)){
                slottedMAC(m);
            }
            myWait(800); // wait for timeout
            // we're turning it to true in run()
        }
        ACK = false;
    }

    public void ackRecieved(){
        packetAck.removeIf(packet -> System.currentTimeMillis() - packet.timestamp > 2500); // time may be longer than expected for acks
    }

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

//    public void acknoledge(PacketACK pkt){
//        waitingACK.remove(pkt);
//    }
//
//    public void pending(PacketACK pkt){
//        if(!waitingACK.contains(pkt)){
//            waitingACK.add(pkt);
//        }
//    }
//
//    public void updateSeq(){ // just to update sequence numbers while chatting
//        sequenceNum++;
//        sequenceNum = sequenceNum % 8;
//    }
//
//    public void slottedMACACK(Message msg){
//        ByteBuffer bytes = msg.getData();
//        byte[] packet = bytes.array();
//        int dest = //
//        int seq = //
//        PacketACK pendingPkt = new PacketACK(seq, dest);
//        pending();
//        while(true){
//            slottedMAC(msg);
//            myWait(800);
//            if(!waitingACK.contains(pendingPkt)){
//                break;
//            }
//            // update sequence number
//        }
//    }

    public void slottedMAC(Message msg){ // can only be used after dynamic addressing is done
        while(true){
            if((System.currentTimeMillis() % 600 > client.getAddress()* 150L) && (System.currentTimeMillis() % 600 < (client.getAddress()* 150L)+150 )){
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
        long globalTimer = System.currentTimeMillis();
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!
        int address = 0;
        while(directions.size() < 4){
            Random rand = new Random();
            address = rand.nextInt(127);

            System.out.println("the random number: " + address);

            byte[] pkt = new byte[2];
            pkt[0] = (byte) 0b010 << 5;
            pkt[1] = (byte) address;

            directions.add(address);

            ByteBuffer msg = ByteBuffer.allocate(2);
            msg = ByteBuffer.wrap(pkt);
            msg.put(pkt);
            try{
                long timer = System.currentTimeMillis();
                while(System.currentTimeMillis() - timer < 100000){
                    MAC(new Message(MessageType.DATA_SHORT, msg));
                    System.out.println("Still here - looking for neighbours");
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
            myWait(80000);


        }

        receivedQueue.clear();

        if(directions.size() == 4 && !endFlood){

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

            // 01100000 0xxxxxxx 0yyyyyyy 0zzzzzzz 0ooooooo
            byte[] addressaPkt = new byte[5];
            addressaPkt[0] = (byte) (0b01100000);
            addressaPkt[1] = (byte) (directions.get(0) & 0b1111111);
            addressaPkt[2] = (byte) (directions.get(1) & 0b1111111);
            addressaPkt[3] = (byte) (directions.get(2) & 0b1111111);
            addressaPkt[4] = (byte) (directions.get(3) & 0b1111111);
            ByteBuffer msg = ByteBuffer.wrap(addressaPkt);

            while(System.currentTimeMillis() - globalTimer < 40000){
                propagatePure(4000, new Message(MessageType.DATA, msg));
            }

        }

        // habia un receiving queue aca
        while(directions.size() != 4  && System.currentTimeMillis() - globalTimer < 30000){
        }
        receivedQueue.clear();

        Collections.sort(directions); // sort the list so the index of all
                                      // of the nodes can be reduced to two by using their respective index

        for(Integer direction: directions){
            System.out.print(direction + ", ");
        }
        System.out.println(" Should be completed ");
        client.setAddress(directions.indexOf(address));
        // Start of DVR
        forwardingTable = new ForwardingTable(client.getAddress());

        myWait(30000);


        byte[] dvrPkt =new byte[2];
        // header should contain: identifier (3 bits), src (2 bits), hops (2 bits), sender (2 bits) --- 10 bits of header
        // format: iii-ss-hh-d d0000000
        // vamos a cambiar esto a iii00000 0ss-hhh-nn
//        dvrPkt[0] = (byte) ((0b010 << 5) | (client.getAddress() << 3) | (client.getAddress() >> 1));
//        dvrPkt[1] = (byte) ((client.getAddress() << 7));          this was the old format

        dvrPkt[0] = (byte) (0b010 << 5);
        dvrPkt[1] = (byte) ((client.getAddress() << 5) | (client.getAddress()));

        byte[] payload = forwardingTable.toBytes();
        byte[] fullpacket = new byte[dvrPkt.length+ payload.length];

        System.arraycopy(dvrPkt,0, fullpacket,0,dvrPkt.length);
        System.arraycopy(payload,0, fullpacket, dvrPkt.length, payload.length);

        ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);

//        slottedMAC(new Message(MessageType.DATA, bufferPacket));
        propagatePure(200, new Message(MessageType.DATA, bufferPacket));


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
//                            MAC(m);
                            directions.clear();
                            // type: DATA, Parsing: 011-xxxxx yyyyy-zzz zz-ooooo0
                            //type: DATA, Parsing: 011-xxxxx 0-yyyyyyy 000-zzzzz
                            directions.add((int) m.getData().get(1));
                            directions.add((int) m.getData().get(2));
                            directions.add((int) m.getData().get(3));
                            directions.add((int) m.getData().get(4));

                            propagatePure(2000, m);
                        }else if(directions.size() == 4){
                            // all the rest and after DVR
                            // new format iii00000 0dd-hhh-nn
                            if(m.getData().get(0) >> 5 == 0b010 && ((m.getData().get(1) & 0b11100) >> 2) != 0b11){ // 010 is the identifier for DVR
                                int src = m.getData().get(1) >> 5;
                                int hops = (m.getData().get(1) >> 2) & 0b111;
                                int sender = m.getData().get(1) & 0b11;

                                hops++;
                                System.out.println("hops: " + hops);
                                ForwardingTable neighbour = new ForwardingTable(m.getData().array());
                                if(hops < forwardingTable.getCost(src)){
                                    forwardingTable.newRoute(src,hops,sender);
                                }

                                //
                                System.out.println(neighbour.getAddress());
                                forwardingTable.mergeTables(neighbour);
                                System.out.println("My neigbour is: " + neighbour.getAddress());
                                forwardingTable.print();
                                // now change sender and put it in the sending queue
                                //send a new updated message of the FT
                                byte[] header =new byte[2];
                                header[0] = (byte) (0b010 << 5);
                                //make new packet of hops 0, sender you, source you, payload your FT
                                src = client.getAddress();
                                hops = 0;
                                sender = client.getAddress();
                                header[1] = (byte) (src << 4 | hops << 2 | sender);
                                byte[] payload = forwardingTable.toBytes();
                                byte[] fullpacket = new byte[header.length+ payload.length];

                                System.arraycopy(header,0, fullpacket,0,header.length);
                                System.arraycopy(payload,0, fullpacket, header.length, payload.length);

                                ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);

//                                slottedMAC(new Message(MessageType.DATA, bufferPacket));
                                propagatePure(200, new Message(MessageType.DATA, bufferPacket));

                            }
                        }else if (m.getData().get() >> 5 == 0b0) {
                            // this is if you receive text and you are the destination
                            
                            // first check if youre the next hop and then check if youre the destination
                            
                            if((m.getData().get(2) >> 5) == client.getAddress()){ // first check if you where the next hop
                                int src = m.getData().get(1) >> 5;
                                int dst = m.getData().get(1) >> 3 & 0b11;
                                int seq = m.getData().get(1) & 0b11;
                                int nextHop = m.getData().get(2) >> 5;

                                // if youre the next address do nothing but printing
                                if(dst == client.getAddress()){
                                    printMessage(m);
                                    packetAck.add(new PacketACK(System.currentTimeMillis(), m));
                                }else{
                                    // send it in rout to the destination
                                    // make use of the forwarding table and then reformat the packet to full send
                                }
                                // acknoledge to the sender the text arrived

                            }
                            // this is if you are not the destination
                            // check if you are using dvr

                            // print the message later in the parsing
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        if(m.getData().get(0) >> 5 == 0b010){
                            int neighbour = m.getData().get(1) & 0b01111111;
                            System.out.println("New nb: " + neighbour);
                            if(!directions.contains(neighbour)) {
                                directions.add(neighbour);
                                if (directions.size() < 4) {
                                    propagatePure(300,m);
                                }
                            }
                            propagatePure(300,m);
                        }else if(m.getData().get(0) >> 5 == 0b011){
                            endFlood = true;
                        }else if(m.getData().get(0) >> 5 == 0b0){
                            // check if pending is waiting on this ack to avoid retransmission
                            for(Message p: pending){
                                // parse the m and see if it is contained here
                                if(p.getData().get(1) == m.getData().get(1)){
                                    pending.remove(p);
                                    ACK = true;
                                }
                            }
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
                ackRecieved();
            }
        }
    }
}

