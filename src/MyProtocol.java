import client.*;
import com.sun.source.tree.IntersectionTypeTree;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Date;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MyProtocol {

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
    private final List<Message> retransmitList = new ArrayList<>();//ack list
    private final List<ObjReceived> ackList = new ArrayList<>();

    private final List<String> printed = new ArrayList<>();
    private final HashMap<Byte, List<Message>> fragmentationMap = new HashMap<>();
    private final long globalTimer;
    private final int addressingTime = 50000;
    private final int waiter = 90000;
    private final List<String> users = new ArrayList<>();
    private boolean stayAlive = true;
    private final String menu =
            "COMMANDS: \n" +
                    "!name [NAME] ............ Enter your name by typing !name, a spacebar, then a name of your choice. \n" +
                    "!help ................... Print out these commands if you forget what there are. \n" +
                    "!submarines ............. Display all current online submarines (nodes) in the chat. \n" +
                    "!table .................. Print your routing table \n" +
                    "!exit ................... Exit the program and any chat you are in. \n";
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    boolean endFlood = false;
    private ForwardingTable forwardingTable;
    private int sequenceNum = 0;
    private boolean tryAgain = true;
    private int extra = 0;

    public MyProtocol(String server_ip, int server_port, int frequency) {

        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        client = new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!
        int address = 0;

        System.out.println("Estimated time is 5 minutes for convergence. Messages \nsent before the menu is printed will not be sent. \nPress enter to start addressing and DVR.");
        Scanner scan = new Scanner(System.in);
        String text = scan.nextLine();
        globalTimer = System.currentTimeMillis();

        do {
            tryAgain = false;
            Random rand = new Random();
            address = rand.nextInt(127);
            System.out.println("Your new address: " + address + ". Propagating address list...");
            directions.clear();

            directions.add(address);


            long timer = System.currentTimeMillis();
            while (System.currentTimeMillis() - timer < addressingTime + extra) {
                System.out.println();
                Random random = new Random();
                int randomElement = directions.get(random.nextInt(directions.size()));
                byte[] pkt = new byte[2];
                pkt[0] = (byte) 0b010 << 5;
                myWait(1000);
                pkt[1] = (byte) randomElement;
                ByteBuffer msg = ByteBuffer.allocate(2);
                msg = ByteBuffer.wrap(pkt);
                msg.put(pkt);
                try {
                    MAC(new Message(MessageType.DATA_SHORT, msg));
                } catch (InterruptedException e) {
                    System.exit(2);
                }
                for(Integer direction: directions){
                    System.out.print(direction + ", ");
                }

            }
            if (directions.size() == 4) {
                tryAgain = false;
                myWait(8000);
            } else {
                byte[] trypkt = new byte[2];
                trypkt[0] = 0b001 << 5;
                ByteBuffer tryoktBuffer = ByteBuffer.wrap(trypkt);
                long timertry = System.currentTimeMillis();
                while (System.currentTimeMillis() - timertry < 7000) {
                    try {
                        MAC(new Message(MessageType.DATA_SHORT, tryoktBuffer));
                    } catch (InterruptedException e) {
                        System.exit(2);
                    }
                }
                extra += 40000;
            }

        } while (tryAgain);
        users.add("0");
        users.add("1");
        users.add("2");
        users.add("3");

        Collections.sort(directions); // sort the list so the index of all
        // of the nodes can be reduced to two by using their respective index

        System.out.println("All addresses:");
        for (Integer direction : directions) {
            System.out.print(direction + ", ");
        }
        System.out.println("\n \nAddressing complete. Starting DVR... \n");
        client.setAddress(directions.indexOf(address));
        // Start of DVR
        forwardingTable = new ForwardingTable(client.getAddress());
        receivedQueue.clear();
        sendingQueue.clear();





        /**
         * These next commented out lines are for the attempt of implementing slotted Aloha.
         */
        while (System.currentTimeMillis() - globalTimer < waiter + extra + addressingTime) {
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
            slottedAloha(new Message(MessageType.DATA, bufferPacket));
            myWait(5000);
        }
        chatRoom();

    }



    public static void main(String[] args) {
        if (args.length > 0) {
            frequency = Integer.parseInt(args[0]);
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);
    }

    public void printMessage(Message msg) {
        ByteBuffer bb = msg.getData();
        byte[] bytes = bb.array();
        int payloadLen = (bytes[2] >> 2);
        String text = "";
        int sender = bytes[2] & 0b11;
        for (int i = 0; i < payloadLen; i++) {
            text += (char) bytes[3 + i];
        }
        List<String> words = List.of(text.split(" "));
        if (words.size() == 2 && words.get(0).equals("!name")) {
            users.set(sender, words.get(1));
        }
        if (!printed.contains(text) && !(words.size() == 2 && words.get(0).equals("!name"))) {
            System.out.print("[" + users.get(sender) + "]: "); // change this sender later
            System.out.println(text);
        }
        printed.add(text);

    }
    public void MAC(Message msg) throws InterruptedException {
        boolean trying = true;
        double p = 0.25;
        int time = msg.getType() == MessageType.DATA ? 1500 : 200;
        while (trying) {
            myWait(time);
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

    public void slottedAloha(Message msg){
        while(true){
            Date date = new Date(System.currentTimeMillis());
            if((date.getTime()/5000)%4==client.getAddress() && (date.getTime()%5000 > 2100) && sendingQueue.size()<2){
                System.out.println("Sending " + client.getAddress());
                try{
                    sendingQueue.put(msg);
                    return;
                }catch(InterruptedException e){
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

    public void propagatePureTables(int ms, Message msg) {
        try {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < ms) {
                myWait(13000);
                MAC(msg);
            }

        } catch (InterruptedException e) {
            System.exit(2);
        }
    }

    public void propagateSlotted(int ms, Message msg) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < ms) {
            slottedMAC(msg);
        }

    }

    public void chatRoom() {
        while (System.currentTimeMillis() < addressingTime + waiter + 10000) {
            // wait some time
        }
        System.out.println("Finished! Here is your forwarding table");
        forwardingTable.print();
        System.out.println("\n" + menu);
        while (stayAlive) {
            System.out.print("[" + users.get(client.getAddress()) + "]: ");
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
                goodbye[0] = 0b011<<5;
                goodbye[1] = (byte) client.getAddress();
                propagatePure(1000, new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(goodbye)));
                System.exit(0);
            } else {
                if (text.length() >= 30 && text.length() <= 58) { // this is for longer packets
                    Message message = null;
                    String part1 = text.substring(0, 28);
                    String part2 = text.substring(29);
                    for (int j = 0; j < 2; j++) {
                        for (int i = 0; i < 3; i++) {
                            byte[] header = new byte[3];
                            byte[] payload;
                            if (j == 0) {
                                payload = part1.getBytes();
                            } else {
                                payload = part2.getBytes();
                            }
                            header[0] = (byte) (client.getAddress() << 3 | forwardingTable.getNeigbours().get(i) << 1);
                            header[1] = (byte) (forwardingTable.getNextHops().get(i) << 5 | sequenceNum << 2 | (j == 0 ? 0b01 : 0b10));
                            header[2] = (byte) (payload.length << 2 | client.getAddress());
                            byte[] packet = new byte[header.length + payload.length];
                            System.arraycopy(header, 0, packet, 0, header.length);
                            System.arraycopy(payload, 0, packet, header.length, payload.length);
                            ByteBuffer msg = ByteBuffer.wrap(packet);
                            message = new Message(MessageType.DATA, msg);
                            new retransmitList(message).start();
                        }
                    }

                } else if (text.length() > 59) {
                    System.out.println("Text is too long, please shorten it");
                } else {
                    //text message format: 000ssdd0 nnqqqff ppppp000 +29bytes
                    //s = source, d = destination, q = sequence number, f= fragmentation flg, p = payload length, 29 bytes data/payload allocated
                    Message message;
                    for (int i = 0; i < 3; i++) {
                        byte[] header = new byte[3];
                        byte[] payload = text.getBytes();
                        header[0] = (byte) (client.getAddress() << 3 | forwardingTable.getNeigbours().get(i) << 1);
                        header[1] = (byte) (forwardingTable.getNextHops().get(i) << 5 | sequenceNum << 2); // no frag
                        header[2] = (byte) (payload.length << 2 | client.getAddress());
                        byte[] packet = new byte[header.length + payload.length];
                        System.arraycopy(header, 0, packet, 0, header.length);
                        System.arraycopy(payload, 0, packet, header.length, payload.length);
                        ByteBuffer msg = ByteBuffer.wrap(packet);
                        message = new Message(MessageType.DATA, msg);
                        new retransmitList(message).start();
                    }
                }
            }


        }
    }

    public void fragmentationFinder(byte key, Message frag) {
        if (fragmentationMap.containsKey(key)) {
            // we know it has two then we print them in order
            List<Message> fragments = fragmentationMap.get(key);
            if ((fragments.get(0).getData().array()[1] & 0b1) < (frag.getData().array()[1] & 0b1)) {
                // print frag first
                printMessage(frag);
                printMessage(fragments.get(0));
            } else {
                // print fragments.get(0) first
                printMessage(fragments.get(0));
                printMessage(frag);
            }
        } else {
            List<Message> values = new ArrayList<>();
            values.add(frag);
            fragmentationMap.put(key, values);

        }
    }

    public byte[] ackBuilder(int src, int dest, int sequenceNum, int frag) {
        byte[] ack = new byte[2];
        ack[0] = (byte) ((src << 3) | (dest << 1));
        ack[1] = (byte) ((sequenceNum << 2) | frag);
        return ack;
    }

    private class printThread extends Thread {
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
    private class retransmitList extends Thread {
        private final Message msg;

        public retransmitList(Message msg) {
            super();
            this.msg = msg;
        }

        @Override
        public void run() {
            do {
                slottedAloha(msg);
                if (!retransmitList.contains(msg)) {
                    retransmitList.add(msg);
                }
                myWait(10000);

            } while (retransmitList.contains(msg));
        }
    }

    private class receiveThread extends Thread {
        private final BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
        }

        public void run() {
            while (true) {
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.DATA) {
                        if (m.getType() == MessageType.DATA && m.getData().get(0) >> 5 == 0b011) {
                            if (directions.size() != 4) {
                                directions.clear();
                                // type: DATA, Parsing: 01100000 0xxxxxxx 0yyyyyyy 0zzzzzzz 0ooooooo
                                directions.add((int) m.getData().get(1));
                                directions.add((int) m.getData().get(2));
                                directions.add((int) m.getData().get(3));
                                directions.add((int) m.getData().get(4));
                            }

                            propagatePureTables(1500, m);
                        } else if (directions.size() == 4 && m.getData().get(0) >> 5 == 0b010) { // added the && to avoid this if
                            // all the rest and after DVR
                            // format iii00000 0dd-hhh-nn
                            if (m.getData().get(0) >> 5 == 0b010 && ((m.getData().get(1) & 0b11100) >> 2) != 0b11) { // 010 is the identifier for DVR
                                int src = m.getData().get(1) >> 5;
                                int hops = (m.getData().get(1) >> 2) & 0b111;
                                int sender = m.getData().get(1) & 0b11;

                                hops++;
//                                System.out.println("It took: " + hops + " hops to get from " + src + " to " + client.getAddress() + ", nb: " + sender );
                                ForwardingTable neighbour = new ForwardingTable(m.getData().array());
                                if (forwardingTable == null) {
                                    forwardingTable = new ForwardingTable(client.getAddress());
                                }
                                if (hops < forwardingTable.getCost(src)) {
                                    forwardingTable.newRoute(src, hops, sender);
                                }

                                forwardingTable.mergeTables(neighbour);
                                forwardingTable.print();
//                                forwardingTable.print();
                                //now change sender and put it in the sending queue
                                //send a new updated message of the FT
                                byte[] header = new byte[2];
                                header[0] = (byte) (0b010 << 5);
                                //make new packet of hops 0, sender you, source you, payload your FT
                                sender = client.getAddress();
                                header[1] = (byte) (src << 5 | hops << 2 | sender);
                                byte[] payload = forwardingTable.toBytes();
                                byte[] fullpacket = new byte[header.length + payload.length];

                                System.arraycopy(header, 0, fullpacket, 0, header.length);
                                System.arraycopy(payload, 0, fullpacket, header.length, payload.length);

                                ByteBuffer bufferPacket = ByteBuffer.wrap(fullpacket);

                                if(System.currentTimeMillis() - globalTimer < waiter + extra + addressingTime) {
                                    slottedAloha(new Message(MessageType.DATA, bufferPacket));
                                }



                            }
                        } else if (m.getData().get(0) >> 5 == 0b0 && !ackList.contains(m)) {
                            //text message format: 000ssdd0 nnqqqff ppppp000 +29bytes
                            //ss=source; dd= destination; nn= next hop; qqq= seq no; ff=fragmentation
                            //ff== 00 -> no fragmentation; ff== 01 ->frag + first packet from frag;
                            //ff== 10 -> frag + last packet

                            int src = m.getData().get(0) >> 3;
                            int dst = (m.getData().get(0) >> 1) & 0b11;
                            int nxt = m.getData().get(1) >> 5;
                            int seq = (m.getData().get(1) >> 2) & 0b111;
                            int frag = m.getData().get(1) & 0b11;
                            int payld = m.getData().get(2) >> 2;
                            int sender = m.getData().get(2) & 0b11;
                            if (dst == client.getAddress()) {

                                byte[] ack = ackBuilder(client.getAddress(), src, seq, frag);
                                Message msg = new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(ack));
//                                ackList.add(new ObjReceived(System.currentTimeMillis(), m));
//                                new ackThread(msg).start();
                                slottedAloha( msg);
                                // there is still a need to ack other msgs (not for u but in the path of the node)
                                // ack thread

                                if (frag == 0b00) {
                                    //send message to print buffer

                                    new printThread(m).start();

                                } else {
                                    byte[] id = new byte[1];
                                    id[0] = (byte) ((src << 6) | (dst << 4) | (seq << 1) | ((frag & 0b10) >> 1));
                                    fragmentationFinder(id[0], m);
                                }
                            } else if (nxt == client.getAddress()) {
                                //send packet to the updated next hop based on FT
                                //dst remains same, source is current node, next is modified
                                byte[] ack = ackBuilder(nxt, src, seq, frag);
                                Message msg = new Message(MessageType.DATA_SHORT, ByteBuffer.wrap(ack));
                                slottedAloha( msg);

                                int newNextHop = forwardingTable.getNextHop(dst); // this has to be the next hop of the destination
                                byte[] header = new byte[3];
                                header[0] = (byte) ((client.getAddress() << 3) | dst << 1);
                                header[1] = (byte) ((newNextHop << 5) | (seq << 2) | frag);
                                header[2] = m.getData().get(2);

                                byte[] payload = Arrays.copyOfRange(m.getData().array(), 3, m.getData().array().length); // could be -1


                                byte[] packet = new byte[header.length + payload.length];
                                System.arraycopy(header, 0, packet, 0, header.length);
                                System.arraycopy(payload, 0, packet, header.length, payload.length);
                                // nunca lo estabamos mandando
                                System.out.println("Should send this to someone else");
                                System.out.println("This is address: " + client.getAddress() + " and sending it to: " + newNextHop);
                                new retransmitList(new Message(MessageType.DATA, ByteBuffer.wrap(packet))).start();
                            }
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT) {
                        if (m.getData().get(0) >> 5 == 0b010 && directions.size() < 4) {
                            int neighbour = m.getData().get(1) & 0b01111111;
                            if (!directions.contains(neighbour)) {
                                directions.add(neighbour);
                                if (directions.size() < 4) {
                                } else if (directions.size() == 4) {
                                    endFlood = true;
                                }
                            }
                        } else if (m.getData().get(0) >> 5 == 0b011) {
                            endFlood = true;
                            propagatePure(1000, m);
                        } else if (m.getData().get(0) >> 5 == 0b0) {
                            // check if pending is waiting on this ack to avoid retransmission
                            int src = m.getData().get(0) >> 3;
                            int dst = m.getData().get(0) >> 1 & 0b11;
                            int sequence = m.getData().get(1) & 0b11111;
                            if (client.getAddress() == dst) {
                                Message temp = null;
                                for (Message msg : retransmitList) {
                                    byte[] info = msg.getData().array();
                                    int srcofMsg = info[0] >> 3;
                                    int nextHop = info[1] >> 5; // 000SSDD0
                                    int sequenceOfNumWFlag = info[1] & 0b11111; // 000QQQFF
                                    if (srcofMsg == dst && nextHop == src && sequenceOfNumWFlag == sequence) {
                                        temp = msg;
                                        break;
                                    }
                                }
                                retransmitList.remove(temp);

                            }
                        } else if (m.getData().get(0) >> 5 == 0b001) {
                            tryAgain = true;
                            long lasttimer = System.currentTimeMillis();
                            while (System.currentTimeMillis() - lasttimer < 2000) {
                                MAC(m);
                            }
                            extra += 40000;
                        }
                    } else if (m.getType() == MessageType.END) {
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from queue: " + e);
                }

            }
        }
    }
}