import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Node {
    boolean synchGHSComplete = false;
    private Object lock = new Object();
    int uid;
    String hostName;
    int port;
    int level;
    int coreMIN;
    int leader;
    int parent;
    NodeState state;
    ArrayList<AdjTuple> adjacentNodes = new ArrayList<AdjTuple>();
    List<Message> messageQueue = Collections.synchronizedList(new ArrayList<Message>());

    public Node(int uid,String hostName,int port){
        this.uid = uid;
        this.hostName = hostName;
        this.port = port;
        this.synchGHSComplete = false;
        this.level = 0;
        this.coreMIN = uid;
        this.parent = uid;
        this.state = NodeState.INITIAL;
    }

    public void addNeighbor(int uid,int edgeWeight,String hostName,int port){
        this.adjacentNodes.add(new AdjTuple(uid,edgeWeight,hostName,port));
    }

    private synchronized void setAlgorithmComplete() {
        synchronized (lock) {
            this.synchGHSComplete = true;
        }
    }
    public synchronized boolean isSynchGHSComplete() {
        synchronized (lock) {
            return this.synchGHSComplete;
        }
    }


    public synchronized void transition(){
        this.respondToTestMessages();
        if(!this.isSynchGHSComplete()){
            //switch on Node state
            this.respondToTestMessages();
            this.absorb();
            switch(this.state){
                case INITIAL:
                    if(this.isLeader()){
                        if(this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH).count() == 0){
                            List<Edge> adjacentEdges = this.adjacentNodes.stream().map(t -> new Edge(t.edgeWeight,t.uid,this.uid)).collect(Collectors.toList());
                            Edge minEdge = Node.findMinimum(adjacentEdges);
                            this.sendComponentMerge(minEdge); 
                            this.state = NodeState.WAIT_FOR_COMPONENT_MERGE;
                        }
                        else{
                            this.sendTestMessage();
                            this.sendSearchMessage();
                            this.state = NodeState.SEARCH_MWOE;
                        }
                    }else{
                        //Non-leader
                        List<Integer> branchUIDs = this.adjacentNodes.stream().filter(t->t.edgeType ==IncidentEdgeType.BRANCH).map(t->t.uid).collect(Collectors.toList());
                        List<Message> searchMessages = this.messageQueue.stream().filter(msg -> msg.messageType == MessageType.SEARCH && branchUIDs.contains(msg.from)).collect(Collectors.toList());
                        this.parent = searchMessages.get(0).from;
                        this.coreMIN = searchMessages.get(0).coreMIN;
                        this.level = searchMessages.get(0).coreLevel;
                        if(searchMessages.size() > 0 ){
                            this.sendTestMessage();
                            this.sendSearchMessage();
                            this.state = NodeState.SEARCH_MWOE;
                            this.messageQueue.removeAll(searchMessages);
                        }
                    }
                    this.printAdjacent();
                    break;
                case SEARCH_MWOE:
                    List<Message> convergeCastMessages = this.getConvergeCastMessages();
                    List<Integer> componentRejectResponseUIDS = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_REJECT).map(t -> t.from).collect(Collectors.toList());
                    Set<Integer> componentAcceptResponeUIDS = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_ACCEPT).map(t -> t.from).collect(Collectors.toSet());
                    this.adjacentNodes.forEach(t-> t.edgeType = (componentRejectResponseUIDS.contains(t.uid)) ? IncidentEdgeType.REJECTED : t.edgeType);
                    // list of messages from nodes not in componentNodes, 
                    Edge convergeCastMWOE = Node.getCandidateMWOE(convergeCastMessages);
                    // If any of Outgoing edge with lesser <weight,s,d> has not confirmed the test, break and wait for more messages to change state
                    List<Integer> candidateNeighbours = this.getOutgoingEdges().stream().filter(t -> Edge.min(convergeCastMWOE,t).equals(t)).map(t -> t.getOtherEnd(this.uid)).filter(t -> !componentRejectResponseUIDS.contains(t)).collect(Collectors.toList());
                    candidateNeighbours.removeAll(componentAcceptResponeUIDS);
                    if(candidateNeighbours.size()  > 0){
                        break;
                    }
                    this.messageQueue.removeAll(this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_ACCEPT).collect(Collectors.toList()));
                    if(this.isLeader()){
                        if(convergeCastMessages.size() == this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH).count()){
                            Edge minEdge = Edge.min(convergeCastMWOE, this.getMWOE());
                            Message broadcastMWOE = new Message(this.uid, minEdge.getSource(),this.level,MessageType.MWOE_BROADCAST,minEdge);
                            this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH).forEach(t -> this.sendMessage(broadcastMWOE,t.uid));
                            if(this.isPartOfMWOE(minEdge)){
                                this.sendComponentMerge(minEdge);
                                this.state = NodeState.WAIT_FOR_COMPONENT_MERGE;
                            }else{
                                this.state = NodeState.INITIAL;
                            }
                            this.messageQueue.removeAll(convergeCastMessages);  
                        }
                    }else{
                        if((convergeCastMessages.size()+1) == this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH).count()){
                            Edge minEdge =  Edge.min(Node.getCandidateMWOE(convergeCastMessages), this.getMWOE());
                            Message broadcastMWOE = new Message(this.uid, this.coreMIN,this.level,MessageType.MWOE_CONVERGECAST,minEdge);
                            this.sendMessage(broadcastMWOE,this.parent);
                            //update state
                            this.state = NodeState.WAIT_FOR_MWOE_BROADCAST;
                            this.messageQueue.removeAll(convergeCastMessages);
                        }
                    }
                    this.printAdjacent();
                    break;
                case WAIT_FOR_MWOE_BROADCAST:
                    if (!this.isLeader()) {
                        List<Message> mwoeBroadcast = this.messageQueue.stream().filter(t -> t.from == this.parent && t.messageType == MessageType.MWOE_BROADCAST).collect(Collectors.toList());
                        if (mwoeBroadcast.size() > 0) {
                            Message mwoeBroadcastMessage = new Message(this.uid, mwoeBroadcast.get(0).coreMIN,this.level, MessageType.MWOE_BROADCAST, mwoeBroadcast.get(0).candidateMWOE);
                            this.sendBroadcastMessage(mwoeBroadcastMessage);
                            if(this.isPartOfMWOE(mwoeBroadcast.get(0).candidateMWOE)){
                                this.state  = NodeState.WAIT_FOR_COMPONENT_MERGE;
                                this.sendComponentMerge(mwoeBroadcast.get(0).candidateMWOE);
                            }else{
                                this.state  = NodeState.INITIAL;
                            }
                            this.messageQueue.removeAll(mwoeBroadcast);
                        }
                    }
                    this.printAdjacent();
                    break;
                case WAIT_FOR_COMPONENT_MERGE:
                    Edge mwoe = this.getMWOE();
                    int mwoe_otherend = mwoe.getOtherEnd(this.uid);
                    List<Message> componentMergeMessages = this.messageQueue.stream()
                            .filter(t -> t.messageType == MessageType.COMPONENT_MERGE && t.from == mwoe_otherend)
                            .collect(Collectors.toList());
                    if (componentMergeMessages.size() > 0) {
                        // Merge or Absorb
                        boolean isMerge = (componentMergeMessages.get(0).coreLevel == this.level);
                        int newLevel = isMerge ? this.level + 1: Math.max(this.level, componentMergeMessages.get(0).coreLevel);
                        this.level = newLevel;
                        this.coreMIN = isMerge ? mwoe.getSource():componentMergeMessages.get(0).coreMIN;
                        this.state = NodeState.INITIAL;
                        this.messageQueue.removeAll(componentMergeMessages);
                    }
                    this.printAdjacent();
                    break;


            }//end switch state
        }//end if
    }

    public void startSynchGHS() throws IOException{
        this.startListening();
    }
    
    private void respondToTestMessages() {
        List<Message> messagesToRespond = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_TEST && (t.coreMIN == this.coreMIN || (t.coreMIN != this.coreMIN && t.coreLevel <= this.level))).collect(Collectors.toList());
        Message rejectMessage = new Message(this.uid, this.coreMIN,this.level, MessageType.COMPONENT_REJECT);
        Message acceptMessage = new Message(this.uid, this.coreMIN,this.level, MessageType.COMPONENT_ACCEPT);
        messagesToRespond.stream().filter(t-> t.coreMIN == this.coreMIN).forEach(t ->this.sendMessage(rejectMessage, t.from));
        messagesToRespond.stream().filter(t-> t.coreMIN != this.coreMIN && t.coreLevel <= this.level ).forEach(t ->this.sendMessage(acceptMessage, t.from));
        this.messageQueue.removeAll(messagesToRespond);
    }

    

    private void sendSearchMessage(){
        Message searchMessage = new Message(this.uid,this.coreMIN,this.level,MessageType.SEARCH);
        this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH && t.uid != this.parent).forEach(t -> this.sendMessage(searchMessage,t.uid));
    }
    private void sendBroadcastMessage(Message broadcastMessage){
        this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH && t.uid != this.parent).forEach(t -> this.sendMessage(broadcastMessage,t.uid));
    }
    private void sendTestMessage(){
        Message testMessage = new Message(this.uid, this.coreMIN, this.level, MessageType.COMPONENT_TEST);
        this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).forEach(t ->this.sendMessage(testMessage,t.uid));
    }
    private boolean isPartOfMWOE(Edge mwoe){
        return mwoe.getSource() == this.uid || mwoe.getDestination() == this.uid;
    }
    private void sendComponentMerge(Edge mwoe){
        if(this.isPartOfMWOE(mwoe)){
            Message componentMerge = new Message(this.uid, mwoe.getSource(), this.level, MessageType.COMPONENT_MERGE,mwoe);
            this.sendMessage(componentMerge, (mwoe.getSource() == this.uid)? mwoe.getDestination():mwoe.getSource());
        }
    }

    private void absorb(){
        List<Message> absorbRequests = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_MERGE && this.level > t.coreLevel).collect(Collectors.toList());
        absorbRequests.stream().forEach(t -> this.sendMessage(new Message(this.uid, this.coreMIN, this.level, MessageType.COMPONENT_MERGE,t.candidateMWOE),t.from));
        List<Integer> absorbedUIDs = absorbRequests.stream().map(t -> t.from).collect(Collectors.toList());
        this.adjacentNodes.forEach(t-> t.edgeType = (absorbedUIDs.contains(t.uid)) ? IncidentEdgeType.BRANCH : t.edgeType);
        this.messageQueue.removeAll(absorbRequests);
    }
    private Edge getMWOE(){
        return Node.findMinimum(this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).map(t -> new Edge(t.edgeWeight,t.uid,this.uid)).collect(Collectors.toList()));
    }

    private List<Edge> getOutgoingEdges(){
        return this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).map(t -> new Edge(t.edgeWeight,t.uid,this.uid)).collect(Collectors.toList());
    }
    

    private List<Message>getConvergeCastMessages(){
        List<Message> convergeCastMessages = this.messageQueue.stream().filter(msg -> msg.messageType == MessageType.MWOE_CONVERGECAST).collect(Collectors.toList());
        return convergeCastMessages;
    }

    private static Edge getCandidateMWOE(List<Message> input){
        return Node.findMinimum(input.stream().filter(t -> t.candidateMWOE !=null).map(t -> t.candidateMWOE).collect(Collectors.toList()));
    }

    private boolean isLeader(){
        return (this.coreMIN == this.uid);
    }

    public static Edge findMinimum(List<Edge> tupleList) {
        // Define a custom comparator to compare tuples lexicographically
        Comparator<Edge> lexicographicComparator = Comparator.comparing(Edge::getWeight)
                .thenComparing(Edge::getSource)
                .thenComparing(Edge::getDestination);
        // Use the custom comparator to find the minimum tuple
        return tupleList.stream().min(lexicographicComparator).orElse(null);
    }



    // communication functions
    public void startListening() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Initalization
            this.transition();
            Thread listeningThread = new Thread(() -> {
                try {
                    while (!this.synchGHSComplete) {
                        Socket clientSocket = serverSocket.accept();
                        Thread clientThread = new Thread(() -> {
                            try (ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream())) {
                                Message message;
                                while ((message = (Message)input.readObject()) != null) {
                                    messageQueue.add(message);
                                    this.transition();
                                }
                            } catch (IOException | ClassNotFoundException  e) {
                                e.printStackTrace();
                            }
                        });
                        
                        clientThread.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            listeningThread.start();
        }
    }

    public void sendMessageTCP(Message message, String host, int port) throws IOException {
        try (Socket socket = new Socket(host, port);
        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            output.writeObject(message);
        }
    }

    private void sendMessage(Message inputMessage,int targetUID){
        //logic to send message
        this.adjacentNodes.stream().filter(t-> t.uid == targetUID).forEach(t -> {
            try {
                this.sendMessageTCP(inputMessage,t.hostName,t.port);
            } catch (IOException e) {
                //e.printStackTrace();
            }
        });

    }

    private void printAdjacent(){
        if(this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).count() == 0 ){
            System.out.println("All Branches in MST are determined .adjacent nodes for "+this.uid +":");
            this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH).forEach(t-> System.out.print("    ("+t.uid+" : "+t.hostName+")    "));
        }else{
            System.out.println("SynchGHS is in progress .adjacent nodes for "+this.uid +":");
            this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH).forEach(t-> System.out.print("    ("+t.uid+" : "+t.hostName+")    "));
        }
    }
}




