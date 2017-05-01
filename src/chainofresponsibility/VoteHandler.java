

/**
 * @author Labhesh
 * @since 29 Mar,2017.
 */

package chainofresponsibility;


import gash.router.server.MessageServer;
import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import gash.router.server.edges.EdgeMonitor;
import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import pipe.work.Work;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.WorkMessage;
import routing.Pipe;

public class VoteHandler extends Handler {
    Logger logger = LoggerFactory.getLogger(WriteRequestHandler.class);
    public int requiredVotes=1;
    Set<Integer> voteList=new HashSet<Integer>();
    public VoteHandler(ServerState state) {
        super(state);
    }

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        
    	System.out.println(" inside vote handler");
    	if (message.hasVote()) {        	
        	System.out.println(" im handling vote");	
        	state.getManager().getCurrentState().receivedVoteReply(message);
           
        } else {
        	System.out.println("I dont have vote going to reqvote handelr");
            next.processWorkMessage(message, channel);
        	
        }
    }


  @Override
    public void processCommandMessage(Pipe.CommandMessage message, Channel channel) {
        
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
        }*/

    }
