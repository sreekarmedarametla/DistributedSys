package gash.router.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import chainofresponsibility.ErrorHandler;
import chainofresponsibility.Handler;
import chainofresponsibility.PingHandler;
import chainofresponsibility.ReadRequestHandler;
import chainofresponsibility.WriteRequestHandler;
import chainofresponsibility.WriteResponseHandler;
import gash.router.container.RoutingConf;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeList;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common.Chunk;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.common.Common.ReadBody;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Request;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.work.Work.WorkMessage;
import redis.clients.jedis.Jedis;
import routing.Pipe.CommandMessage;

/**
 * The message handler processes json messages that are delimited by a 'newline'
 * 
 * TODO replace println with logging!
 * 
 * @author gash
 * 
 */
public class CommandHandler extends SimpleChannelInboundHandler<CommandMessage> {
	protected static Logger logger = LoggerFactory.getLogger("cmd");
	protected RoutingConf conf;
	private Handler handler;
	ServerState state;
	Map<Integer, Channel> channelMap = new HashMap<Integer, Channel>();
	Map<Integer, Channel> writeChannelMap = new HashMap<Integer, Channel>();
	Map<String, ArrayList<CommandMessage>> fileChunkMap = new HashMap<String, ArrayList<CommandMessage>>();

	public CommandHandler(ServerState state, RoutingConf conf) {
		this.state = state;
		if (conf != null) {
			this.conf = conf;
		}
		this.handler = new ErrorHandler(state);
		Handler pingHandler = new PingHandler(state);
		Handler writeRequestHandler = new WriteRequestHandler(state);
		Handler writeResponseHandler = new WriteResponseHandler(state);
		Handler readRequestHandler = new ReadRequestHandler(state);
		// Handler readResponseHandler= new ReadResponseHandler(state);

		handler.setNext(pingHandler);
		pingHandler.setNext(writeRequestHandler);
		writeRequestHandler.setNext(readRequestHandler);

	}

