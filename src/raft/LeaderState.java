package raft;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.protobuf.ByteString;

import gash.router.client.CommConnection;
import gash.router.server.PrintUtil;
import gash.router.server.edges.EdgeInfo;
import io.netty.channel.Channel;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.ReadBody;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Request;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteBody;
import pipe.election.Election.LeaderStatus;
import pipe.work.Work.Commit;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import redis.clients.jedis.Jedis;
import routing.Pipe;
import routing.Pipe.CommandMessage;

public class LeaderState implements RaftState {
	private RaftManager Manager;
	LinkedBlockingDeque<WorkMessage> chunkMessageQueue = new LinkedBlockingDeque<WorkMessage>();
	LinkedBlockingDeque<WorkMessage> TemporaryMessageQueue = new LinkedBlockingDeque<WorkMessage>();
	public LinkedBlockingDeque<WorkMessage> readMessageQueue = new LinkedBlockingDeque<WorkMessage>();
	Map<String, Map<Integer, Integer>> chunkResponseMap = new HashMap<String, Map<Integer, Integer>>();
	Map<Integer, Integer> nodeCountMap = new HashMap<Integer, Integer>();
	Map<String, Integer> commitMap = new HashMap<String, Integer>();
	Map<String, ArrayList<WorkMessage>> fileChunkMap = new HashMap<String, ArrayList<WorkMessage>>();
	Set<Channel> followerChannelsList = new HashSet<Channel>();
	ArrayList<String> commitedFileNames = new ArrayList<String>();
	double clusterSize = 1;
	ArrayList<Integer> workStealingNodes = new ArrayList<Integer>();

	public LinkedBlockingDeque<WorkMessage> getMessageQueue() {
		return readMessageQueue;
	}

	public boolean flag = true;

	public void setFollowerQueueSize(int queueSize, int nodeId) {
		if (flag) {
			System.out.println("entered inside the if block");
			Manager.getStateWorker().startTime = System.currentTimeMillis();
			Manager.getStateWorker().startTracking = true;
			flag = false;
			System.out.println("leader state's method checking flag  "+flag);
		}
		
		if (queueSize < 2)
			workStealingNodes.add(nodeId);
		
		//if (workStealingNodes.size() == 1) {
			
			

			//Manager.getStateWorker().startTracking = true;
			/*
			 * else { if (System.currentTimeMillis() - startTime > 5000) {
			 * Manager.getStateWorker().start = true; count = 0; } }
			 */
		//}

		/*

		if (count == getClusterSize()) {
			Manager.getStateWorker().processRequest = true;
			Manager.getStateWorker().start = true;
			count = 0;
		}
		*/
	}

