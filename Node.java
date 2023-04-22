import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.io.FileWriter;

public class Node {
    private boolean synchGHSComplete = false;
    private Object lock = new Object();
    private boolean logging =true;
    private boolean testingMode = false;
    private int uid;
    private String hostName;
    private int port;
    private int level;
    private int coreMIN;
    private boolean coreIDKnown = true;
    private int parent;
    private NodeState state;

    //bookkeeping variables
    private Set<Integer> convergeCastWait = new HashSet<>();
    private Set<Integer> testResponseWait = new HashSet<>();

    private ArrayList<AdjTuple> adjacentNodes = new ArrayList<AdjTuple>();
    private List<Message> messageQueue = Collections.synchronizedList(new ArrayList<Message>());

    private List<Message> inMessages = Collections.synchronizedList(new ArrayList<Message>());
    private List<Message> outMessages = Collections.synchronizedList(new ArrayList<Message>());

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
                            this.state = NodeState.WAIT_FOR_COMPONENT_MERGE;
                            this.sendComponentMerge(minEdge); 
                        }
                        else{
                            this.state = NodeState.SEARCH_MWOE;
                            this.sendTestMessage();
                            this.sendSearchMessage();
                        }
                        this.transition();
                    }else{
                        //Non-leader
                        List<Message> searchMessages = this.messageQueue.stream().filter(msg -> msg.messageType == MessageType.SEARCH).collect(Collectors.toList());
                        if(searchMessages.size() > 0 ){
                            this.state = NodeState.SEARCH_MWOE;
                            this.parent = searchMessages.get(0).from;
                            this.coreMIN = searchMessages.get(0).coreMIN;
                            this.level = searchMessages.get(0).coreLevel;
                            this.coreIDKnown = true;

                            Message broadCastMessage = new Message(this.uid, this.coreMIN, this.level, MessageType.SEARCH);
                            this.sendTestMessage();
                            this.sendBroadcastMessage(broadCastMessage);
                            this.messageQueue.removeAll(searchMessages);
                            this.transition();
                        }
                    }
                    break;
                case SEARCH_MWOE:
                    List<Message> convergeCastMessages = this.getConvergeCastMessages();
                    this.updateEdgeType(convergeCastMessages.stream().filter(t -> t.messageType == MessageType.NO_MWOE).map(t->t.from).collect(Collectors.toList()),IncidentEdgeType.NOMWOE);
                    this.handleTestResponse();
                    if(convergeCastMessages.size() == this.convergeCastWait.size()){
                        //Edge minEdge = Edge.min(convergeCastMWOE, this.getMWOE());
                        //check if all the responses from
                        this.consolelog("BASIC: " + this.getBasicEdges().size() + " NO_MWOE: " + convergeCastMessages.stream().filter(t -> t.messageType == MessageType.NO_MWOE).count());
                        List<Message> activeMessagesFromConvergeCast = convergeCastMessages.stream().filter( t -> t.messageType != MessageType.NO_MWOE).collect(Collectors.toList()); 
                        if(this.getBasicEdges().size() == 0 && activeMessagesFromConvergeCast.size() == 0){
                            //Algorithm terminated. for this node. print tree and send response to parent.
                            this.printAdjacent();
                            this.writeIO();
                            if(!this.isLeader()){
                                Message NoMWOE = new Message(this.uid, this.coreMIN, this.level,MessageType.NO_MWOE);
                                this.sendMessage(NoMWOE, this.parent);
                            }
                            break;
                        }
                        Edge minEdge = null;
                        if(this.getBasicEdges().size() == 0  && activeMessagesFromConvergeCast.size() != 0){
                            // find the minimum edge and forward it to parent.
                            minEdge = Node.getCandidateMWOE(convergeCastMessages);
                        }
                        if(this.getBasicEdges().size() != 0  && activeMessagesFromConvergeCast.size() == 0){
                            // find the min edge and forward it to parent.
                            minEdge = this.getMWOE();
                        }
                        // This needs a change. 
                        if(this.getBasicEdges().size() != 0  && activeMessagesFromConvergeCast.size() != 0){
                            // find the min edge and forward it to parent.
                            this.consolelog("convergeCastMessages:" + convergeCastMessages.size());
                            minEdge = Edge.min(Node.getCandidateMWOE(convergeCastMessages), this.getMWOE());
                            
                        }
                        int otherEnd = minEdge.getOtherEnd(this.uid);
                        if(this.testResponseWait.contains(otherEnd)){
                            break;
                        }
                        //
                        this.messageQueue.removeAll(convergeCastMessages);
                        convergeCastWait.clear();
                        if(this.isLeader()){
                            Message broadcastMWOE = new Message(this.uid, this.coreMIN, this.level,MessageType.MWOE_BROADCAST, minEdge);
                            this.sendBroadcastMessage(broadcastMWOE);
                            if (this.isPartOfMWOE(minEdge)) {
                                this.state = NodeState.WAIT_FOR_COMPONENT_MERGE;
                                this.sendComponentMerge(minEdge); 
                                this.transition(); 
                            } else {
                                // so that this node doesnt assume it is leader anymore.
                                this.coreIDKnown = false;
                                this.state = NodeState.INITIAL;
                            }
                        }else{
                            //converge cast to parent
                            this.state = NodeState.WAIT_FOR_MWOE_BROADCAST;
                            Message responseToParent = new Message(this.uid, this.coreMIN, this.level,
                                    MessageType.MWOE_CONVERGECAST, minEdge);
                            this.sendMessage(responseToParent, this.parent);
                        }
                    }
                    break;
                case WAIT_FOR_MWOE_BROADCAST:
                    if (!this.isLeader()) {
                        List<Message> mwoeBroadcast = this.messageQueue.stream().filter(t -> t.from == this.parent && t.messageType == MessageType.MWOE_BROADCAST).collect(Collectors.toList());
                        if (mwoeBroadcast.size() > 0) {
                            Message mwoeBroadcastMessage = new Message(this.uid, this.coreMIN,this.level, MessageType.MWOE_BROADCAST, mwoeBroadcast.get(0).candidateMWOE);
                            this.sendBroadcastMessage(mwoeBroadcastMessage);
                            this.messageQueue.removeAll(mwoeBroadcast);
                            if(this.isPartOfMWOE(mwoeBroadcast.get(0).candidateMWOE)){
                                this.state  = NodeState.WAIT_FOR_COMPONENT_MERGE;
                                this.sendComponentMerge(mwoeBroadcast.get(0).candidateMWOE);
                                this.transition();
                            }else{
                                this.state  = NodeState.INITIAL;
                            }
                        }
                    }
                    break;
                case WAIT_FOR_COMPONENT_MERGE:
                    Edge mwoe = this.getMWOE();
                    int mwoe_otherend = mwoe.getOtherEnd(this.uid);
                    List<Message> componentMergeMessages = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_MERGE && t.from == mwoe_otherend).collect(Collectors.toList());
                    if (componentMergeMessages.size() > 0) {
                        // Merge or Absorb
                        boolean isMerge = (componentMergeMessages.get(0).coreLevel == this.level);
                        if(isMerge){
                            this.level = this.level+1;
                            this.coreMIN = Math.min(mwoe.getSource(),mwoe.getDestination());
                            this.parent = this.coreMIN;
                        }else{
                            this.level = Math.max(this.level, componentMergeMessages.get(0).coreLevel);
                            this.coreMIN = componentMergeMessages.get(0).coreMIN;
                            this.parent = mwoe_otherend;
                        }
                        this.state = NodeState.INITIAL;
                        this.updateEdgeType(Arrays.asList(mwoe_otherend),IncidentEdgeType.BRANCH);
                        this.messageQueue.removeAll(componentMergeMessages);
                        this.transition();
                    }
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

    private void handleTestResponse(){
        List<Integer> componentRejectResponseUIDS = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_REJECT).map(t -> t.from).collect(Collectors.toList());
        Set<Integer> componentAcceptResponeUIDS = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_ACCEPT).map(t -> t.from).collect(Collectors.toSet());
        this.updateEdgeType(componentRejectResponseUIDS, IncidentEdgeType.REJECTED);
        this.testResponseWait.removeAll(componentRejectResponseUIDS);
        this.testResponseWait.removeAll(componentAcceptResponeUIDS);
        this.messageQueue.removeAll(this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_REJECT).collect(Collectors.toList()));
    }

    

    private void sendSearchMessage(){
        Message searchMessage = new Message(this.uid,this.coreMIN,this.level,MessageType.SEARCH);
        this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH && t.uid != this.parent).forEach(t -> this.sendMessage(searchMessage,t.uid));
        this.convergeCastWait.clear();
        this.convergeCastWait.addAll(this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH && t.uid != this.parent).map(t -> t.uid).collect(Collectors.toList()));
    }
    private void sendBroadcastMessage(Message broadcastMessage){
        this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH && t.uid != this.parent).forEach(t -> this.sendMessage(broadcastMessage,t.uid));
    }
    private void sendTestMessage(){
        Message testMessage = new Message(this.uid, this.coreMIN, this.level, MessageType.COMPONENT_TEST);
        this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).forEach(t ->this.sendMessage(testMessage,t.uid));
        //remove old accept messages.
        this.messageQueue.removeAll(this.messageQueue.stream().filter(t-> t.messageType == MessageType.COMPONENT_ACCEPT).collect(Collectors.toList()));
        this.testResponseWait.clear();
        this.testResponseWait.addAll(this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).map(t -> t.uid).collect(Collectors.toList()));
    }

    private boolean isPartOfMWOE(Edge mwoe){
        return mwoe.getSource() == this.uid || mwoe.getDestination() == this.uid;
    }
    private void sendComponentMerge(Edge mwoe){
        if(this.isPartOfMWOE(mwoe)){
            Message componentMerge = new Message(this.uid, this.coreMIN, this.level, MessageType.COMPONENT_MERGE,mwoe);
            this.sendMessage(componentMerge, mwoe.getOtherEnd(this.uid));
        }
    }

    private void absorb(){
        List<Message> absorbRequests = this.messageQueue.stream().filter(t -> t.messageType == MessageType.COMPONENT_MERGE && this.level > t.coreLevel).collect(Collectors.toList());
        absorbRequests.stream().forEach(t -> this.sendMessage(new Message(this.uid, this.coreMIN, this.level, MessageType.COMPONENT_MERGE,t.candidateMWOE),t.from));
        List<Integer> absorbedUIDs = absorbRequests.stream().map(t -> t.from).collect(Collectors.toList());
        this.updateEdgeType(absorbedUIDs,IncidentEdgeType.BRANCH);
        this.consolelog(" absorbedUIDs: " + absorbedUIDs.toString());
        this.messageQueue.removeAll(absorbRequests);
    }
    private void updateEdgeType(List<Integer> uids, IncidentEdgeType edgeType) {
        for (AdjTuple t : this.adjacentNodes) {
            if (uids.contains(t.uid) && (( t.edgeType == IncidentEdgeType.BASIC && (edgeType == IncidentEdgeType.REJECTED || edgeType == IncidentEdgeType.BRANCH))||(t.edgeType == IncidentEdgeType.BRANCH && edgeType == IncidentEdgeType.NOMWOE)) ){
                t.edgeType = edgeType;
            }
        }
    }
    
    private Edge getMWOE(){
        return Node.findMinimum(this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).map(t -> new Edge(t.edgeWeight,t.uid,this.uid)).collect(Collectors.toList()));
    }

    private List<Edge> getBasicEdges(){
        return this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).map(t -> new Edge(t.edgeWeight,t.uid,this.uid)).collect(Collectors.toList());
    }
    private List<Edge> getActiveBranchEdges(){
        return this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BRANCH).map(t -> new Edge(t.edgeWeight,t.uid,this.uid)).collect(Collectors.toList());
    }
    

    private List<Message>getConvergeCastMessages(){
        List<Message> convergeCastMessages = this.messageQueue.stream().filter(msg -> msg.messageType == MessageType.MWOE_CONVERGECAST || msg.messageType == MessageType.NO_MWOE).collect(Collectors.toList());
        return convergeCastMessages;
    }

    private static Edge getCandidateMWOE(List<Message> input){
        return Node.findMinimum(input.stream().filter(t -> t.candidateMWOE !=null).map(t -> t.candidateMWOE).collect(Collectors.toList()));
    }

    private boolean isLeader(){
        return (this.coreMIN == this.uid) && this.coreIDKnown;
    }

    public static Edge findMinimum(List<Edge> tupleList) {
        // Define a custom comparator to compare tuples lexicographically
        Comparator<Edge> lexicographicComparator = Comparator.comparing(Edge::getWeight)
                .thenComparing(Edge::getSource)
                .thenComparing(Edge::getDestination);
        // Use the custom comparator to find the minimum tuple
        return tupleList.stream().min(lexicographicComparator).orElse(null);
    }



    
    
    public void startListening() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        this.transition();
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            while (!this.synchGHSComplete) {
                Socket clientSocket = serverSocket.accept();
                executor.execute(() -> {
                    try (ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream())) {
                        Message message;
                        while (clientSocket.isConnected() && !clientSocket.isClosed() && (message = (Message) input.readObject()) != null) {
                            this.addMessage(message);
                            this.transition();
                        }
                    } catch (EOFException e) {
                        // Stream ended normally, do nothing
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } finally {
            serverSocket.close();
            executor.shutdown();
        }
    }
    
    

    public void sendMessageTCP(Message message, String host, int port) throws IOException {
        int retryInterval = 5000;
        int maxRetries = 5;
        int retries = 0;
        while (retries <= maxRetries) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), retryInterval);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                output.writeObject(message);
                output.flush();
                return;
            } catch (IOException e) {
                retries++;
                if (retries > maxRetries) {
                    throw e; // throw the exception if max retries have been reached
                }
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(Message inputMessage,int targetUID){
        //logic to send message
        this.adjacentNodes.stream().filter(t-> t.uid == targetUID).forEach(t -> {
            try {
                if(!this.testingMode){
                    this.sendMessageTCP(inputMessage,t.hostName,t.port);
                }
            } catch (IOException e) {
                //e.printStackTrace();
            }
        });
        this.outMessages.add(inputMessage);
        this.consolelog("message  to--->:" + targetUID + " content: " + inputMessage.toString());
    }
    public synchronized void addMessage(Message inputMessage){
        this.messageQueue.add(inputMessage);
        this.inMessages.add(inputMessage);
        this.consolelog("message  <--from:"+inputMessage.from + " content: " + inputMessage.toString());
    }
    private void printAdjacent(){
        if(this.adjacentNodes.stream().filter(t->t.edgeType == IncidentEdgeType.BASIC).count() == 0 ){
            System.out.println("All Branches in MST are determined .adjacent nodes for "+this.uid +":");
            this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH || t.edgeType == IncidentEdgeType.NOMWOE ).forEach(t-> System.out.println("    ("+t.uid+" : "+t.hostName+")    "));
        }else{
            System.out.println("SynchGHS is in progress .adjacent nodes for "+this.uid +":");
            this.adjacentNodes.stream().filter(t -> t.edgeType == IncidentEdgeType.BRANCH || t.edgeType == IncidentEdgeType.NOMWOE ).forEach(t-> System.out.println("    ("+t.uid+" : "+t.hostName+")    "));
        }
    }
    private void consolelog(String msg){
        if(this.logging){
            System.out.println("uid:"+this.uid+" state:"+this.state.toString()+" coreID:"+this.coreMIN + " : "+msg);
        }
    }
    public void setTestingMode(boolean mode){
        this.testingMode = mode;
    }
    public NodeState getState(){
        NodeState r = this.state;
        return r;
    }
    public int getCoreID(){
        int cid = this.coreMIN;
        return cid;
    }

    public static void writeMessagesToJsonFile(List<Message> messages, String fileName) throws IOException {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(fileName);
            fileWriter.write("[");
            for (int i = 0; i < messages.size(); i++) {
                Message message = messages.get(i);
                fileWriter.write(message.toString());
                if (i < messages.size() - 1) {
                    fileWriter.write(",");
                }
            }
            fileWriter.write("]");
            fileWriter.flush();
           
        } 
        finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    public void writeIO(){
        String inPath = "logs/" + this.uid+ "in.json";
        String outPath = "logs/" + this.uid+ "out.json";
        try {
            Node.writeMessagesToJsonFile(this.inMessages,inPath);
            Node.writeMessagesToJsonFile(this.outMessages,outPath);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}

