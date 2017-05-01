/**
 * @author Labhesh
 * @since 25 Mar,2017.
 */
package chainofresponsibility;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import io.netty.channel.Channel;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.work.Work;
import routing.Pipe.CommandMessage;

public class ReadRequestHandler extends Handler {
    public ReadRequestHandler(ServerState state) {
		super(state);
		// TODO Auto-generated constructor stub
	}


	Logger logger = LoggerFactory.getLogger(ReadRequestHandler.class);
    

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.getRequest().hasRrb()) {
        	System.out.println("im handling read req");        	
        } else {
        	System.out.println("no read req going to read response handler");
            next.processWorkMessage(message, channel);
        }
    }

   @Override
    public void processCommandMessage(CommandMessage message, Channel channel) {
	   if(message.getRequest().hasRrb() ){
			System.out.println("request taken forwarding to followers");
			//&& (state.getManager().getCurrentState().getClass()== LeaderState.class)
			try{
			Class.forName("com.mysql.jdbc.Driver");  
			Connection con=DriverManager.getConnection(  
			"jdbc:mysql://localhost:3306/mydb","root","abcd");  			    
			PreparedStatement statement = con.prepareStatement("select * from filetable where filename = ?");    
			statement.setString(1, message.getRequest().getRrb().getFilename());    
			ResultSet rs = statement.executeQuery();  				  
			while(rs.next()){ 
				System.out.println("fetching a chunk");
				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(999);
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);
				
				Chunk.Builder chb=Chunk.newBuilder();
				chb.setChunkId(rs.getInt(2));
				ByteString bs=ByteString.copyFrom(rs.getBytes(3));
				System.out.println("byte string "+bs);
				chb.setChunkData(bs);
				chb.setChunkSize(rs.getInt(4));
				
				ReadResponse.Builder rrb=ReadResponse.newBuilder();						
				rrb.setFilename(rs.getString(1));
				rrb.setChunk(chb);
				rrb.setNumOfChunks(rs.getInt(5));
				
				Response.Builder rb = Response.newBuilder();
				//request type, read,write,etc				
				rb.setResponseType(TaskType.RESPONSEREADFILE);
				rb.setReadResponse(rrb);
				CommandMessage.Builder cb = CommandMessage.newBuilder();
				// Prepare the CommandMessage structure
				cb.setHeader(hb);
				cb.setResponse(rb);		
				channel.writeAndFlush(cb.build());
			}  
			}catch(Exception e){
				e.printStackTrace();
			}
		}else {
			System.out.println("no read request");
            next.processCommandMessage(message, channel);
        }
    }

  /*  @Override
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
