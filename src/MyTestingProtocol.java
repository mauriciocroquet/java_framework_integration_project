import client.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Thread.sleep;

public class MyTestingProtocol {

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static final String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static final int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 6900;
    private final BlockingQueue<Message> receivedQueue;
    private final BlockingQueue<Message> sendingQueue;
    private final List<Integer> directions = new ArrayList<>();
    private final Client client;
    private final boolean ACK = false;
    private final List<String> names = new ArrayList<>();
    private final List<ObjReceived> printingList = new ArrayList<>();
    private final static List<Message> retransmitList = new ArrayList<>();
    private final List<Message> ackList = new ArrayList<>();
    private final HashMap<Message, Long> receivedMap = new HashMap<>();

    private final List<String> printed = new ArrayList<>();
    private final HashMap<Byte, List<Message>> fragmentationMap = new HashMap<>();
    private final int addressingTime = 45000;
    /**
     * Testruns to see how long addressing takes with line topology & 25% packet loss:
     * 10s, 15s, 18s, 20s, 20s, 21s, 23s, 23s, 24, 25s, 26s, 29s, 30s, 30s, 32s, 32s, 35s, 37s, 37s, 40s, 43s
     */
    private final int waiter = 50000;
    private final List<String> users = new ArrayList<>();
    private final String menu =
            "COMMANDS: \n" +
                    "!name [NAME] ............ Enter your name by typing !name, a spacebar, then a name of your choice. \n" +
                    "!help ................... Print out these commands if you forget what there are. \n" +
                    "!submarines ............. Display all current online submarines (nodes) in the chat. \n" +
                    "!table .................. Print your routing table \n" +
                    "!exit ................... Exit the program and any chat you are in. \n";
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    boolean endFlood = false;
    private long globalTimer = 0;
    private boolean stayAlive = true;
    private boolean csmaFlag = true;
    private HashMap<Integer, Integer> priorityMap = new HashMap<>();
    private ForwardingTable forwardingTable;
    private int sequenceNum = 0;
    private boolean tryAgain = true;
    private int extra = 0;

    public MyTestingProtocol(String server_ip, int server_port, int frequency) {

        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();


        // global variable for myProtocol -- used to recognize its own address
        client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        // dynamic addressing phase
        dynamicAddressing();

        // DvR phase
        DVR();

        // chat phase
        chatRoom();

    }


    public static void main(String[] args) {
        if (args.length > 0) {
            frequency = Integer.parseInt(args[0]);
        }
        new MyTestingProtocol(SERVER_IP, SERVER_PORT, frequency);
    }

