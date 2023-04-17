package client;

import java.util.ArrayList;
import java.util.List;

public class ForwardingTable {
    public int INF = 4;
    private Integer[][] fTable = new Integer[4][3];
    private int address;
    public ForwardingTable(int address){    // Format of the row: Destination : Cost : NextHop
        this.address = address;
        for(int i = 0; i < 4; i++){
            fTable[i][0] = i;               // Initially al the values of the first row are equal to the respective addresses
            fTable[i][2] = i;               // and the next hop to all addresses are themselves
            if(i == address){
                fTable[i][1] = 0;           // only cost set to 0 is the address itself
            }else{
                fTable[i][1] = INF;
            }
        }
    }

    // vamos a cambiar esto a 00-aa-cc-nn

    public ForwardingTable(byte[] fTable){ // only the payload will be taken into the function
                                           // currently attempting full packet and +2 on fTable to ignore header
        //neighbour's table made as a table, not byte[]
        // 01000000 0000000
        //0aa-ccc-nn
        //0aa-ccc-nn
        //0aa-ccc-nn
        //0aa-ccc-nn

        for(int i = 2; i < 6; i++){
            int node = fTable[i] >> 5;
            int cost = (fTable[i] >> 2) & 0b111;
            int nhop = fTable[i] & 0b11;
            this.fTable[i-2][0] = node;
            this.fTable[i-2][1] = cost;
            this.fTable[i-2][2] = nhop;
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
    public int getDestination(int row){
        return fTable[row][0];
    }

    public int getAddress(){
        return address;
    }

    public void mergeTables(ForwardingTable nbTable){ // Searches to see if faster routes are available from neighbour table
        int linkcost = nbTable.getCost(address);
        if(linkcost == INF){//does not get here
            return;
        }
        for(int i = 0; i < 4; i++){
            int destination = nbTable.getDestination(i);
            if(nbTable.getCost(i) + linkcost < this.getCost(destination)){
                this.newRoute(i, nbTable.getCost(i) + linkcost, destination);
            }
        }
    }

    public byte[] toBytes(){
        byte[] table = new byte[4];
        // new format 0aa-ccc-nn
        for(int i = 0; i < 4; i++){
            table[i] = (byte) (fTable[i][0] << 5 | fTable[i][1] << 2 | fTable[i][2]);
        }

        return table;
    }

    public void print(){
        System.out.println("[ Dst | Cst | NHp ]");
        for(int i = 0; i < 4; ++i){
            System.out.println("[  " + fTable[i][0] + "  |  " + fTable[i][1] + "  |  " + fTable[i][2] + "  ]");

        }
    }
}


/*
tui node neighbour see them
addressing works, good enough for now
reachable nodes, update addressing when topology changes
message length fragmentation, if 1 packet, then no padding. first make 1 packet work
packet loss nothing implemented yet, detect and do something about it.
try slotted aloha if time is there, check if someone is sending, easy? No easy!!!!! CHECK
in case two nodes are looking at one other node, choose the one with the lower id. * this is not the case
 */