	@Override
	public synchronized void process() {
		// System.out.println("In leaders process method");
		try {

			if (chunkMessageQueue.isEmpty()) {
				for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
					if (ei.isActive() && ei.getChannel() != null) {
						Manager.getEdgeMonitor().sendMessage(createHB());
						System.out.println("sent hb to" + ei.getRef());
					}
				}
			}
			if (!chunkMessageQueue.isEmpty()) {
				System.out.println("before taking message " + chunkMessageQueue.size());
				for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
					if (ei.isActive() && ei.getChannel() != null) {
						System.out.println("flshing to node " + ei.getRef());
						WorkMessage wm = sendChunkToFollowers();
						Manager.getEdgeMonitor().sendChunk(wm);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// CREATE HEARTBEAT
	public WorkMessage createHB() {
		WorkState.Builder sb = WorkState.newBuilder();
		sb.setEnqueued(-1);
		sb.setProcessed(-1);

		Heartbeat.Builder bb = Heartbeat.newBuilder();
		bb.setState(sb);

		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getNodeId());
		hb.setDestination(-1);
		hb.setTime(System.currentTimeMillis());

		LeaderStatus.Builder lb = LeaderStatus.newBuilder();
		lb.setLeaderId(Manager.getNodeId());
		lb.setLeaderTerm(Manager.getTerm());

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(hb);
		wb.setBeat(bb);
		wb.setLeader(lb);
		wb.setSecret(10);
		return wb.build();
	}

	// creating heartbeat and appendentry
	public synchronized WorkMessage sendChunkToFollowers() {
		if (!chunkMessageQueue.isEmpty()) {
			WorkMessage msg = null;
			try {
				msg = chunkMessageQueue.take();

			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Header.Builder hb = Header.newBuilder();
			hb.setNodeId(Manager.getNodeId());
			hb.setDestination(-1);
			hb.setTime(System.currentTimeMillis());

			LeaderStatus.Builder lb = LeaderStatus.newBuilder();
			lb.setLeaderId(Manager.getNodeId());
			lb.setLeaderTerm(Manager.getTerm());

			Chunk.Builder chb = Chunk.newBuilder();
			chb.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
			chb.setChunkData(msg.getRequest().getRwb().getChunk().getChunkData());
			chb.setChunkSize(msg.getRequest().getRwb().getChunk().getChunkSize());

			WriteBody.Builder wb = WriteBody.newBuilder();

			wb.setFilename(msg.getRequest().getRwb().getFilename());
			wb.setChunk(chb);
			wb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());

			Request.Builder rb = Request.newBuilder();
			// request type, read,write,etc
			rb.setRwb(wb);
			rb.setRequestType(TaskType.REQUESTWRITEFILE);

			WorkMessage.Builder wbs = WorkMessage.newBuilder();
			wbs.setHeader(hb);
			wbs.setLeader(lb);
			wbs.setSecret(10);
			wbs.setRequest(rb);
			System.out.println("i am working fine from build chunk to follower method");
			PrintUtil.printWork(wbs.build());

			return wbs.build();
		} else {
			return createHB();
		}

	}

	public void readChunksFromFollowers(String fileName, int numOfChunks) {
		// TODO Auto-generated method stub
		int count = 0;
		for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
			if (ei.isActive() && ei.getChannel() != null) {
				followerChannelsList.add(ei.getChannel());
			}
		}
		while (count < numOfChunks) {
			for (Iterator<Channel> it = followerChannelsList.iterator(); it.hasNext();) {
				it.next().writeAndFlush(buildReadChunksFromFollowersMessage(fileName, count));
				count++;
			}
		}

	}

	public WorkMessage buildReadChunksFromFollowersMessage(String fileName, int chunkId) {
		Header.Builder hb = Header.newBuilder();

		hb.setNodeId(Manager.getLeaderId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		// prepare the read body request Structure
		ReadBody.Builder rrb = ReadBody.newBuilder();
		rrb.setFilename(fileName);
		rrb.setChunkId(chunkId);

		Request.Builder rb = Request.newBuilder();
		rb.setRequestType(TaskType.REQUESTREADFILE);
		rb.setRrb(rrb);

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		// Prepare the CommandMessage structure
		wb.setHeader(hb);
		wb.setSecret(10);
		wb.setRequest(rb);
		return wb.build();
	}

	// received hearbeat no need to implement here
	public synchronized void receivedHeartBeat(WorkMessage msg) {
		Manager.randomizeElectionTimeout();
		System.out.println("received hearbeat from the Leader: " + msg.getLeader().getLeaderId());
		PrintUtil.printWork(msg);
		Manager.setCurrentState(Manager.Follower);
		Manager.setLastKnownBeat(System.currentTimeMillis());
	}

	@Override
	public synchronized void setManager(RaftManager Mgr) {
		this.Manager = Mgr;
	}

	@Override
	public synchronized RaftManager getManager() {
		return Manager;
	}

	@Override
	public void onRequestVoteReceived(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

	@Override
	public void receivedVoteReply(WorkMessage msg) {
		// TODO Auto-generated method stub
		return;

	}

	public void receivedLogToWrite(CommandMessage msg) {
		System.out.println("reached leader now ");
		System.out.println("building work message");

		WorkMessage wm = buildWorkMessage(msg);
		if (!fileChunkMap.containsKey(msg.getRequest().getRwb().getFilename())) {
			fileChunkMap.put(msg.getRequest().getRwb().getFilename(), new ArrayList<WorkMessage>());
		}
		fileChunkMap.get(msg.getRequest().getRwb().getFilename()).add(wm);

		try {
			chunkMessageQueue.put(wm);
			System.out.println("size of queue is " + chunkMessageQueue.size());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("queue size is " + chunkMessageQueue.size());
		// PrintUtil.printWork(wm);
		// for(EdgeInfo
		// ei:Manager.getEdgeMonitor().getOutBoundEdges().map.values())
		// {
		// if(ei.getChannel()!=null& &ei.isActive())
		// {
		// Manager.getEdgeMonitor().sendMessage(wm);
		//
		// }
		// }

	}

	public WorkMessage buildWorkMessage(CommandMessage msg) {

		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getLeaderId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		Chunk.Builder chb = Chunk.newBuilder();
		chb.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
		chb.setChunkData(msg.getRequest().getRwb().getChunk().getChunkData());
		chb.setChunkSize(msg.getRequest().getRwb().getChunk().getChunkSize());

		WriteBody.Builder wb = WriteBody.newBuilder();

		wb.setFilename(msg.getRequest().getRwb().getFilename());
		wb.setChunk(chb);
		wb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());

		Request.Builder rb = Request.newBuilder();
		// request type, read,write,etc
		rb.setRwb(wb);
		rb.setRequestType(TaskType.REQUESTWRITEFILE);
		WorkMessage.Builder wbs = WorkMessage.newBuilder();
		// Prepare the CommandMessage structure

		wbs.setHeader(hb);
		wbs.setRequest(rb);
		wbs.setSecret(10);
		System.out.println("retunr fun");
		return wbs.build();
	}

	public void chunkReceived(WorkMessage msg) {
		return;
	}

	public void commitToDatabase(Map<String, ArrayList<WorkMessage>> fileChunkMap, String fileName, int numOfChunks) {

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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public WorkMessage askFollowersToCommitFile(WorkMessage msg) {

		Commit.Builder cb = Commit.newBuilder();
		cb.setFilename(msg.getResponse().getWriteResponse().getFilename());
		cb.setNumOfChunks(msg.getResponse().getWriteResponse().getNumOfChunks());

		WorkMessage.Builder wbs = WorkMessage.newBuilder();
		wbs.setCommit(cb);
		wbs.setSecret(10);
		System.out.println("retunr fun");
		return wbs.build();
	}

	public Double getClusterSize() {
		// checking cluster size
		clusterSize = 1;
		for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
			if (ei.isActive() && ei.getChannel() != null) {
				clusterSize++;
			}
		}
		return clusterSize;
	}

	public void responseToChuckSent(WorkMessage msg) {
		System.out.println("in responsetochunk method");
		System.out.println("adding to chunk map");
		/*
		 * if (!fileChunkMap.containsKey(msg.getResponse().getWriteResponse().
		 * getFileName())) {
		 * fileChunkMap.put(msg.getResponse().getWriteResponse().getFileName(),
		 * new ArrayList<WorkMessage>()); }
		 * fileChunkMap.get(msg.getResponse().getWriteResponse().getFileName()).
		 * add(msg);
		 */
		// System.out.println("chunks for
		// file"+msg.getResponse().getWriteResponse().getFileName()+" added to
		// map");
		// System.out.println(fileChunkMap.get(msg.getResponse().getWriteResponse().getFileName()).size());
		if (!chunkResponseMap.containsKey(msg.getResponse().getWriteResponse().getFilename())) {
			chunkResponseMap.put(msg.getResponse().getWriteResponse().getFilename(), new HashMap<Integer, Integer>());
		}
		if (!chunkResponseMap.get(msg.getResponse().getWriteResponse().getFilename())
				.containsKey(msg.getHeader().getNodeId())) {
			chunkResponseMap.get(msg.getResponse().getWriteResponse().getFilename()).put(msg.getHeader().getNodeId(),
					1);
		} else {
			int i = chunkResponseMap.get(msg.getResponse().getWriteResponse().getFilename())
					.get(msg.getHeader().getNodeId());
			chunkResponseMap.get(msg.getResponse().getWriteResponse().getFilename()).put(msg.getHeader().getNodeId(),
					++i);
			System.out.println("Node " + msg.getHeader().getNodeId() + " commited " + chunkResponseMap
					.get(msg.getResponse().getWriteResponse().getFilename()).get(msg.getHeader().getNodeId()));
		}
		System.out.println("chunk reponse maps value " + (chunkResponseMap
				.get(msg.getResponse().getWriteResponse().getFilename()).get(msg.getHeader().getNodeId())));

		if ((chunkResponseMap.get(msg.getResponse().getWriteResponse().getFilename())
				.get(msg.getHeader().getNodeId())) == msg.getResponse().getWriteResponse().getNumOfChunks()) {
			System.out.println("in committing now ");
			if (!commitMap.containsKey(msg.getResponse().getWriteResponse().getFilename())) {
				commitMap.put(msg.getResponse().getWriteResponse().getFilename(), 1);
				System.out.println(
						"i have commited, value " + commitMap.get(msg.getResponse().getWriteResponse().getFilename()));
			} else {
				int i = commitMap.get(msg.getResponse().getWriteResponse().getFilename());
				commitMap.put(msg.getResponse().getWriteResponse().getFilename(), ++i);
				System.out.println("For this file " + msg.getResponse().getWriteResponse().getFilename()
						+ " commit count " + commitMap.get(msg.getResponse().getWriteResponse().getFilename()));
			}
			double size = getClusterSize();
			System.out.println("cluster size " + size / 2);
			if (commitMap.get(msg.getResponse().getWriteResponse().getFilename()) >= (size / 2)) {
				System.out.println("going to add to db  : " + msg.getResponse().getWriteResponse().getFilename());
				// db add
				try {
					System.out.println("Filename" + msg.getResponse().getWriteResponse().getFilename());
					System.out.println("chunk value"
							+ fileChunkMap.get(msg.getResponse().getWriteResponse().getFilename()).size());
					commitToDatabase(fileChunkMap, msg.getResponse().getWriteResponse().getFilename(),
							fileChunkMap.get(msg.getResponse().getWriteResponse().getFilename()).size());

					// write message to send to follows to commit a particular
					// file
					System.out.println("completed db");
					Set<Integer> keys = chunkResponseMap.get(msg.getResponse().getWriteResponse().getFilename())
							.keySet();
					for (Integer key : keys) {
						// if(key==msg.getHeader().getNodeId()){
						EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(key);
						if (ei.isActive() && ei.getChannel() != null) {
							System.out.println("Im sending request to commit to "
									+ msg.getRequest().getRwb().getChunk().getChunkId());
							ei.getChannel().writeAndFlush(askFollowersToCommitFile(msg));

							// }
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			// map list add

		}
	}

	@Override
	public void receivedCommitChunkMessage(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fetchChunk(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

	@Override
	public synchronized void sendChunkToClient(WorkMessage msg) {
		
		System.out.println("received sendChunnk to client method");
		// TODO Auto-generated method stub
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getNodeId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		Chunk.Builder chb = Chunk.newBuilder();
		chb.setChunkId(msg.getResponse().getReadResponse().getChunk().getChunkId());
		chb.setChunkData(msg.getResponse().getReadResponse().getChunk().getChunkData());
		chb.setChunkSize(msg.getResponse().getReadResponse().getChunk().getChunkSize());

		ReadResponse.Builder rrb = ReadResponse.newBuilder();
		rrb.setFilename(msg.getResponse().getReadResponse().getFilename());
		rrb.setChunk(chb);
		rrb.setNumOfChunks(msg.getResponse().getReadResponse().getNumOfChunks());

		Response.Builder rb = Response.newBuilder();
		// request type, read,write,etc
		rb.setResponseType(TaskType.RESPONSEREADFILE);
		rb.setReadResponse(rrb);
		CommandMessage.Builder cb = CommandMessage.newBuilder();
		// Prepare the CommandMessage structure
		cb.setHeader(hb);
		cb.setResponse(rb);
		Manager.getClientChannel().writeAndFlush(cb.build());

	}

	@Override
	public synchronized void replicateDatatoNewNode(int newNodeId) {
		// TODO Auto-generated method stub
		for (String fileName : commitedFileNames) {

			if (fileChunkMap.containsKey(fileName)) {

				ArrayList<WorkMessage> replList = fileChunkMap.get(fileName);
				for (WorkMessage lwm : replList) {

					pipe.common.Common.Log.Builder lb = pipe.common.Common.Log.newBuilder();
					lb.setRwb(lwm.getRequest().getRwb());
					lb.setNewNodeId(newNodeId);
					WorkMessage.Builder wmsgl = WorkMessage.newBuilder();
					wmsgl.setSecret(10);
					wmsgl.setLog(lb);

					EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(newNodeId);
					if (ei.isActive() && ei.getChannel() != null) {
						System.out.println("LogReplication: Sending the replication chunk to " + newNodeId);
						ei.getChannel().writeAndFlush(wmsgl.build());

					}

				}

			}
		}

	}

	@java.lang.Override
	public void logReplicationMessage(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

	public void sendGlobalping() {
		Jedis jedis = new Jedis("169.254.214.175");
		System.out.println("Connection to server outside sucessfully");
		// check whether server is running or not
		System.out.println("Server is running: " + jedis.ping());
		String out = jedis.get("1");
		System.out.println("out is " + out);
		String ip = out.split(":")[0];
		int po = Integer.parseInt(out.split(":")[1]);
		Channel clusterChannel = Manager.getEdgeMonitor().connectToChannel(ip, po);
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getNodeId());
		hb.setDestination(-1);
		hb.setTime(System.currentTimeMillis());

		// PingMessage.builder pm=PingMessage.newBuilder();
		CommandMessage.Builder cb = CommandMessage.newBuilder();
		cb.setHeader(hb);
		cb.setPing(true);
		clusterChannel.writeAndFlush(cb.build());
	}

}
