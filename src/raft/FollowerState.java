package raft;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeMonitor;
import pipe.common.Common.AddNewNode;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteResponse;
import pipe.election.Election.Vote;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import routing.Pipe.CommandMessage;

public class FollowerState implements RaftState {
	protected static Logger logger = LoggerFactory.getLogger("Follower State");
	private RaftManager Manager;
	private int votedFor = -1;
	private boolean initial = true;
	public LinkedBlockingDeque<WorkMessage> readMessageQueue = new LinkedBlockingDeque<WorkMessage>();
	Map<String, ArrayList<WorkMessage>> fileChunkMap = new HashMap<String, ArrayList<WorkMessage>>();
	Map<String, ArrayList<WorkMessage>> LogReplicationMap = new HashMap<String, ArrayList<WorkMessage>>();
	HashSet<String> databaseAddedFiles = new HashSet<String>();

	public LinkedBlockingDeque<WorkMessage> getMessageQueue() {
		return readMessageQueue;
	}

	public synchronized void process() {

		try {
			if (Manager.getElectionTimeout() <= 0
					&& (System.currentTimeMillis() - Manager.getLastKnownBeat() > Manager.getHbBase())) {
				Manager.setCurrentState(Manager.Candidate);
				System.out.println("state changed to candidate... all set for leader election");
				return;
			} else {
				Thread.sleep(200);
				long dt = Manager.getElectionTimeout() - (System.currentTimeMillis() - Manager.getTimerStart());
				//System.out.println("election timeout value " + dt);
				Manager.setElectionTimeout(dt);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public synchronized void setManager(RaftManager Mgr) {
		this.Manager = Mgr;
	}

	@Override
	public synchronized RaftManager getManager() {
		return Manager;
	}

	// giving vote after receiving request votes
	public synchronized void onRequestVoteReceived(WorkMessage msg) {
		System.out.println("got a election request vote ");
		Manager.setCurrentState(Manager.Follower);
		System.out.println("state is follower");
		Manager.randomizeElectionTimeout();
		if (Manager.getTerm() < msg.getReqvote().getCurrentTerm() && votedFor == -1) {
			// changed from node id to candidate id
			votedFor = msg.getReqvote().getCandidateID();

			// changed term value
			Manager.setTerm(msg.getReqvote().getCurrentTerm());
			System.out.println(System.currentTimeMillis() + ": " + Manager.getNodeId() + " voted for " + votedFor
					+ " in term " + Manager.getTerm() + ". Timeout is : " + Manager.getElectionTimeout());
			replyVote(msg, true);
			votedFor = -1;

		} else {
			replyVote(msg, false);
		}

	}

	// replyvote

	public synchronized void replyVote(WorkMessage msg, boolean sendVote) {
		if (sendVote == true) {
			int toNode = msg.getReqvote().getCandidateID();
			int fromNode = Manager.getNodeId();
			EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(toNode);
			if (ei.isActive() && ei.getChannel() != null) {
				System.out.println("Im giving my vote to " + toNode);
				ei.getChannel().writeAndFlush(Vote(fromNode, toNode));

			}
		}
	}

	public synchronized WorkMessage Vote(int NodeId, int CandidateId) {
		Vote.Builder vb = Vote.newBuilder();
		vb.setVoterID(NodeId);
		vb.setCandidateID(CandidateId);
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setVote(vb);
		wb.setSecret(10);
		return wb.build();
	}

	@Override
	public synchronized void receivedVoteReply(WorkMessage msg) {
		System.out.println("Im in follower recvVOteReply method.... doing nothing");
		// TODO Auto-generated method stub
		return;

	}

	public synchronized void fetchChunk(WorkMessage msg) {
		try {
			Manager.randomizeElectionTimeout();
			Manager.setCurrentState(Manager.Follower);
			Manager.setLastKnownBeat(System.currentTimeMillis());
			System.out.println("Reading from database in Follower Node");
			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "abcd");
			PreparedStatement statement = con
					.prepareStatement("select * from filetable where chunkid=? && filename = ?");
			statement.setLong(1, msg.getRequest().getRrb().getChunkId());
			statement.setString(2, msg.getRequest().getRrb().getFilename());
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(Manager.getNodeId());
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);

				Chunk.Builder chb = Chunk.newBuilder();
				chb.setChunkId(rs.getInt(2));
				ByteString bs = ByteString.copyFrom(rs.getBytes(3));
				System.out.println("byte string " + bs);
				chb.setChunkData(bs);
				chb.setChunkSize(rs.getInt(4));

				ReadResponse.Builder rrb = ReadResponse.newBuilder();
				rrb.setFilename(rs.getString(1));
				rrb.setChunk(chb);
				rrb.setNumOfChunks(rs.getInt(5));

				Response.Builder rb = Response.newBuilder();
				// request type, read,write,etc
				rb.setResponseType(TaskType.RESPONSEREADFILE);
				rb.setReadResponse(rrb);
				WorkMessage.Builder cb = WorkMessage.newBuilder();
				// Prepare the CommandMessage structure
				cb.setHeader(hb);
				cb.setSecret(10);
				cb.setResponse(rb);
				int toNode = msg.getHeader().getNodeId();
				int fromNode = Manager.getNodeId();
				EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(toNode);
				if (ei.isActive() && ei.getChannel() != null) {
					ei.getChannel().writeAndFlush(cb.build());
				}
			}
		} catch (Exception e) {
		}
	}

	@Override
	public synchronized void receivedHeartBeat(WorkMessage msg) {
		Manager.randomizeElectionTimeout();
		System.out.println("received ehearbeat from the Leader: " + msg.getLeader().getLeaderId());
		PrintUtil.printWork(msg);
		Manager.setCurrentState(Manager.Follower);
		Manager.setLastKnownBeat(System.currentTimeMillis());
	}

