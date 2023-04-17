package client;

public class PacketACK {
    public long timestamp;
    public Message msg;
    public PacketACK(long time, Message message){
        msg = message;
        timestamp = time;
    }
}
