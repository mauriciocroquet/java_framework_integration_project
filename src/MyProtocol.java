import client.*;
import org.w3c.dom.ls.LSOutput;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
    private final int INFINITY= 1000000;
    private FTable forwardingTable;

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

    public ByteBuffer TransmitForwardingPacket(Packet packet) throws InterruptedException {
        // allocate packet structure in ByteBuffer
        byte[] packetHeader = new byte[3];
        int source = packet.getSource();
        int hasDestination = packet.isRaw()? 0:1; // isRaw means it contains raw bits instead of a forwarding table
        int destination = packet.getDestination();
        packetHeader[0] = (byte)source;
        packetHeader[1] = (byte)hasDestination; // we need to shift the bits to allocate src,dst,flag in one byte
        packetHeader[2] = (byte)destination;

        // include the forwarding table of the packet in these bits
        byte[] packetData;
        if(!packet.isRaw()){
            FTable fTable = packet.getForwardingtable();
            packetData = new byte[8 + 4 * fTable.getColumns() * fTable.getRows()]; // max = 8 + (4*4*3)

            int nRows = packet.getForwardingtable().getRows();
            packetData[0] = (byte) (nRows >> 24);
            packetData[1] = (byte) (nRows >> 16);
            packetData[2] = (byte) (nRows >> 8);
            packetData[3] = (byte) (nRows >> 0);

            int nColumns = packet.getForwardingtable().getColumns();
            packetData[4] = (byte) (nColumns >> 24);
            packetData[5] = (byte) (nColumns >> 16);
            packetData[6] = (byte) (nColumns >> 8);
            packetData[7] = (byte) (nColumns >> 0);

            for (int i = 0; i < packet.getForwardingtable().getRows(); i++) {
                for (int j = 0; j < packet.getForwardingtable().getColumns(); j++) {

                    int cellData = packet.getForwardingtable().get(i, j);
                    packetData[8 + i * packet.getForwardingtable().getColumns() * 4 + j * 4 + 0] = (byte) (cellData >> 24);
                    packetData[8 + i * packet.getForwardingtable().getColumns() * 4 + j * 4 + 1] = (byte) (cellData >> 16);
                    packetData[8 + i * packet.getForwardingtable().getColumns() * 4 + j * 4 + 2] = (byte) (cellData >> 8);
                    packetData[8 + i * packet.getForwardingtable().getColumns() * 4 + j * 4 + 3] = (byte) (cellData >> 0);
                }
            }
        }else{
            packetData = new byte[0];
        }

        byte[] fullPacket = new byte[packetHeader.length + packetData.length];
        System.arraycopy(packetHeader, 0, fullPacket, 0, packetHeader.length);
        System.arraycopy(packetData, packetHeader.length, fullPacket, packetHeader.length, packetData.length);

        return ByteBuffer.wrap(fullPacket);
    }

    public void initiateTable(int address){
        for(int i = 0; i < 4; i ++){
            Integer[] row = new Integer[3];
            if(i == address){
                row[1] = 0;
            }else{
                row[1] = INFINITY;
            }
            row[0] = i;
            row[2] = i;
            forwardingTable.addRow(row);
        }
    }

    public boolean completeTable(){
        for(int i = 0; i < 4; i ++){
            if(forwardingTable.get(i, 1) == INFINITY){
                return false;
            }
        }
        return true;
    }
    
    public FTable decodeFTable(ByteBuffer data){
        FTable newTable = new FTable(3);
        byte[] packetData = data.array();
        ByteBuffer wrapped;

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                wrapped = ByteBuffer.wrap(packetData,
                        3 + 4 * i * 3 + 4 * j, 4); // we need to change this to the proper offset of when the ftable starts
                wrapped.order(ByteOrder.BIG_ENDIAN);            // the offset is probably wrong
                int cellData = wrapped.getInt();

                newTable.set(i, j, cellData);
            }
        }
        return newTable;
    }

    public void updateTable(FTable neighbourTable, int costWithNeighbour, int neighbourAddress){
        for(int i = 0; i < 4; i++){ // number of rows
            if (forwardingTable.get(i, 1) > neighbourTable.get(i, 1) + costWithNeighbour){
                forwardingTable.set(i, 1, neighbourTable.get(i, 1) + costWithNeighbour); // update cost
                forwardingTable.set(i, 2, neighbourAddress); // update next hop
            }
        }
    }

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

        // Create an initial forwarding table: should only contain address, cost, nextHop, in said order on the table
        initiateTable(address);

        boolean complete = false; // comment trial

        while(!complete){
            // flooding for routing
            wait(5000); // 5 seconds to allow all nodes to start and send their first packet
            // formatting the tables by propagating packets with src, destination and flags
            ByteBuffer flood = TransmitForwardingPacket(new Packet(address, 0, forwardingTable)); // generate an empty pkt (only src)
            while(!(System.currentTimeMillis() % 4 == address)){
            }
            sendingQueue.put(new Message(MessageType.DATA, flood));
            long startTime = System.currentTimeMillis();                        //needs endtime FOR TIME OUT LATER
            while (true){
                try {
                    ByteBuffer reply = ByteBuffer.allocate(32);
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.DATA) {
                        //m.getData().getInt()
                        System.out.println(m.getData().get());
                        // forwarding table construction
                        if(m.getData().get(1) == 0 ) // message only contains source
                            //the 6th bit from right to left is set to 0 (from the total of 1 byte)
                        {   //
//                            int dst=(m.getData().get(0) & ((1<<7)+ (1<<6))) >>6; //src sent
//                            int dst_flag=1;
                            //I have all the info, how do I combine in into reply?
                            //src is address, dst_flag, dst, 3 0s
                            // send back with src as destination and new src + flag
                            reply = TransmitForwardingPacket(new Packet(address, m.getData().get(0), forwardingTable));
                        }else if(m.getData().get(1) == 1 && m.getData().get(2) == address) // message contains destination and such destination is equal to that nodes address
                            //the 4th and 5th bites form the address of the node
                        {
                            long endTime = System.currentTimeMillis();
                            long total = endTime-startTime; //should be rounded to ~0.1s
                            // add this to the routing table if this time is shorter than any other entry
                            // add to routing table

                            FTable neighbourTable = decodeFTable(m.getData());
                            updateTable(neighbourTable, (int) total, m.getData().get(0)); // updates our forwarding table

                            reply.put(new Message(MessageType.DATA, ));
                        }else // message contains destination but that isnt equal to that nodes address
                        {
                            // propagate message to all other nodes but with src as that nodes address
                            // this is because some nodes cant reach others so they depend on others to reach the remaining nodes
                            reply.put(new Message(MessageType.DATA_SHORT, ));
                        }
                        while(!(System.currentTimeMillis() % 4 == address)){ // round robin
                        }
                        sendingQueue.put(new Message(MessageType.DATA_SHORT, reply));
                    }
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: " + e);
                }
            }

            if(completeTable()){
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

