package client;

public class ObjReceived {
    public long timestamp;
    public Message msg;
    public ObjReceived(long time, Message msg){
        this.timestamp = time;
        this.msg = msg;
    }
}