	public void receivedLogToWrite(CommandMessage msg) {
		return;
	}

	public void chunkReceived(WorkMessage msg) {
		System.out.println("i received a chunk from leader");

		Manager.randomizeElectionTimeout();
		Manager.setCurrentState(Manager.Follower);
		Manager.setLastKnownBeat(System.currentTimeMillis());

		// adding chunk for a file to map for delayed commiting
		if (!fileChunkMap.containsKey(msg.getRequest().getRwb().getFilename())) {
			fileChunkMap.put(msg.getRequest().getRwb().getFilename(), new ArrayList<WorkMessage>());
		}
		fileChunkMap.get(msg.getRequest().getRwb().getFilename()).add(msg);
		System.out.println("added a chunk to map" + fileChunkMap.get(msg.getRequest().getRwb().getFilename()).size());
		// building write response message
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getNodeId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		WriteResponse.Builder wrb = WriteResponse.newBuilder();
		wrb.setFilename(msg.getRequest().getRwb().getFilename());
		wrb.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
		wrb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());

		Response.Builder rb = Response.newBuilder();
		rb.setResponseType(TaskType.RESPONSEWRITEFILE);
		rb.setWriteResponse(wrb);
		WorkMessage.Builder wbs = WorkMessage.newBuilder();
		wbs.setHeader(hb);
		wbs.setSecret(10);
		wbs.setResponse(rb);
		int toNode = msg.getHeader().getNodeId();
		System.out.println("to node is" + toNode);

		int fromNode = Manager.getNodeId();
		EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(toNode);
		if (ei.isActive() && ei.getChannel() != null) {
			System.out.println("Im responding to leader that i received chunk "
					+ msg.getRequest().getRwb().getChunk().getChunkId());
			ei.getChannel().writeAndFlush(wbs.build());

		}

	}

	public void responseToChuckSent(WorkMessage msg) {
		return;
	}

	public void receivedCommitChunkMessage(WorkMessage msg) {
		System.out.println("going to commit now");
		String fileName = msg.getCommit().getFilename();
		int numOfChunks = msg.getCommit().getNumOfChunks();
		System.out.println(numOfChunks);
		System.out.println(fileName);
		try {
			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "root");
			// create the mysql insert preparedstatement
			for (int j = 0; j < numOfChunks; j++) {
				System.out.println("Added chunk to DB " + j);
				String query = " insert into filetable (filename, chunkid, chunkdata, chunksize, numberofchunks)"
						+ " values (?, ?, ?, ?, ?)";
				PreparedStatement preparedStmt = con.prepareStatement(query);
				preparedStmt.setString(1, fileName);
				preparedStmt.setInt(2, fileChunkMap.get(fileName).get(j).getRequest().getRwb().getChunk().getChunkId());
				preparedStmt.setBytes(3,
						(fileChunkMap.get(fileName).get(j).getRequest().getRwb().getChunk().getChunkData())
								.toByteArray());
				preparedStmt.setInt(4,
						fileChunkMap.get(fileName).get(j).getRequest().getRwb().getChunk().getChunkSize());
				preparedStmt.setInt(5, numOfChunks);
				preparedStmt.execute();
			}
			con.close();
			System.out.println("commited");
		}

		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void readChunksFromFollowers(String fileName, int numOfchunks) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void sendChunkToClient(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void replicateDatatoNewNode(int newNodeId) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void logReplicationMessage(WorkMessage msg) {

		if (!LogReplicationMap.containsKey(msg.getLog().getRwb().getFilename())) {
			LogReplicationMap.put(msg.getLog().getRwb().getFilename(), new ArrayList<WorkMessage>());
		}
		LogReplicationMap.get(msg.getLog().getRwb().getFilename()).add(msg);
		System.out.println("added a chunk to map" + LogReplicationMap.get(msg.getLog().getRwb().getFilename()).size());
		if (LogReplicationMap.get(msg.getLog().getRwb().getFilename()).size() == msg.getLog().getRwb()
				.getNumOfChunks()) {
			if (!databaseAddedFiles.contains(msg.getLog().getRwb().getFilename())) {

				addTodatabase(msg.getLog().getRwb().getFilename());
			}

		}

	}

	public synchronized void addTodatabase(String fileName) {
		System.out.println("new node adding to database the file " + fileName);
		databaseAddedFiles.add(fileName);
		System.out.println("going to commit now");
		int numOfChunks = LogReplicationMap.get(fileName).size();
		System.out.println(numOfChunks);

		try {
			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "root");
			// create the mysql insert preparedstatement
			for (int j = 0; j < numOfChunks; j++) {
				System.out.println("Added chunk to DB " + j);
				String query = " insert into filetable (filename, chunkid, chunkdata, chunksize, numberofchunks)"
						+ " values (?, ?, ?, ?, ?)";
				PreparedStatement preparedStmt = con.prepareStatement(query);
				preparedStmt.setString(1, fileName);
				preparedStmt.setInt(2,
						LogReplicationMap.get(fileName).get(j).getLog().getRwb().getChunk().getChunkId());
				preparedStmt.setBytes(3,
						(LogReplicationMap.get(fileName).get(j).getLog().getRwb().getChunk().getChunkData())
								.toByteArray());
				preparedStmt.setInt(4,
						LogReplicationMap.get(fileName).get(j).getLog().getRwb().getChunk().getChunkSize());
				preparedStmt.setInt(5, numOfChunks);
				preparedStmt.execute();
			}
			con.close();
			System.out.println("commited");
		}

		catch (Exception e) {
			e.printStackTrace();
		}

	}

}
