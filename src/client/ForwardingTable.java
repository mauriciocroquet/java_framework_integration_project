package client;

import java.util.ArrayList;
import java.util.List;

public class ForwardingTable {
    public int INF = 100000;
    private Integer[][] fTable = new Integer[4][3];
    private int address;
    public ForwardingTable(int address){    // Format of the row: Destination : Cost : NextHop
        this.address = address;
        for(int i = 0; i < 4; ++i){
            fTable[i][0] = i;               // Initially al the values of the first row are equal to the respective addresses
            fTable[i][2] = i;               // and the next hop to all addresses are themselves
            if(i == address){
                fTable[i][1] = 0;           // only cost set to 0 is the address itself
            }else{
                fTable[i][1] = INF;
            }
        }
    }
    // aa-cccccc cccccc-nn
    // aa-cccccc cccccc-nn
    // aa-cccccc cccccc-nn
    // aa-cccccc cccccc-nn

    public ForwardingTable(byte[] fTable){ // only the payload will be taken into the function
        for(int i = 0; i < 8; i+=2){
            int node = fTable[i]>>6;
            int cost = ((fTable[i] & 0b111111) <<6) | (fTable[i+1] >>2);
            int nextHop= fTable[i+1] &0b11;
            this.fTable[i/2][0] = node;
            this.fTable[i/2][1] = cost;
            this.fTable[i/2][2] = nextHop;
            if(cost == 0){
                this.address = node;
            }
        }
    }

    public void newRoute(int dest, int cost, int nextHop){
        fTable[dest][1] = cost;
        fTable[dest][2] = nextHop;
    }

    public void lostRoute(int dest){
        fTable[dest][1] = INF;
    }

    public int getNextHop(int dest){
        return fTable[dest][2];
    }

    public int getCost(int dest){
        return fTable[dest][1];
    }

    public int getAddress(){
        return address;
    }

    public void mergeTables(ForwardingTable nbTable){ // Searches to see if faster routes are available from neighbour table
        int costToNB = nbTable.getCost(address);
        if(costToNB == INF){
            return;
        }
        for(int i = 0; i < 4; ++i){
            if(costToNB + nbTable.getCost(i) < this.getCost(i)){
                this.newRoute(i,costToNB + nbTable.getCost(i), nbTable.getAddress());
            }
        }
    }

    public byte[] toBytes(){
        byte[] table = new byte[8];
        // aa-cccccc cccccc-nn
        for (int i = 0; i < 8; i+=2) {
            table[i] = (byte) (fTable[i/2][0] << 6 | fTable[i/2][1] >> 6);
            table[i+1] = (byte) (((fTable[i/2][1] & 0b111111) <<2) | fTable[i/2][2]);
        }

        return table;
    }
}


/*
tui node neighbour see them
addressing works, good enough for now
reachable nodes, update addressing when topology changes
message length fragmentation, if 1 packet, then no padding. first make 1 packet work
packet loss nothing implemented yet, detect and do something about it.
try slotted aloha if time is there, check if someone is sending, easy? No easy!!!!!
in case two nodes are looking at one other node, choose the one with the lower id.
 */