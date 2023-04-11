package client;

public class MyRoute {
    public int nextHop;
    public int cost;

    public MyRoute(int neighbour, int cost) {
        this.nextHop = neighbour;
        this.cost = cost;
    }

    public MyRoute(){
    }
}
