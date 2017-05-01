package chainofresponsibility;


import gash.router.server.ServerState;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pipe.common.Common.Failure;
import pipe.work.Work.WorkMessage;
import routing.Pipe;
import routing.Pipe.CommandMessage;


public class CommitHandler extends Handler {

    protected static Logger logger = LoggerFactory.getLogger("LeaderIs");

    public CommitHandler(ServerState state) {
        super(state);
    }

    public void processWorkMessage(WorkMessage msg, Channel channel) {
    	if(msg.hasCommit()){
			System.out.println("asking for committing now");
			state.getManager().getCurrentState().receivedCommitChunkMessage(msg);
		}else {
        	System.out.println("no erro going to hB handler");
            next.processWorkMessage(msg, channel);
        }
    }


	@Override
	public void processCommandMessage(CommandMessage message, Channel channel) {
		// TODO Auto-generated method stub
		
	}
/*
   
    @Override
    public void processGlobalMessage(Global.GlobalMessage message, Channel channel) {

    }

*/
}
