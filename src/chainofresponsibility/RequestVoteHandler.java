package chainofresponsibility;

/**
 * @author Labhesh
 * @since 29 Mar,2017.
 */

import gash.router.server.MessageServer;
import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import gash.router.server.edges.EdgeMonitor;
import io.netty.channel.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import pipe.election.Election.Vote;
import pipe.work.Work;
import pipe.work.Work.WorkMessage;
import raft.CandidateState;
import raft.FollowerState;
import routing.Pipe;


public class RequestVoteHandler extends Handler {
    public RequestVoteHandler(ServerState state) {
		super(state);
		// TODO Auto-generated constructor stub
	}


	Logger logger = LoggerFactory.getLogger(WriteRequestHandler.class);
    

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.hasReqvote()) {
        	System.out.println("im in req vote handler");        	
        	state.getManager().getCurrentState().onRequestVoteReceived(message);
        	System.out.println("after req vote handler");
        	
        } else {        	
        	System.out.println("I dont have request vote going to write req handler");
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
        }

    }*/


    }

