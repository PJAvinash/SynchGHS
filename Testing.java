
import java.util.ArrayList;
import java.util.List;


public class Testing {

    public static Node creatNode(){
        Node testNode =  new Node(1,"testnode",1);
        testNode.setTestingMode(true);
        testNode.addNeighbor(2, 1, "n2", 1);
        testNode.addNeighbor(3, 2, "n3", 1);
        testNode.addNeighbor(4, 5, "n4", 1);
        testNode.addNeighbor(5, 6, "n5", 1);
        testNode.addNeighbor(6, 6, "n6", 1);
        return testNode;
    }
    public static List<Message> getTestMessages(){
        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(new Message(3, 3, 0, MessageType.COMPONENT_MERGE));
        inputMessages.add(new Message(2, 2, 0, MessageType.COMPONENT_MERGE));
        // 1 & 2 merge
        // 3 gets absorbed.
        inputMessages.add(new Message(2, 1, 1, MessageType.NO_MWOE));
        inputMessages.add(new Message(3, 1, 1, MessageType.NO_MWOE));
        inputMessages.add(new Message(5, 4, 1, MessageType.COMPONENT_TEST));
        inputMessages.add(new Message(6, 4, 1, MessageType.COMPONENT_TEST));
        inputMessages.add(new Message(4, 4, 1, MessageType.COMPONENT_TEST));
        inputMessages.add(new Message(5, 4, 1, MessageType.COMPONENT_ACCEPT));
        inputMessages.add(new Message(6, 4, 1, MessageType.COMPONENT_ACCEPT));
        inputMessages.add(new Message(4, 4, 1, MessageType.COMPONENT_ACCEPT));
        inputMessages.add(new Message(4, 4, 1, MessageType.COMPONENT_MERGE));
        inputMessages.add(new Message(4, 1, 2, MessageType.NO_MWOE));
        inputMessages.add(new Message(5, 1, 2, MessageType.COMPONENT_REJECT));
        inputMessages.add(new Message(6, 1, 2, MessageType.COMPONENT_REJECT));
        return inputMessages;
    }
    public static List<NodeState> getExpectedStates(){
        List<NodeState> expStates = new ArrayList<>();
        expStates.add(NodeState.WAIT_FOR_COMPONENT_MERGE);
        expStates.add(NodeState.SEARCH_MWOE);

        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.WAIT_FOR_COMPONENT_MERGE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        expStates.add(NodeState.SEARCH_MWOE);
        return expStates;
    }
    public static List<Integer> getExpCoreID(){
        List<Integer> expCoreID = new ArrayList<>();
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        expCoreID.add(1);
        return expCoreID;
    }

    
    public static void test(){
        Node testNode =  Testing.creatNode();
        List<Message> inputMessages = Testing.getTestMessages();
        List<NodeState> expStates = Testing.getExpectedStates();
        List<Integer> coreID = Testing.getExpCoreID();

        for(int i = 0; i<inputMessages.size(); i++){
            testNode.addMessage(inputMessages.get(i));
            testNode.transition();
            if(expStates.get(i) == testNode.getState() && coreID.get(i) == testNode.getCoreID()){
                System.out.println(i+" - PASS");
            }else{
                System.out.println(i+" - #FAIL STATE:"+testNode.getState()+" expSTATE: "+expStates.get(i)+" CORE:"+testNode.getCoreID()+" expCORE:"+coreID.get(i));
            }
        }
    }
    public static void main(String[] args){
        Testing.test();
    }
}
