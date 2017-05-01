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
package gash.router.client;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.netty.channel.Channel;
import pipe.common.Common;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.ReadBody;
import pipe.common.Common.Request;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteBody;
import redis.clients.jedis.Jedis;
import routing.Pipe.CommandMessage;


/**
 * front-end (proxy) to our service - functional-based
 * 
 * @author gash
 * 
 */
public class MessageClient {
	// track requests
	private long curID = 0;
	protected static Logger logger = LoggerFactory.getLogger("Client");
	public MessageClient(String host, int port) {
		init(host, port);
	}

	private void init(String host, int port) {
		CommConnection.initConnection(host, port);
	}

	public void addListener(CommListener listener) {
		CommConnection.getInstance().addListener(listener);
	}
	
	public void ping() {
		// construct the message to send
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(999);
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		CommandMessage.Builder rb = CommandMessage.newBuilder();
		rb.setHeader(hb);
		rb.setPing(true);

		try {
			// direct no queue
			// CommConnection.getInstance().write(rb.build());

			// using queue
			CommConnection.getInstance().enqueue(rb.build());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	// Save File to server
			public void writeFile(String filename, ByteString chunkData, int noOfChunks, int chunkId) {
				
				logger.info("Printing byte size"+chunkData.size());
				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(999);
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);
				
				Chunk.Builder chb=Chunk.newBuilder();
				chb.setChunkId(chunkId);
				chb.setChunkData(chunkData);
				chb.setChunkSize(chunkData.size());
				
				WriteBody.Builder wb=WriteBody.newBuilder();
				
				wb.setFilename(filename);
				wb.setChunk(chb);
				wb.setNumOfChunks(noOfChunks);
				
				Request.Builder rb = Request.newBuilder();
				//request type, read,write,etc				
				rb.setRequestType(Common.TaskType.REQUESTWRITEFILE); // operation to be
																// performed
				rb.setRwb(wb);	
				CommandMessage.Builder cb = CommandMessage.newBuilder();
				// Prepare the CommandMessage structure
				cb.setHeader(hb);
				cb.setRequest(rb);				

				// Initiate connection to the server and prepare to save file
				try {
					CommConnection.getInstance().enqueue(cb.build());
				} catch (Exception e) {
					e.printStackTrace();
					logger.error("Problem connecting to the system");
				}			

			}

	/*public void release() {
		CommConnection.getInstance().release();
	}*/

	/**
	 * Since the service/server is asychronous we need a unique ID to associate
	 * our requests with the server's reply
	 * 
	 * @return
	 */
	private synchronized long nextId() {
		return ++curID;
	}

	public void globalPing(int id){
		
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(55);
		hb.setDestination(id);
		hb.setTime(System.currentTimeMillis());
		hb.setMaxHops(10);
		
		//PingMessage.builder pm=PingMessage.newBuilder();
		CommandMessage.Builder cb=CommandMessage.newBuilder();
		cb.setHeader(hb);
		cb.setPing(true);
		
		try {
			// direct no queue
			// CommConnection.getInstance().write(rb.build());

			// using queue
			CommConnection.getInstance().enqueue(cb.build());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void readFile(String fileName) {
		// TODO Auto-generated method stub
		Header.Builder hb = Header.newBuilder();
		// prepare the Header Structure
		hb.setNodeId(999);
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		// prepare the read body request Structure
		ReadBody.Builder rrb=ReadBody.newBuilder();
		rrb.setFilename(fileName);

		Request.Builder rb = Request.newBuilder();
		rb.setRequestType(TaskType.REQUESTREADFILE);
		rb.setRrb(rrb);		
		
		CommandMessage.Builder cb = CommandMessage.newBuilder();
		// Prepare the CommandMessage structure
		cb.setHeader(hb);
		cb.setRequest(rb);		

		// Initiate connection to the server and prepare to read and save file
		try {

			CommConnection.getInstance().enqueue(cb.build());
		} catch (Exception e) {
			e.printStackTrace();
		}

		
	}
}
