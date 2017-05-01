package chainofresponsibility;


import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pipe.common.Common.Failure;
import pipe.work.Work.WorkMessage;
import routing.Pipe;


public class PingHandler extends Handler {

    protected static Logger logger = LoggerFactory.getLogger("LeaderIs");

    public PingHandler(ServerState state) {
        super(state);
    }

    public void processWorkMessage(WorkMessage msg, Channel channel) {
        if (msg.hasPing()) {
        	PrintUtil.printWork(msg);           
        } else {
        	System.out.println("no ping going to hB handler");
            next.processWorkMessage(msg, channel);
        }
    }

    @Override
    public void processCommandMessage(Pipe.CommandMessage message, Channel channel) {
    	if (message.hasPing()) {
			
			logger.info("ping from " + message.getHeader().getNodeId()+" to "+message.getHeader().getDestination());
			PrintUtil.printCommand(message);
			
		}	
    	else {
        	System.out.println("no ping going to hB handler");
            next.processCommandMessage(message, channel);
        }
    }
/*
    @Override
    public void processGlobalMessage(Global.GlobalMessage message, Channel channel) {

    }

*/
}
