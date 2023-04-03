import java.io.Serializable;

public class Message implements Serializable{
    int from;
    int coreMIN;
    int coreLevel;
    MessageType messageType;
    Edge candidateMWOE;
    public Message(int from,int coreMIN,int coreLevel,MessageType messageType, Edge candidateMWOE){
        this.from = from;
        this.coreMIN = coreMIN;
        this.coreLevel = coreLevel;
        this.messageType = messageType;
        //used only in converge cast
        this.candidateMWOE = candidateMWOE;
    }
    public Message(int from,int coreMIN,int coreLevel,MessageType messageType){
        this.from = from;
        this.coreMIN = coreMIN;
        this.coreLevel = coreLevel;
        this.messageType = messageType;
        this.candidateMWOE = null;

    }
}
