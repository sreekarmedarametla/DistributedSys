package chainofresponsibility;


import gash.router.server.ServerState;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pipe.common.Common.Failure;
import pipe.work.Work.WorkMessage;
import routing.Pipe;


public class AddNewNodeHandler extends Handler {

    protected static Logger logger = LoggerFactory.getLogger("LeaderIs");

    public AddNewNodeHandler(ServerState state) {
        super(state);
    }

    public void processWorkMessage(WorkMessage msg, Channel channel) {
    	if (msg.hasAddnewnode()) {
			state.getManager().getEdgeMonitor().createOutBoundIfNew(msg.getHeader().getNodeId(),msg.getAddnewnode().getHost(),msg.getAddnewnode().getPort());
    	}else {
        	System.out.println("no newnode added going to hB handler");
            next.processWorkMessage(msg, channel);
        }
    }

    @Override
    public void processCommandMessage(Pipe.CommandMessage message, Channel channel) {

    }
    /*
    @Override
    public void processGlobalMessage(Global.GlobalMessage message, Channel channel) {

    }

*/
}