	/**
	 * override this method to provide processing behavior. This implementation
	 * mimics the routing we see in annotating classes to support a RESTful-like
	 * behavior (e.g., jax-rs).
	 * 
	 * @param msg
	 */
	public void handleMessage(CommandMessage msg, Channel channel) {
		if (msg == null) {
			// TODO add logging
			System.out.println("ERROR: Unexpected content - " + msg);
			return;
		}
		state.getManager().setClientChannel(channel);
		try {

			// TODO How can you implement this without if-else statements?
			if (msg.hasPing()) {
				logger.info("ping from " + msg.getHeader().getNodeId() + " to " + msg.getHeader().getDestination());
				PrintUtil.printCommand(msg);
				if (msg.getHeader().getNodeId() == 55) {
					channelMap.put(msg.getHeader().getNodeId(), channel);
				}
				if (msg.getHeader().getDestination() == 55)
					channelMap.get(55).writeAndFlush(msg);
				else if (msg.getHeader().getDestination() == 5) {
					Header.Builder hb = Header.newBuilder();
					hb.setNodeId(msg.getHeader().getDestination());
					hb.setTime(System.currentTimeMillis());
					hb.setDestination(msg.getHeader().getNodeId());
					CommandMessage.Builder cb = CommandMessage.newBuilder();
					cb.setHeader(hb);
					cb.setPing(true);
					Jedis jedis = new Jedis("192.168.1.20");
					System.out.println("Connection to server sucessfully");
					// check whether server is running or not
					System.out.println("Server is running: " + jedis.ping());
					String out = jedis.get("6");
					System.out.println("out is " + out);
					String ip = out.split(":")[0];
					int po = Integer.parseInt(out.split(":")[1]);
					Channel clusterChannel = state.getManager().getEdgeMonitor().connectToChannel(ip, po);
					clusterChannel.writeAndFlush(cb.build());
				} else {
					Jedis jedis = new Jedis("192.168.1.20");
					System.out.println("Connection to server sucessfully");
					// check whether server is running or not
					System.out.println("Server is running: " + jedis.ping());
					String out = jedis.get("6");
					System.out.println("out is " + out);
					String ip = out.split(":")[0];
					int po = Integer.parseInt(out.split(":")[1]);
					Channel clusterChannel = state.getManager().getEdgeMonitor().connectToChannel(ip, po);
					clusterChannel.writeAndFlush(msg);
				}

			} else if (msg.getRequest().hasRwb()) {
				System.out.println("has write request");
				if (!fileChunkMap.containsKey(msg.getRequest().getRwb().getFilename())) {
					fileChunkMap.put(msg.getRequest().getRwb().getFilename(), new ArrayList<CommandMessage>());
				}
				fileChunkMap.get(msg.getRequest().getRwb().getFilename()).add(msg);

				if (state.getManager().getLeaderId() == state.getManager().getNodeId()) {
					state.getManager().getCurrentState().receivedLogToWrite(msg);
				}
				if (fileChunkMap.get(msg.getRequest().getRwb().getFilename()).size() != msg.getRequest().getRwb()
						.getNumOfChunks()) {
					if (msg.getHeader().getNodeId() == 55) {
						writeChannelMap.put(msg.getHeader().getNodeId(), channel);
					} else {
						Jedis jedis = new Jedis("192.168.1.20");
						System.out.println("Connection to server sucessfully");
						// check whether server is running or not
						System.out.println("Server is running: " + jedis.ping());
						String out = jedis.get("6");
						System.out.println("out is " + out);
						String ip = out.split(":")[0];
						int po = Integer.parseInt(out.split(":")[1]);
						Channel clusterChannel = state.getManager().getEdgeMonitor().connectToChannel(ip, po);
						clusterChannel.writeAndFlush(msg);
					}
				}
			} else if (msg.getRequest().hasRrb()) {
				System.out.println("Read Request received in CommandHandler");

				/*
				 * Class.forName("com.mysql.jdbc.Driver"); Connection con =
				 * DriverManager.getConnection(
				 * "jdbc:mysql://localhost:3306/mydb", "root", "abcd");
				 * PreparedStatement statement = con
				 * .prepareStatement("select numberofchunks from filetable where chunkid=0 && filename = ?"
				 * ); statement.setString(1,
				 * msg.getRequest().getRrb().getFilename()); ResultSet rs =
				 * statement.executeQuery();
				 * 
				 */
				
				  Class.forName("com.mysql.jdbc.Driver"); Connection con =
				  DriverManager.getConnection(
				  "jdbc:mysql://localhost:3306/mydb", "root", "root");
				  PreparedStatement statement = con
				  .prepareStatement("select numberofchunks from filetable where chunkid=0 && filename = ?"
				  ); statement.setString(1,
				  msg.getRequest().getRrb().getFilename()); ResultSet rs =
				  statement.executeQuery();
				 System.out.println("atlease here");
				 
				
				 if (rs.next()) {
					 System.out.println("sreekar you are good");
//				if (true) {
					System.out.println(rs.getInt(1));
					System.out.println("Building Message");
					WorkMessage.Builder wbr = WorkMessage.newBuilder();
					Header.Builder hbr = Header.newBuilder();
					hbr.setDestination(-1);
					hbr.setTime(System.currentTimeMillis());
					
					ReadBody.Builder rb = ReadBody.newBuilder();
					rb.setFilename(msg.getRequest().getRrb().getFilename());
					rb.setChunkId(msg.getRequest().getRrb().getChunkId());
					Request.Builder rrb = Request.newBuilder();
					rrb.setRequestType(TaskType.REQUESTREADFILE);
					rrb.setRrb(rb);

					//wbr.setHeader(hbr);
					wbr.setSecret(10);
					wbr.setRequest(msg.getRequest());
					WorkMessage wm = wbr.build();
					System.out.println("Message Built");
					state.getManager().getCurrentState().getMessageQueue().add(wm);
					System.out.println("Added to leader's queue");
				} else
					System.out.println("File not present");
			} else {
			}

		} catch (Exception e) {
			// TODO add logging
			Failure.Builder eb = Failure.newBuilder();
			eb.setId(conf.getNodeId());
			eb.setRefId(msg.getHeader().getNodeId());
			eb.setMessage(e.getMessage());
			CommandMessage.Builder rb = CommandMessage.newBuilder(msg);
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
	protected void channelRead0(ChannelHandlerContext ctx, CommandMessage msg) throws Exception {
		handleMessage(msg, ctx.channel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

}