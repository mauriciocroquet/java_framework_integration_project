import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Date;
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

    private List<String> names = new ArrayList<>();

    private int sequenceNum = 0;
//    private List<PacketACK> packetAck = new ArrayList<>();
//    private List<Message> pending = new ArrayList<>();

    private List<ObjReceived> printingList = new ArrayList<>();
    private List<Message> retransmitList = new ArrayList<>();//ack list
    private List<ObjReceived> ackList = new ArrayList<>();
    private List<String> printed = new ArrayList<>();
    private HashMap<Byte,List<Message>> fragmentationMap = new HashMap<>();
    private long globalTimer;




    public void printMessage(Message msg){
        ByteBuffer bb = msg.getData();
        byte[] bytes = bb.array();
        int payloadLen = (bytes[2] >> 3);
        String text = "";
        int sender = bytes[1] >> 7;
        for(int i = 0; i < payloadLen; i++){
            text += (char)bytes[3+i];
        }
        if(!printed.contains(text)){
            System.out.print("[Message]: "); // change this sender later
            System.out.println(text);
        }
        printed.add(text);

    }
    
//    public void fullSend(Message m){
//        pending.add(m); // we remove this from the list when we get the respective ack
//        while(!ACK){
//            if(pending.contains(m)){
//                slottedMAC(m);
//            }
//            myWait(800); // wait for timeout
//            // we're turning it to true in run()
//        }
//        ACK = false;
//    }
//
//    public void ackRecieved(){
//        packetAck.removeIf(packet -> System.currentTimeMillis() - packet.timestamp > 2500); // time may be longer than expected for acks
//    }

    public void MAC(Message msg) throws InterruptedException {
        boolean trying = true;
        double p = 0.25;
        int time = msg.getType()==MessageType.DATA?1500:200;
        while(trying){
            myWait(time);
            if (new Random().nextInt(100) < p * 100) {
                sendingQueue.put(msg);
                trying = false;
            }
        }
    }

