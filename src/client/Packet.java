package client;

import java.util.ArrayList;
import java.util.List;

public class Packet {
    private boolean isRaw;
    private final byte[] rawData;
    private int destination;
    private int source;

    private FTable forwardingtable;

    public Packet(int src, int dst, FTable fTable){ // forwarding table
        this.source = src;
        this.destination = dst;
        this.forwardingtable = fTable;
        this.isRaw = false;
        this.rawData = null;
    }

    public Packet(int src, int dst, byte[] rawData){ // text
        this.source = src;
        this.destination = dst;
        this.forwardingtable = null;
        this.isRaw = true;
        this.rawData = rawData;
    }

    public int getSource() {
        return source;
    }
    public int getDestination(){
        return destination;
    }

    public FTable getForwardingtable() {
        return forwardingtable;
    }

    public boolean isRaw() {
        return isRaw;
    }

    public byte[] getRawData() {
        return rawData;
    }
}
