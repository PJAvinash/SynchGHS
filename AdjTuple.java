
public class AdjTuple {
    int uid;
    int edgeWeight;
    String hostName;
    int port;
    IncidentEdgeType edgeType;

    public AdjTuple(int uid,int edgeWeight,String hostName,int port){
        this.uid = uid;
        this.edgeWeight = edgeWeight;
        this.hostName = hostName;
        this.port = port;
        this.edgeType  = IncidentEdgeType.BASIC;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AdjTuple)) return false;
        AdjTuple other = (AdjTuple) o;
        return (this.uid == other.uid  && this.edgeWeight == other.edgeWeight && this.hostName.equals(other.hostName) && this.port == other.port);
    }
}
