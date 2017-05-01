/**
 * Copyright 2016 Gash.
 *
 * This file and intellectual content is protected under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package gash.router.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.container.RoutingConf.RoutingEntry;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeList;
import gash.router.server.edges.EdgeMonitor;
import chainofresponsibility.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.work.Work.AskQueueSize;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.ReplyQueueSize;
import pipe.work.Work.Task;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import raft.FollowerState;
import raft.LeaderState;

/**
 * The message handler processes json messages that are delimited by a 'newline'
 * 
 * TODO replace println with logging!
 * 
 * @author gash
 * 
 */
public class WorkHandler extends SimpleChannelInboundHandler<WorkMessage> {
	protected static Logger logger = LoggerFactory.getLogger("work");

	protected ServerState state;
	protected boolean debug = false;
	private Handler handler;
	private EdgeList outboundEdges;
	// plan to shift this to vote handler

	public WorkHandler(ServerState state) {
		if (state != null) {
			this.state = state;
			this.handler = new ErrorHandler(state);
			Handler pingHandler = new PingHandler(state);
			Handler addNewNodeHandler = new AddNewNodeHandler(state);
			Handler heartbeatHandler = new HeartbeatHandler(state);
			Handler voteHandler = new VoteHandler(state);
			Handler reqVoteHandler = new RequestVoteHandler(state);
			Handler writeRequestHandler = new WriteRequestHandler(state);
			Handler writeResponseHandler = new WriteResponseHandler(state);
			Handler readRequestHandler = new ReadRequestHandler(state);
			// Handler readResponseHandler= new ReadResponseHandler(state);

			handler.setNext(pingHandler);
			pingHandler.setNext(addNewNodeHandler);
			addNewNodeHandler.setNext(heartbeatHandler);
			heartbeatHandler.setNext(voteHandler);
			voteHandler.setNext(reqVoteHandler);
			reqVoteHandler.setNext(writeRequestHandler);
			writeRequestHandler.setNext(writeResponseHandler);
			writeResponseHandler.setNext(readRequestHandler);

		}

	}

	/**
	 * override this method to provide processing behavior. T
	 * 
	 * @param msg
	 */

	public void handleMessage(WorkMessage msg, Channel channel) {
		if (msg == null) {
			// TODO add logging
			System.out.println("ERROR: Unexpected content - " + msg);
			return;
		}

		try {

			if (msg.hasAskqueuesize() && state.getManager().getCurrentState().getClass() == FollowerState.class) {
				System.out.println("Received message from Leader asking Queue Size");
				WorkMessage.Builder wbr = WorkMessage.newBuilder();
				Header.Builder hbr = Header.newBuilder();
				hbr.setDestination(-1);
				hbr.setTime(System.currentTimeMillis());

				ReplyQueueSize.Builder reply = ReplyQueueSize.newBuilder();
				System.out.println("Node Id : " + state.getManager().getNodeId());
				System.out.println("Queue Size : " + state.getManager().getCurrentState().getMessageQueue().size());
				System.out.println("Leader Id : " + state.getManager().getNodeId());

				reply.setNodeid(state.getManager().getNodeId());
				reply.setQueuesize(state.getManager().getCurrentState().getMessageQueue().size());

				// wbr.setHeader(hbr);
				wbr.setSecret(10);
				wbr.setReplyqueuesize(reply);
				WorkMessage wm = wbr.build();
				
				System.out.println("Work Message built");

				EdgeInfo ei = state.getManager().getEdgeMonitor().getOutBoundEdges().map
						.get(msg.getHeader().getNodeId());
				if (ei.isActive() && ei.getChannel() != null) {
					System.out.println("Sending now...");
					ei.getChannel().writeAndFlush(wm);
				}
				System.out.println("Queue Size Response sent to " + ei.getRef());
			} else if (msg.getResponse().hasReadResponse()&&state.getManager().getCurrentState().getClass() == LeaderState.class) {
				System.out.println("received read response chunk from follower");
				state.getManager().getCurrentState().sendChunkToClient(msg);
			} else if (msg.hasReplyqueuesize()
					&& state.getManager().getCurrentState().getClass() == LeaderState.class) {
				System.out.println("Received response from followers Queue Size");
				LeaderState leader = (LeaderState) state.getManager().getCurrentState();
				leader.setFollowerQueueSize(msg.getReplyqueuesize().getQueuesize(),
						msg.getReplyqueuesize().getNodeid());
			} else

			if (msg.hasReqvote()) {
				state.getManager().getCurrentState().onRequestVoteReceived(msg);
			} else if (msg.hasVote()) {

				state.getManager().getCurrentState().receivedVoteReply(msg);
			} else if (msg.hasLeader() && !msg.hasRequest()) {
				state.getManager().getCurrentState().receivedHeartBeat(msg);
				System.out.println("after has leader recv hb");
			} else if (msg.hasErr()) {
				Failure err = msg.getErr();
				logger.error("failure from " + msg.getHeader().getNodeId());
				PrintUtil.printFailure(err);
			} else if (msg.hasAddnewnode()) {
				state.getManager().getEdgeMonitor().createOutBoundIfNew(msg.getHeader().getNodeId(),
						msg.getAddnewnode().getHost(), msg.getAddnewnode().getPort());

				if (state.getManager().getCurrentState().getClass() == LeaderState.class) {
					System.out.println("directing you to leader state's replicating method");
					state.getManager().getCurrentState().replicateDatatoNewNode(msg.getHeader().getNodeId());
				}
			} else if (msg.getRequest().hasRwb()) {
				System.out.println("write picked by follower");
				state.getManager().getCurrentState().chunkReceived(msg);
			} else if (msg.getResponse().hasWriteResponse()) {
				System.out.println("got the log response from follower");
				state.getManager().getCurrentState().responseToChuckSent(msg);
			} else if (msg.hasCommit()) {
				System.out.println("in here");
				state.getManager().getCurrentState().receivedCommitChunkMessage(msg);
			} else if (msg.getRequest().hasRrb()) {
				System.out.println("Received Read Req from Leader");
				System.out.println("chunk " + msg.getRequest().getRrb().getFilename());
				System.out.println("chunk " + msg.getRequest().getRrb().getChunkId());
				state.getManager().getStateWorker().fetchChunk(msg);
			} else if (msg.getResponse().hasReadResponse()) {
				System.out.println("received read response chunk from follower");
				state.getManager().getCurrentState().sendChunkToClient(msg);
			} else if (msg.hasLog() && msg.getLog().getNewNodeId() == state.getManager().getNodeId()) {
				System.out.println("Chunk Replication message from Leader to New Node");
				state.getManager().getCurrentState().logReplicationMessage(msg);
			}

		} catch (NullPointerException e) {
			logger.error("Null pointer has occured from work handler logic" + e.getMessage());
		} catch (Exception e) {
			// TODO add logging
			Failure.Builder eb = Failure.newBuilder();
			eb.setId(state.getConf().getNodeId());
			eb.setRefId(msg.getHeader().getNodeId());
			eb.setMessage(e.getMessage());
			WorkMessage.Builder rb = WorkMessage.newBuilder(msg);
			rb.setErr(eb);
			channel.write(rb.build());
		}

		System.out.flush();

	}

	/**
	 * a message was received from the server. Here we dispatch the message to
	 * the client's thread pool to minimize the time it takes to process other
	 * messages.
	 * 
	 * @param ctx
	 *            The channel the message was received from
	 * @param msg
	 *            The message
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, WorkMessage msg) throws Exception {
		handleMessage(msg, ctx.channel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

}