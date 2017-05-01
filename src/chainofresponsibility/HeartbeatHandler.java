/**
 * @author Labhesh
 * @since 25 Mar,2017.
 */
package chainofresponsibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import io.netty.channel.Channel;
import pipe.work.Work;
import routing.Pipe.CommandMessage;

public class HeartbeatHandler extends Handler {
    Logger logger = LoggerFactory.getLogger(WriteRequestHandler.class);
    public HeartbeatHandler(ServerState state) {
        super(state);
    }

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.hasLeader()&&!message.hasRequest()) {
        	state.getManager().getCurrentState().receivedHeartBeat(message);
        } else {
        	System.out.println("I dont have beat going to vote handler");
        	next.processWorkMessage(message, channel);        	
        }
    }

    @Override
    public void processCommandMessage(CommandMessage message, Channel channel) {
      
    }
    /*
    @Override
    public void processGlobalMessage(Global.GlobalMessage message, Channel channel) {
        if (message.getGlobalHeader().getDestinationId() == server.getGlobalConf().getClusterId()) {
            logger.info("I got back my request");
        } else {
            if (message.hasRequest()) {
                server.onGlobalDutyMessage(message, channel);
            } else {
                next.processGlobalMessage(message, channel);
            }
        }

    }*/


}