    public void dynamicAddressing() {
        int address = 0;
        // address will be picked when loop initiates

        System.out.println("Estimated time is 2.5 minutes for convergence. \nMessages sent before the menu is printed will not be sent. \nPress enter to start addressing and DVR.");
        Scanner scan = new Scanner(System.in);
        String text = scan.nextLine(); // only waiting for an enter (to make it easier to start all nodes simultaneously)
        globalTimer = System.currentTimeMillis();

        priorityMap = new HashMap<>();// newest idea to make dynamic addressing faster


        do {
            tryAgain = false;
            Random rand = new Random();
            address = rand.nextInt(127); // to allocate a number in 7 bits
            sendingQueue.clear();
            receivedQueue.clear();
            System.out.println("Your new address: " + address + ". Propagating address list...");
            directions.clear();

            directions.add(address);
            priorityMap.put(address, 0); // places its address on hash map with a value representing the ammount of times such key has
            // propagated


            long timer = System.currentTimeMillis();
            while (System.currentTimeMillis() - timer < addressingTime + extra) {
                int counter = 999;
                int nextAddress = 0;
                for (Integer addy : priorityMap.keySet()) { // takes the address that has been propagated the least
                    if (priorityMap.get(addy) < counter) {
                        nextAddress = addy;
                        counter = priorityMap.get(addy);
                    }
                }
//                System.out.println();
                byte[] pkt = new byte[2]; // encoding of the data short to send single chosen address
                pkt[0] = (byte) 0b010 << 5; // 010 is the identifier for a new incoming address (parsed as a DATA_SHORT)
//                myWait(1000);
                pkt[1] = (byte) nextAddress; // encoding address into the second byte of the data short
                ByteBuffer msg = ByteBuffer.allocate(2);
                msg = ByteBuffer.wrap(pkt);
                msg.put(pkt);
                CSMA(new Message(MessageType.DATA_SHORT, msg)); // sending now as csma
                int newcount = priorityMap.get(nextAddress) + 1; // increases the counter of the address just sent
                priorityMap.put(nextAddress, newcount); // updates
//                for (Integer direction : directions) { // prints addresses
//                    System.out.print(direction + ", ");
//                }

            }
            if (directions.size() == 4) { // waiting to see if any node didnt recieve 4 addresses
                tryAgain = false; // should turn to true if the process needs to restart
                try {
                    sleep(8000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                byte[] trypkt = new byte[2]; // sending a tryagain packet with identifier 001, this removes previous directions and starts again
                trypkt[0] = 0b001 << 5;
                ByteBuffer tryoktBuffer = ByteBuffer.wrap(trypkt);
                long timertry = System.currentTimeMillis();
                while (System.currentTimeMillis() - timertry < 7000) {
                    CSMA(new Message(MessageType.DATA_SHORT, tryoktBuffer)); // propagating tryagain message
                }
                extra += 48000; // adds extra compilation time
            }

        } while (tryAgain);
        users.add("0"); // string representing each node, they will later be needed to change users names
        users.add("1");
        users.add("2");
        users.add("3");

        Collections.sort(directions); // sort the list so the index of all
        // of the nodes can be reduced to two by using their respective index

        System.out.println("\nAll addresses:");
        for (Integer direction : directions) {
            System.out.print(direction + ", ");
        }
        client.setAddress(directions.indexOf(address)); // dynamic addressing concluded
        csmaFlag = true;
    }

    public void DVR() {
        System.out.println("\n \nAddressing complete. Starting DVR... \n");

        // Start of DVR
        forwardingTable = new ForwardingTable(client.getAddress()); // generates a new forwarding table for the node
        receivedQueue.clear();
        sendingQueue.clear();


        while (System.currentTimeMillis() - globalTimer < waiter + extra + addressingTime) { // every iteration of the loop, the node sends
            // its updated version of DVR
//        for(int i = 0; i < 16; i++){
            byte[] dvrPkt = new byte[2];
            // i=indentifier, s= source, h=hops, n= next hop
            // format: iii00000 0ss-hhh-nn

            dvrPkt[0] = (byte) (0b010 << 5);
            dvrPkt[1] = (byte) ((client.getAddress() << 5) | (client.getAddress()));

            byte[] payload = forwardingTable.toBytes();
            byte[] fullpacket = new byte[dvrPkt.length + payload.length];

            System.arraycopy(dvrPkt, 0, fullpacket, 0, dvrPkt.length);
            System.arraycopy(payload, 0, fullpacket, dvrPkt.length, payload.length);

            ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);
            slottedAlohaTables(new Message(MessageType.DATA, bufferPacket));

//             might need to remove
            try {
                sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } // collision precausion due to sending longer messages
        }

        while(!forwardingTable.complete()){
            byte[] tableIncomp = new byte[2];
            tableIncomp[0] = 0b011<<5;
            System.out.println("table incomplete, requesting...");
            CSMA(new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(tableIncomp)));
            try {
                sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void printMessage(Message msg) { // needs no further explanation other than parsing the message and extracting text
        // also dropping any duplicates/retransmissions
        ByteBuffer bb = msg.getData();
        byte[] bytes = bb.array();
        // 000ssdd0 0nnsssff 0pppppoo 00000ttt

        int payloadLen = (bytes[2] >> 2);
        String text = "";
        int sender = bytes[2] & 0b11;
        for (int i = 0; i < payloadLen; i++) {
            text += (char) bytes[4 + i];
        }
        if(!printed.contains(text)){
            System.out.println();
        }
        List<String> words = List.of(text.split(" "));
        if (words.size() == 2 && words.get(0).equals("!name") && !printed.contains(text)) {
            String past = users.get(sender);
            users.set(sender, words.get(1)); // updates the name of the sender in the users String list
            System.out.println("Name of [" + past + "] updated to: " + words.get(1));
        }
        if (!printed.contains(text) && !(words.size() == 2 && words.get(0).equals("!name"))) {
            System.out.print("[" + users.get(sender) + "]: "); // change this sender later
            System.out.println(text);
        }
        printed.add(text);
        if(!printed.contains(text)){
            System.out.print("["+users.get(client.getAddress())+"]: ");
        }

    }

    public void MAC(Message msg) throws InterruptedException {
        boolean trying = true;
        double p = 0.25;
        int time = msg.getType() == MessageType.DATA ? 1500 : 200;
        while (trying) {
            try {
                sleep(time);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (new Random().nextInt(100) < p * 100) {
                sendingQueue.put(msg);
                trying = false;
            }
        }
    }

    public void updateSeq() { // just to update sequence numbers while chatting
        sequenceNum++;
        sequenceNum = sequenceNum % 8;
    }

    public void slottedMAC(Message msg) { // can only be used after dynamic addressing is done
        while (true) {
            Date dateTime = new Date();
            if ((dateTime.getTime() % 1000 > client.getAddress() * 250L) && (dateTime.getTime() % 600 < (client.getAddress() * 250L) + 250)) {
                try {
                    sendingQueue.put(msg);
                } catch (InterruptedException e) {
                    System.exit(2);
                }
                return;
            }
        }
    }

    public synchronized void CSMA(Message msg) {
        synchronized (this){
            double p = 0.25;
            byte[] ident = msg.getData().array();
            if(ident[0] << 5 == 0b0 && msg.getType() == MessageType.DATA_SHORT){ //acks have priority
                System.out.println("1o");
                p = 1;
            }
            while (true) {
//            System.out.println("dsa");
                if (csmaFlag && new Random().nextInt(100) < p * 100) {
                    try {
                        sendingQueue.put(msg);
                        //break?
                    } catch (InterruptedException e) {
                        System.exit(2);
                    }
                    return;
                }
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void slottedAloha(Message msg) {
        synchronized (this){
            while (true) {
                Date date = new Date(System.currentTimeMillis());
                if ((date.getTime() / 4500) % 4 == client.getAddress() && (date.getTime() % 4500 > 2100) && sendingQueue.size() < 2) { // previously 5000
//                System.out.println("Sending " + client.getAddress());
                    try {
                        sendingQueue.put(msg);
                        return;
                    } catch (InterruptedException e) {
                        System.exit(2);
                    }
                }
            }
        }
    }

    public void slottedAlohaTables(Message msg) {
        while (true) {
            Date date = new Date(System.currentTimeMillis());
            if ((date.getTime() / 3000) % 4 == client.getAddress() && (date.getTime() % 3000 > 2100) && sendingQueue.size() < 2) { // previously 5000
//                System.out.println("Sending " + client.getAddress());
                try {
                    sendingQueue.put(msg);
                    return;
                } catch (InterruptedException e) {
                    System.exit(2);
                }
            }
        }
    }

    // Java's own wait() was not working as intended, hence we implemented this method.
    public void myWait(int ms) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
        }
    }

    public void propagatePure(int ms, Message msg) {
        try {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < ms) {

                MAC(msg);
            }

        } catch (InterruptedException e) {
            System.exit(2);
        }
    }

    public void chatRoom() {
        while (System.currentTimeMillis() < addressingTime + waiter + 10000) {
            // wait some time
        }                                   // this print dialog doesnt always appear in the right time while debugging
        sendingQueue.clear();
        receivedQueue.clear();
        System.out.println("Finished! Here is your forwarding table");
        forwardingTable.print();
        System.out.println("\n" + menu);
        System.out.print("[" + users.get(client.getAddress()) + "]: ");
        while (stayAlive) {
            Scanner scan = new Scanner(System.in);
            String text = scan.nextLine();
            List<String> words = List.of(text.split(" "));
            updateSeq();
            if (words.size() == 2 && words.get(0).equals("!name")) {
                users.set(client.getAddress(), words.get(1));
                System.out.println("Name is set to: " + words.get(1));
            }
            if (Objects.equals(text, "!help")) {
                System.out.println(menu);
            } else if (Objects.equals(text, "!submarines")) {
                if (users.size() <= 1) {
                    System.out.println("There are no other submarines in the chat.");
                } else {
                    for (String name : users) {
                        System.out.println(name);
                    }
                }
            } else if (Objects.equals(text, "!table")) {
                System.out.println("This is your forwarding table: \n");
                forwardingTable.print();
            } else if (Objects.equals(text, "!exit")) {
                System.out.println("OK, exiting chatroom");
                stayAlive = false;
                byte[] goodbye = new byte[2];
                goodbye[0] = 0b011 << 5;
                goodbye[1] = (byte) client.getAddress();
                CSMA(new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(goodbye)));
                System.exit(0);
            } else {
                //011ssdd0 0nnqqqf0 ppppp0aa should be the format for the fragmented header

                if (text.length() > 27 && text.length() <= 54) {
                    Message message = null;
                    String part1 = text.substring(0, 27);
                    String part2 = text.substring(27);
                    for (int j = 0; j < 2; j++) {
                        for (int i = 0; i < 3; i++) {
                            byte[] header = new byte[4];
                            byte[] payload;
                            if (j == 0) {
                                payload = part1.getBytes();
                            } else {
                                payload = part2.getBytes();
                            }
                            //000ssdd0 0nnqqqff ppppp0aa
                            header[0] = (byte) (client.getAddress() << 3 | forwardingTable.getNeigbours().get(i) << 1);
                            header[1] = (byte) (forwardingTable.getNextHops().get(i) << 5 | sequenceNum << 2 | (j == 0 ? 0b01 : 0b10));
                            header[2] = (byte) (payload.length << 2 | client.getAddress());
                            header[3] = 0b0;
                            byte[] packet = new byte[header.length + payload.length];
                            System.arraycopy(header, 0, packet, 0, header.length);
                            System.arraycopy(payload, 0, packet, header.length, payload.length);
                            ByteBuffer msg = ByteBuffer.wrap(packet);
                            message = new Message(MessageType.DATA, msg);
                            retransmitList.add(message);
                            new retransmitThread(message).start();
                            try{
                                sleep(4000);
                            }catch (InterruptedException e){
                                System.out.println(e);
                            }

                        }
                    }
                }else if(text.length() > 54) {
                    // The application will support at most 2 fragments
                    System.out.println("Message too long! Please try again");
                }else {
                    // single message format
                    //text message format: 000ssdd0 nnqqqff 0pppppoo +29bytes
                    //s = source, d = destination, q = sequence number, f= fragmentation flg, p = payload length, 29 bytes data/payload allocated
                    Message message;
                    for (int i = 0; i < 3; i++) {
                        byte[] header = new byte[4];
                        byte[] payload = text.getBytes();
                        header[0] = (byte) (client.getAddress() << 3 | forwardingTable.getNeigbours().get(i) << 1);
                        header[1] = (byte) (forwardingTable.getNextHops().get(i) << 5 | sequenceNum << 2); // no frag
                        header[2] = (byte) (payload.length << 2 | client.getAddress());
                        header[3] = 0b0;
                        byte[] packet = new byte[header.length + payload.length];
                        System.arraycopy(header, 0, packet, 0, header.length);
                        System.arraycopy(payload, 0, packet, header.length, payload.length);
                        ByteBuffer msg = ByteBuffer.wrap(packet);
                        message = new Message(MessageType.DATA, msg);
                        System.out.println("Original to: " + ((header[0]>>1) & 0b11));

                        retransmitList.add(message);
                        new retransmitThread(message).start();
                        try{
                            sleep(4000);
                        }catch (InterruptedException e){
                            System.out.println(e);
                        }

                    }
                }
            }


        }
    }

    public void fragmentationFinder(byte key, Message frag, byte fragFlag) {
        if (fragmentationMap.containsKey(key) && fragmentationMap.get(key).get(0) != frag) {
            // we know it has two then we print them in order
            List<Message> fragments = fragmentationMap.get(key);
            if (fragFlag == 0b10) {
                // print frag first
                printMessage(fragments.get(0));
                printMessage(frag);
            } else {
                // print fragments.get(0) first
                printMessage(frag);
                printMessage(fragments.get(0));
            }
        } else if (!(fragmentationMap.containsKey(key))) {
            List<Message> values = new ArrayList<>();
            values.add(frag);
            fragmentationMap.put(key, values);

        }
    }

    public byte[] ackBuilder(int src, int dest, int sequenceNum, int frag) { // generates acks depending in incoming info
        byte[] ack = new byte[2];
        //  000ssdd0 000qqqff
        ack[0] = (byte) ((src << 3) | (dest << 1));
        ack[1] = (byte) ((sequenceNum << 2) | frag);
        return ack;
    }

    private class printThread extends Thread { // not in use rn
        private final Message msg;

        public printThread(Message msg) {
            super();
            this.msg = msg;
        }

        public void run() {

            // ---
            boolean print = true;
            for (ObjReceived obj : printingList) {
//                System.out.println("into the loop");
                if (obj.msg == msg && System.currentTimeMillis() - obj.timestamp < 10000) {
//                    System.out.println("Print shouldnt happen");
                    print = false;
                    obj.timestamp = System.currentTimeMillis();
                    break;
                }
            }

            if (print) {
                printMessage(msg);
                printingList.add(new ObjReceived(System.currentTimeMillis(), msg));
            }
            ObjReceived temp = null;
            for (ObjReceived obj : printingList) {
//                System.out.println("into the loop");
                if (System.currentTimeMillis() - obj.timestamp > 10000) {
                    temp = obj;
                }
            }
            printingList.remove(temp);

        }
    }

    private class retransmitThread extends Thread { // retransmits messages every 5 seconds if no ack corresponding has been recieved
        private final Message msg;
        private int rn;

        public retransmitThread(Message msg) {
            super();
            this.msg = msg;
            Random rand = new Random();
            rn = rand.nextInt(200);
        }

        @Override
        public void run() {
            int src = msg.getData().get(0) >> 3;
            int dst = (msg.getData().get(0) >> 1) & 0b11;
            int nxt = msg.getData().get(1) >> 5;
            int seq = (msg.getData().get(1) >> 2) & 0b111;
            int frag = msg.getData().get(1) & 0b11;
            while (retransmitList.contains(msg)) {
                System.out.println(rn+ ": Retransmit from " + src + " to " + dst + " with #: " + seq );
                if(retransmitList.contains(msg)){
                    CSMA(msg);
                }

//                if (!retransmitList.contains(msg)) {
//                    retransmitList.add(msg);
//                }
                try {
                    sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    public class ackThread extends Thread{
        private final Message msg;

        public ackThread(Message msg) {
            super();
            this.msg = msg;
        }

        @Override
        public void run() {
            System.out.println("Ack");
            CSMA(msg);
        }
    }

    private class receiveThread extends Thread {
        private final BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
        }

        public void run() { //receiving node
            while (true) {
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.DATA) {
                        if (directions.size() == 4 && m.getData().get(0) >> 5 == 0b010) { // added the && to avoid this if

                            // all the rest and after DVR
                            // format iii00000 0dd-hhh-nn
                            if (m.getData().get(0) >> 5 == 0b010 && ((m.getData().get(1) & 0b11100) >> 2) != 0b11) { // 010 is the identifier for DVR
                                int src = m.getData().get(1) >> 5;
                                int hops = (m.getData().get(1) >> 2) & 0b111;
                                int sender = m.getData().get(1) & 0b11;

                                hops++;
                                ForwardingTable neighbour = new ForwardingTable(m.getData().array());
                                if (forwardingTable == null) {
                                    forwardingTable = new ForwardingTable(client.getAddress());
                                }
                                if (hops < forwardingTable.getCost(src)) {
                                    forwardingTable.newRoute(src, hops, sender);
                                }
                                forwardingTable.mergeTables(neighbour);
//                                forwardingTable.print();
                            }
                        } else if (m.getData().get(0) >> 5 == 0b0 && !(m.getData().get(3) > 0b100)) {

                            //text message format: 000ssdd0 0nnqqqff pppppoo0 00000ttt +29bytes
                            //ss=source; dd= destination; nn= next hop; qqq= seq no; ff=fragmentation
                            //ff== 00 -> no fragmentation; ff== 01 ->frag + first packet from frag;
                            //ff== 10 -> frag + last packet

                            int src = m.getData().get(0) >> 3;
                            int dst = (m.getData().get(0) >> 1) & 0b11;
                            int nxt = m.getData().get(1) >> 5;
                            int seq = (m.getData().get(1) >> 2) & 0b111;
                            int frag = m.getData().get(1) & 0b11;
                            int payld = m.getData().get(2) >> 2;
                            int sende = m.getData().get(2) & 0b11;
                            int ttl = m.getData().get(3) & 0b111;
                            if (dst == client.getAddress()) {
                                System.out.println("received message and im the dest");
//                                byte[] ack = ackBuilder(dst, src, seq, frag);
                                byte[] ack = new byte[2];
                                ack[0] = m.getData().get(0);
                                ack[1] = m.getData().get(1);
                                Message msg = new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(ack));

                                new ackThread(msg).start();
                                if (frag == 0b00) {
                                    //send message to print buffer

                                    new printThread(m).start();

                                } else {
                                    // this should mean fragmentation, it was paused as baris wanted to try something else
                                    byte[] id = new byte[1];
                                    id[0] = (byte) ((src << 6) | (dst << 4) | (seq << 1));
                                    fragmentationFinder(id[0], m, (byte) frag);
                                }

                            } else if (nxt == client.getAddress()) {
                                //send packet to the updated next hop based on FT
                                //dst remains same, source is current node, next is modified
//                                byte[] ack = ackBuilder(dst, src, seq, frag);
                                ttl +=1;
                                byte[] ack = new byte[2];
                                ack[0] = m.getData().get(0);
                                ack[1] = m.getData().get(1);
                                Message msg = new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(ack));
                                new ackThread(msg).start();

                                boolean containsOlder = true;

                                if(receivedMap.size()!=0) {
                                    while (containsOlder) {
                                        Message rep = null;
                                        containsOlder = false;
                                        for (Message ackMessage : receivedMap.keySet()) {
                                            System.out.println(ackMessage + ", this message is: " + (System.currentTimeMillis()-receivedMap.get(ackMessage)) + " long");
                                            if (System.currentTimeMillis() - receivedMap.get(ackMessage) > 10000) {
                                                rep = ackMessage;
                                                System.out.println("removing older messages");
                                                containsOlder = true;
                                            }
                                        }
                                        receivedMap.remove(rep);
                                    }
                                }
                                if(!receivedMap.containsKey(m)){
                                    receivedMap.put(m, System.currentTimeMillis());
                                    int newNextHop = forwardingTable.getNextHop(dst); // this has to be the next hop of the destination
                                    byte[] header = new byte[4];
                                    header[0] = (byte) ((client.getAddress() << 3) | dst << 1);
                                    header[1] = (byte) ((newNextHop << 5) | (seq << 2) | frag);
                                    header[2] = m.getData().get(2);
                                    header[3] = (byte) ttl;

                                    byte[] payload = Arrays.copyOfRange(m.getData().array(), 4, m.getData().array().length); // could be -1

                                    byte[] packet = new byte[header.length + payload.length];
                                    System.arraycopy(header, 0, packet, 0, header.length);
                                    System.arraycopy(payload, 0, packet, header.length, payload.length);
                                    Message message = new Message(MessageType.DATA, ByteBuffer.wrap(packet));

                                    // ask yourself have i received this packet before.
                                    // yes: dont retrasmit, only ack..
                                    // no: retransmit and ack
                                    if(!ackList.contains(m)){
                                        ackList.add(m);
//                                        System.out.println("Rerouting message to: " + newNextHop + ", with destination: " + dst);
                                        retransmitList.add(message);
                                        new retransmitThread(message).start();
                                    }else{
                                        System.out.println("avoided retransmitting for nothing");
                                    }
                                }


                            }
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT) {
                        if (m.getData().get(0) >> 5 == 0b010 && directions.size() < 4) { // catches a neighbour for dynamic addressing
                            int neighbour = m.getData().get(1) & 0b01111111;
                            if (!directions.contains(neighbour)) {
                                priorityMap.put(neighbour, 0);
                                directions.add(neighbour);
                                if (directions.size() < 4) {
                                } else if (directions.size() == 4) {
                                    endFlood = true;
                                }
                            }
                        } else if (m.getData().get(0) >> 5 == 0b0) { // acknowledgement builder for incoming text messages
                            // check if pending is waiting on this ack to avoid retransmission
                            // packet format: 000ssdd0 0nnqqqff ppppp000
                            // ack    format: 000ssdd0 0nnqqqff
                            int src = m.getData().get(0) >> 3;
                            int dst = m.getData().get(0) >> 1 & 0b11;
                            int sequence = m.getData().get(1) & 0b11111;
                            if (client.getAddress() == src) {
                                Message temp = null;
                                for (Message msg : retransmitList) {
                                    byte[] info = msg.getData().array();
                                    int srcofMsg = info[0] >> 3;
                                    int nextHop = info[1] >> 5; // 000SSDD0
                                    int sequenceOfNumWFlag = info[1] & 0b11111; // 000QQQFF
                                    if (info[0] == m.getData().get(0) && info[1] == m.getData().get(1)) {
                                        System.out.println("Message from " + dst + " to " + src + " with #" + sequence + " will be removed");
                                        temp = msg;
                                        break;
                                    }
                                }
                                retransmitList.remove(temp); // removes message waiting to be sent again
                                if(retransmitList.contains(temp)){
                                    System.out.println("removing from ack list unsuccessfull");
                                }

                            }
                        } else if (m.getData().get(0) >> 5 == 0b001) { // to loop back into dynamic addressing if it fails
                            tryAgain = true;
                            long lasttimer = System.currentTimeMillis();
                            while (System.currentTimeMillis() - lasttimer < 2000) {
                                CSMA(m);
                            }
                            extra += 48000;
                        } else if (m.getData().get(0) >> 5 == 0b011){
                            // DVR of neighbour incomplete

                            byte[] dvrPkt = new byte[2];
                            // i=indentifier, s= source, h=hops, n= next hop
                            // format: iii00000 0ss-hhh-nn

                            dvrPkt[0] = (byte) (0b010 << 5);
                            dvrPkt[1] = (byte) ((client.getAddress() << 5) | (client.getAddress()));

                            byte[] payload = forwardingTable.toBytes();
                            byte[] fullpacket = new byte[dvrPkt.length + payload.length];

                            System.arraycopy(dvrPkt, 0, fullpacket, 0, dvrPkt.length);
                            System.arraycopy(payload, 0, fullpacket, dvrPkt.length, payload.length);

                            ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);
                            slottedAlohaTables(new Message(MessageType.DATA, bufferPacket));

                        }
                    } else if (m.getType() == MessageType.END) {
                        System.exit(0);
                    } else if (m.getType() == MessageType.BUSY) {
                        csmaFlag = false;
                    } else if (m.getType() == MessageType.FREE) {
                        csmaFlag = true;
                    }
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from queue: " + e);
                }

            }
        }
    }
}