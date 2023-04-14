import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    boolean endFlood = false;

    public void MAC(Message msg) throws InterruptedException {
        boolean trying = true;
        double p = 0.25;
        while(trying){
            long start = System.currentTimeMillis();
            while(System.currentTimeMillis()-start < 500){

            }
            if (new Random().nextInt(100) < p * 100) {
                sendingQueue.put(msg);
                trying = false;
            }
        }
    }

    public MyProtocol(String server_ip, int server_port, int frequency){
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        while(directions.size() < 4){
            Random rand = new Random();
            int address = rand.nextInt(31);
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

            // si es menos de 4, espera un rato para que todos se den cuenta y vacia tu receiving queue

        }
        for(Integer direction: directions){
            System.out.print(direction + ", ");
        }
        System.out.println(" Should be the completed direction list");
        // now end the while loops of others
        ByteBuffer end = ByteBuffer.allocate(1);
        byte endSignal = 0b01100000;
        end.put(endSignal);

        try{
            long start = System.currentTimeMillis();
            while(System.currentTimeMillis()-start < 1000){
                MAC(new Message(MessageType.DATA_SHORT, end));
            }

        }catch (InterruptedException e){
            System.exit(2);
        }

        receivedQueue.clear();

        // propaga esa lista un rato


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
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                    } else if (m.getType() == MessageType.DATA_SHORT){
                        System.out.print("DATA_SHORT: ");
                        if(m.getData().get(0) >> 5 == 0b010){
                            System.out.println("Address " + (m.getData().get(0) & 0b00011111 ) + " was obtained");
                            int neighbour = m.getData().get(0) & 0b00011111;
                            if(!directions.contains(neighbour)) {
                                directions.add(neighbour);
                                for (Integer direction : directions) {
                                    System.out.print(direction + ", ");
                                }

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

