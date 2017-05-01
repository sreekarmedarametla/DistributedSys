package chainofresponsibility;


import gash.router.server.ServerState;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pipe.common.Common.Failure;
import pipe.work.Work.WorkMessage;
import routing.Pipe;


public class ErrorHandler extends Handler {

    protected static Logger logger = LoggerFactory.getLogger("LeaderIs");

    public ErrorHandler(ServerState state) {
        super(state);
    }

    public void processWorkMessage(WorkMessage msg, Channel channel) {
        if (msg.hasErr()) {
        	System.out.println("in error handling");
            Failure err = msg.getErr();
            logger.error("failure from " + msg.getHeader().getNodeId());
        } else {
        	System.out.println("no erro going to ping handler");
            next.processWorkMessage(msg, channel);
        }
    }

    @Override
    public void processCommandMessage(Pipe.CommandMessage msg, Channel channel) {
    	if (msg.hasErr()) {
        	System.out.println("in error handling");
            Failure err = msg.getErr();
            logger.error("failure from " + msg.getHeader().getNodeId());
        } else {
        	System.out.println("no erro going to ping handler");
            next.processCommandMessage(msg, channel);
        }
    }
/*
    @Override
    public void processGlobalMessage(Global.GlobalMessage message, Channel channel) {

    }

*/
}