//    public void acknowledge(PacketACK pkt){
//        waitingACK.remove(pkt);
//    }
//
//    public void pending(PacketACK pkt){
//        if(!waitingACK.contains(pkt)){
//            waitingACK.add(pkt);
//        }
//    }
//
    public void updateSeq(){ // just to update sequence numbers while chatting
        sequenceNum++;
        sequenceNum = sequenceNum % 8;
    }
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
//        System.out.println("Into slotted");
        while(true){
            Date dateTime = new Date();
            if((dateTime.getTime() % 1000 > client.getAddress()* 250L) && (dateTime.getTime() % 600 < (client.getAddress()* 250L)+250 )){
                try{
                    sendingQueue.put(msg);
//                    System.out.println("sending slotted");
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

    public void propagatePureTables(int ms, Message msg){
        try{
            long start = System.currentTimeMillis();
            while(System.currentTimeMillis()-start < ms){
                myWait(13000);
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

    public void chatRoom(){
        while(!receivedQueue.isEmpty()){
            // wait
        }
        while (true){
            System.out.print("["+client.getAddress()+"]: ");
            Scanner scan = new Scanner(System.in);
            String text = scan.nextLine();
            updateSeq();
            if(Objects.equals(text, "!table")){
                forwardingTable.print();
            }else{
                if(text.length() >= 30 && text.length()<=58){ // this is for longer packets
                    Message message = null;
                    String part1 = text.substring(0,28);
                    String part2 = text.substring(29);
                    for(int j = 0; j < 2; j++){
                        for(int i = 0; i < 3; i++){
                            byte[] header = new byte[3];
                            byte[] payload;
                            if(j == 0){
                                payload = part1.getBytes();
                            }else{
                                payload = part2.getBytes();
                            }
//                    System.out.println("Sending a message from ");
                            header[0] = (byte) (client.getAddress() << 3 | forwardingTable.getNeigbours().get(i) << 1);
                            header[1] = (byte) (forwardingTable.getNextHops().get(i) << 5 | sequenceNum << 2 | (j==0?0b01:0b10));
                            header[2] = (byte) (payload.length << 3);
                            byte[] packet = new byte[header.length+payload.length];
                            System.arraycopy(header,0, packet,0,header.length);
                            System.arraycopy(payload,0, packet, header.length, payload.length);
                            ByteBuffer msg = ByteBuffer.wrap(packet);
                            message = new Message(MessageType.DATA, msg);
                            new retransmitList(message).start();
//                            myWait(1000);
                        }
                    }

                } else if (text.length()>59) {
                    System.out.println("Text is too long, please shorten it");
                } else{ //text message format: 000ssdd0 nnqqqff ppppp000 +29bytes
                    Message message = null;
                    for(int i = 0; i < 3; i++){
                        byte[] header = new byte[3];
                        byte[] payload = text.getBytes();
//                    System.out.println("Sending a message from ");
                        header[0] = (byte) (client.getAddress() << 3 | forwardingTable.getNeigbours().get(i) << 1);
                        header[1] = (byte) (forwardingTable.getNextHops().get(i) << 5 | sequenceNum << 2); // no frag
                        header[2] = (byte) (payload.length << 3);
                        byte[] packet = new byte[header.length+payload.length];
                        System.arraycopy(header,0, packet,0,header.length);
                        System.arraycopy(payload,0, packet, header.length, payload.length);
                        ByteBuffer msg = ByteBuffer.wrap(packet);
                        message = new Message(MessageType.DATA, msg);
                        new retransmitList(message).start();
//                        myWait(1000);
                    }
                }
            }


        }
    }

    public void fragmentationFinder(byte key, Message frag){
        if(fragmentationMap.containsKey(key)){
            // we know it has two then we print them in order
            List<Message> fragments = fragmentationMap.get(key);
            if((fragments.get(0).getData().array()[1] & 0b1) < (frag.getData().array()[1] & 0b1)){
                // print frag first
                printMessage(frag);
                printMessage(fragments.get(0));
            }else{
                // print fragments.get(0) first
                printMessage(fragments.get(0));
                printMessage(frag);
            }
        }else{
            List<Message> values = new ArrayList<>();
            values.add(frag);
            fragmentationMap.put(key, values);
        }
    }

    void redirectMessage(Message msg){
        do{
            if(!retransmitList.contains(msg)){
                retransmitList.add(msg);
            }
            myWait(10000);
        }while(retransmitList.contains(msg));
    }

    public byte[] ackBuilder(int src, int dest, int sequenceNum, int frag){
        byte[] ack = new byte[2];
        ack[0] = (byte) ((src << 3) | (dest << 1));
        ack[1] = (byte) ((sequenceNum << 2) | frag);
        return ack;
    }

    public MyProtocol(String server_ip, int server_port, int frequency){
        globalTimer = System.currentTimeMillis();
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!
        int address = 0;
        while(directions.size() < 4){
            Random rand = new Random();
            address = rand.nextInt(127);

//            System.out.println("the random number: " + address);

            byte[] pkt = new byte[2];
            pkt[0] = (byte) 0b010 << 5;
            pkt[1] = (byte) address;

            directions.add(address);

            ByteBuffer msg = ByteBuffer.allocate(2);
            msg = ByteBuffer.wrap(pkt);
            msg.put(pkt);
            try{
                long timer = System.currentTimeMillis();
                while(System.currentTimeMillis() - timer < 120000){
//                    if(System.currentTimeMillis() - timer > 37500 && directions.size()<4){
//                        System.out.println("now increase");
//                        myWait(1700);
//                    }
                    if(System.currentTimeMillis() - timer > 42000){
                        break;
                    }
                    MAC(new Message(MessageType.DATA_SHORT, msg));
                    for(Integer direction: directions){
                        System.out.print(direction + ", ");
                    }
                    System.out.println(" up to this point -- still looking");
//                    if(directions.size() == 4 || endFlood){
//                        break;
//                    }
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

//        if(directions.size() == 4){
//
//            // now end the while loops of others
//            ByteBuffer end = ByteBuffer.allocate(1);
//            byte endSignal = 0b01100000;
//            end.put(endSignal);
//
//
//            // the first node that gets all directions call others to ask them to stop flooding
//            try{
//                long start = System.currentTimeMillis();
//                while(System.currentTimeMillis()-start < 1000){
//                    MAC(new Message(MessageType.DATA_SHORT, end));
//                }
//
//            }catch (InterruptedException e){
//                System.exit(2);
//            }
//
//            // 01100000 0xxxxxxx 0yyyyyyy 0zzzzzzz 0ooooooo
//            byte[] addressaPkt = new byte[5];
//            addressaPkt[0] = (byte) (0b01100000);
//            addressaPkt[1] = (byte) (directions.get(0) & 0b1111111);
//            addressaPkt[2] = (byte) (directions.get(1) & 0b1111111);
//            addressaPkt[3] = (byte) (directions.get(2) & 0b1111111);
//            addressaPkt[4] = (byte) (directions.get(3) & 0b1111111);
//            ByteBuffer msg = ByteBuffer.wrap(addressaPkt);
//
//            while(System.currentTimeMillis() - globalTimer < 60000){
////                myWait(1000);
////                System.out.println("Propagating full list");
//                try{
//                    MAC(new Message(MessageType.DATA, msg));
//
//                }catch(InterruptedException e){
//                    System.exit(2);
//                }
//            }
//
//        }
//        System.out.println("Before timer");
        // habia un receiving queue aca
        while(directions.size() != 4  && System.currentTimeMillis() - globalTimer < 60000){
        }

        Collections.sort(directions); // sort the list so the index of all
                                      // of the nodes can be reduced to two by using their respective index

//        for(Integer direction: directions){
//            System.out.print(direction + ", ");
//        }
        System.out.println(" Should be completed ");
        client.setAddress(directions.indexOf(address));
        // Start of DVR
        forwardingTable = new ForwardingTable(client.getAddress());


        myWait(30000);
//        System.out.println("after my wait");
        receivedQueue.clear();
        sendingQueue.clear();


//        long startDVR = System.currentTimeMillis();
//        while(System.currentTimeMillis()-startDVR < 90000){
//
//        }
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

        while(System.currentTimeMillis() - globalTimer < 110000){
//            System.out.println("Still sending -- propagation");
            try{
                MAC(new Message(MessageType.DATA, bufferPacket));
            }catch(InterruptedException e){
                System.exit(2);
            }
        }
//        System.out.println("Finish propagating tables");

//        System.out.println("Outside");
//        for(int i = 0; i < 10; i++){
//            slottedMAC(new Message(MessageType.DATA, bufferPacket));
//            System.out.println("Trying...");
//        }

        // handle sending from stdin from this thread.
        chatRoom();
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

    private class printThread extends Thread{
        private Message msg;
        public printThread(Message msg){
            super();
            this.msg = msg;
        }
        public void run(){

            // ---
            boolean print = true;
            for(ObjReceived obj: printingList){
//                System.out.println("into the loop");
                if(obj.msg == msg && System.currentTimeMillis()- obj.timestamp < 10000){
//                    System.out.println("Print shouldnt happen");
                    print = false;
                    obj.timestamp = System.currentTimeMillis();
                    break;
                }
            }

            if(print){
                printMessage(msg);
                printingList.add(new ObjReceived(System.currentTimeMillis(), msg));
            }
            ObjReceived temp = null;
            for(ObjReceived obj: printingList){
//                System.out.println("into the loop");
                if(System.currentTimeMillis()- obj.timestamp > 10000){
                 temp = obj;
                }
            }
            printingList.remove(temp);

        }
    }

    private class ackThread extends Thread{
        private Message msg;
        public ackThread(Message msg){
            super();
            this.msg = msg;
        }

        public void run(){
            boolean b;

            do{

                propagatePure(600,msg);
//                System.out.println("sending ack");

                b = false;
                ObjReceived temp = null;
                for(ObjReceived obj: ackList){
                    if(System.currentTimeMillis()-obj.timestamp > 10000 && obj.msg == msg){
                        temp = obj;
                    }
                }
                ackList.remove(temp);
                for(ObjReceived obj: ackList){
                    if(obj.msg == msg){
                        b = true;
//                        System.out.println("received retransmit");
                    }
                }
            }while(b);
        }
    }

    private class retransmitList extends Thread{
        private final Message msg;
        public retransmitList(Message msg){
            super();
            this.msg = msg;
        }

        @Override
        public void run() {
            do{
                try{
                    MAC(msg);
//                    System.out.println("Sending once");
                    byte[] message = msg.getData().array();
                }catch(InterruptedException e){
                    System.exit(2);
                }
                if(!retransmitList.contains(msg)){
                    retransmitList.add(msg);
                }
                myWait(10000);

            }while(retransmitList.contains(msg));
        }
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
//                        System.out.println("BUSY");
                    } else if (m.getType() == MessageType.FREE){
//                        System.out.println("FREE");
                    } else if (m.getType() == MessageType.DATA){
//                        System.out.print("DATA: ");
//                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        if(m.getData().get(0) >> 5 == 0b011){
//                            MAC(m);
                            if(directions.size() != 4){
                                directions.clear();
                                // type: DATA, Parsing: 01100000 0xxxxxxx 0yyyyyyy 0zzzzzzz 0ooooooo
                                directions.add((int) m.getData().get(1));
                                directions.add((int) m.getData().get(2));
                                directions.add((int) m.getData().get(3));
                                directions.add((int) m.getData().get(4));
                            }

                            propagatePureTables(10000, m);
                        }else if(directions.size() == 4 && m.getData().get(0) >> 5 == 0b010 ){ // added the a&& to avoid this if
                            // all the rest and after DVR
                            // new format iii00000 0dd-hhh-nn
                            if(m.getData().get(0) >> 5 == 0b010 && ((m.getData().get(1) & 0b11100) >> 2) != 0b11){ // 010 is the identifier for DVR
                                int src = m.getData().get(1) >> 5;
                                int hops = (m.getData().get(1) >> 2) & 0b111;
                                int sender = m.getData().get(1) & 0b11;

                                hops++;
//                                System.out.println("It took: " + hops + " hops to get from " + src + " to " + client.getAddress() + ", nb: " + sender );
                                ForwardingTable neighbour = new ForwardingTable(m.getData().array());
                                if(hops < forwardingTable.getCost(src)){
                                    forwardingTable.newRoute(src,hops,sender);
                                }

                                forwardingTable.mergeTables(neighbour);
                                forwardingTable.print();
                                // now change sender anda put it in the sending queue
                                //send a new updated message of the FT
                                byte[] header =new byte[2];
                                header[0] = (byte) (0b010 << 5);
                                //make new packet of hops 0, sender you, source you, payload your FT
                                sender = client.getAddress();
                                header[1] = (byte) (src << 5 | hops << 2 | sender);
                                byte[] payload = forwardingTable.toBytes();
                                byte[] fullpacket = new byte[header.length+ payload.length];

                                System.arraycopy(header,0, fullpacket,0,header.length);
                                System.arraycopy(payload,0, fullpacket, header.length, payload.length);

                                ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);

//                                for(int i = 0; i < 10; i++){
//                                    slottedMAC(new Message(MessageType.DATA, bufferPacket));
//                                }
                                while(System.currentTimeMillis() - globalTimer < 110000){
//                                    System.out.println("Still sending -- propagation");
                                    System.out.println("table propagating");
                                    MAC(new Message(MessageType.DATA, bufferPacket));
                                }


                            }
                        } else if (m.getData().get(0) >> 5 == 0b0 && !ackList.contains(m)) { // get() wasnt indxed
                            //text message format: 000ssdd0 nnqqqff ppppp000 +29bytes
                            //ss=source; dd= destination; nn= next hop; qqq= seq no; ff=fragmentation
                            //ff== 00 -> no fragmentation; ff== 01 ->frag + first packet from frag;
                            //ff== 10 -> frag + last packet

                            int src= m.getData().get(0) >> 3;
                            int dst= (m.getData().get(0) >> 1) & 0b11;
                            int nxt= m.getData().get(1) >>5;
                            int seq= (m.getData().get(1) >> 2) & 0b111;
                            int frag= m.getData().get(1) & 0b11;
                            int payld= m.getData().get(2) >> 3;
                            if(dst == client.getAddress()){

                                byte[] ack = ackBuilder(client.getAddress(), src, seq, frag);
                                Message msg = new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(ack));
//                                ackList.add(new ObjReceived(System.currentTimeMillis(), m));
//                                new ackThread(msg).start();
                                propagatePure(600,msg);
                                // ack thread

                                if (frag == 0b00){
                                    //send message to print buffer

                                    new printThread(m).start();

                                } else{
                                    byte[] id = new byte[1];
                                    id[0]= (byte) ((src <<6) | (dst <<4) | (seq <<1) | ((frag &0b10) >>1));
                                    fragmentationFinder(id[0], m);
                                }
                            } else if (nxt == client.getAddress()) {
                                //send packet to updated nxt hop based on Ftable
                                //dst remains same, source is us, nxt is modified
                                byte[] ack = ackBuilder(client.getAddress(), src, seq, frag);
                                Message msg = new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(ack));
//                                ackList.add(new ObjReceived(System.currentTimeMillis(), m));
//                                new ackThread(msg).start();
                                propagatePure(600,msg);

                                int newNextHop = forwardingTable.getNextHop(client.getAddress());
                                byte[] header = new byte[3];
                                header[0] = (byte) ((client.getAddress() <<3) | dst <<1);
                                header[1] = (byte) ((newNextHop<<5) | (seq << 2) | frag);
                                header[2] = m.getData().get(2);

                                byte[] payload = Arrays.copyOfRange(m.getData().array(),3,m.getData().array().length); // could be -1

                                byte[] packet = new byte[header.length+payload.length];
                                System.arraycopy(header,0, packet,0,header.length);
                                System.arraycopy(payload,0, packet, header.length, payload.length);

                            } else if (nxt != client.getAddress()) {
                                //drop; nothing done
                            }
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT){
//                        System.out.print("DATA_SHORT: ");
                        if(m.getData().get(0) >> 5 == 0b010 && directions.size() < 4){
                            int neighbour = m.getData().get(1) & 0b01111111;
                            if(!directions.contains(neighbour)) {
//                                System.out.println("New nb: " + neighbour);
                                directions.add(neighbour);
//                                for(Integer direction: directions){
//                                    System.out.print(direction + ", ");
//                                }
//                                System.out.println(" list up to this point ");
                                if (directions.size() < 4) {
                                    propagatePure(300,m);
                                }else if(directions.size() == 4){
                                    endFlood = true;
                                }
                            }
                            propagatePure(300,m);
                        }else if(m.getData().get(0) >> 5 == 0b011 && directions.size() == 4){
                            endFlood = true;
                        }else if(m.getData().get(0) >> 5 == 0b0){
                            // check if pending is waiting on this ack to avoid retransmission
                            int src = m.getData().get(0) >> 3;
                            int dst = m.getData().get(0) >> 1 & 0b11;
                            int sequence = m.getData().get(1) & 0b11111;
                            if(client.getAddress() == dst){
                                Message temp = null;
                                for(Message msg : retransmitList){
                                    byte[] info = msg.getData().array();
                                    int srcofMsg = info[0] >> 3;
                                    int nextHop = info[1] >> 5; // 000SSDD0
                                    int sequenceOfNumWFlag = info[1] & 0b11111; // 000QQQFF
                                    if(srcofMsg == dst && nextHop == src && sequenceOfNumWFlag == sequence){
//                                        System.out.println("removed a message after ack");
                                        temp = msg;
                                        break;
                                    }
                                }
                                retransmitList.remove(temp);

                            }
                        }
                    } else if (m.getType() == MessageType.DONE_SENDING){
//                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO){
//                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING){
//                        System.out.println("SENDING");
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

//        sending node:     One list for retransmitting, aka the list with packets waiting to be acknowledged, these packets have a timer.
//        Stop timer when ack comes in. If timer reaches 0, resend packet.
//        receiving node:   3 lists
//        -   AckList:    List of packets that have an acknowledgement sent. It stays here for 10 seconds,
//        waiting to see if the same packet will be received, meaning the ack got lost.
//
//        -   FragmentedList: Whenever a packet is acked in the first list, it enters the second list if the
//        fragmentation bits are set to 01 or 10 AND they have the same sequence number.
//
//        -   PrintList:  List of the packets that leave the FragmentedList (in case it was fragmented and completed back) or
//        AckList if the fragmentation bits are set to 00.