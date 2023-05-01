package client;

import java.nio.ByteBuffer;

public class Message {
    private MessageType type;
    private ByteBuffer data;


    public Message(MessageType type){
        this.type = type;
    }

//    //for data
//    public Message(MessageType type, int identifier, int source, int dest, int frag, ){
//        this.type = type;
//    }

    public Message(MessageType type, ByteBuffer data){
        this.type = type;
        this.data = data;
    }

    public MessageType getType(){
        return type;
    }

    public ByteBuffer getData(){
        return data;
    }
    public int getIdentify(){
        return data.get(0);
    }
}