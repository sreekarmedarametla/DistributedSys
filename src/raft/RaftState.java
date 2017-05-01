package raft;

import java.util.concurrent.LinkedBlockingDeque;

import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public interface RaftState {

	public void setManager(RaftManager Mgr);

	public RaftManager getManager();

	public void process();

	// latest implementation
	// public void receivedVote(WorkMessage msg);
	//
	// public void replyVote(WorkMessage msg);
	//
	public void onRequestVoteReceived(WorkMessage msg);

	public void receivedVoteReply(WorkMessage msg);

	public void receivedHeartBeat(WorkMessage msg);

	public void receivedLogToWrite(CommandMessage msg);

	public void chunkReceived(WorkMessage msg);

	public void responseToChuckSent(WorkMessage msg);

	public void receivedCommitChunkMessage(WorkMessage msg);

	public void readChunksFromFollowers(String fileName, int numOfchunks);

	public void fetchChunk(WorkMessage msg);

	public void sendChunkToClient(WorkMessage msg);

	public void replicateDatatoNewNode(int newNodeId);

	public void logReplicationMessage(WorkMessage msg);

	public LinkedBlockingDeque<WorkMessage> getMessageQueue();

}
