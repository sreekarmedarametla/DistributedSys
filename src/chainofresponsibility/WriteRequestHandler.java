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

public class WriteRequestHandler extends Handler {
    public WriteRequestHandler(ServerState state) {
		super(state);
		// TODO Auto-generated constructor stub
	}


	Logger logger = LoggerFactory.getLogger(WriteRequestHandler.class);
    

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.getRequest().hasRwb()) {
        	System.out.println("im handling write req");
        	System.out.println("is it fucking here in workhandler"); 
			state.getManager().getCurrentState().chunkReceived(message);
        	
        } else {
        	System.out.println("no write req going to write resonse handler");
            next.processWorkMessage(message, channel);
        }
    }

   @Override
    public void processCommandMessage(CommandMessage message, Channel channel) {
	   if (message.getRequest().hasRwb()) {
		   System.out.println("has write request");				

			if(state.getManager().getLeaderId()==state.getManager().getNodeId())
			{		
				System.out.println("im in ");
			    state.getManager().getCurrentState().receivedLogToWrite(message);
			}
        } else {
            next.processCommandMessage(message, channel);
        }
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
