import java.io.Serializable;

public class Edge implements Serializable {
    private int weight;
    private int source;
    private int destination;

    public Edge(int weight, int node1, int node2) {
        this.weight = weight;
        this.source = Math.min(node1,node2);
        this.destination = Math.max(node1,node2);;
    }

    public int getWeight() {
        return weight;
    }

    public int getSource() {
        return source;
    }

    public int getDestination() {
        return destination;
    }
    public int getOtherEnd(int end1){
        return (end1==source)? destination:source;
    }
    public boolean equals(Edge input){
        return (this.weight == input.getWeight() && this.source == input.getSource() && this.destination ==input.getDestination());
    }
    public static Edge min(Edge e1, Edge e2) {
        if (e1.getWeight() < e2.getWeight()) {
            return e1;
        } else if (e1.getWeight() == e2.getWeight() && e1.getSource() < e2.getSource()) {
            return e1;
        } else if (e1.getWeight() == e2.getWeight() && e1.getSource() == e2.getSource() && e1.getDestination() < e2.getDestination()) {
            return e1;
        } else {
            return e2;
        }
    }


}