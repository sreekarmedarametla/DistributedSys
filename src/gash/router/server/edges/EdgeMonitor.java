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
package gash.router.server.edges;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.container.RoutingConf.RoutingEntry;
import gash.router.server.ServerState;
import gash.router.server.WorkInit;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import pipe.common.Common.AddNewNode;
import pipe.common.Common.Header;
import pipe.election.Election.RequestVote;
import pipe.election.Election.Vote;

import pipe.work.Work.Heartbeat;

import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
//import raft.Candidate;
import raft.LeaderState;

public class EdgeMonitor implements EdgeListener, Runnable {
	protected static Logger logger = LoggerFactory.getLogger("edge monitor");

	private EdgeList outboundEdges;
	private EdgeList inboundEdges;
	private long dt = 2000;
	private ServerState state;
	private boolean forever = true;

	public EdgeMonitor(ServerState state) {
		if (state == null)
			throw new RuntimeException("state is null");

		this.outboundEdges = new EdgeList();
		this.inboundEdges = new EdgeList();
		this.state = state;
		this.state.setEmon(this);

		if (state.getConf().getRouting() != null) {
			for (RoutingEntry e : state.getConf().getRouting()) {
				outboundEdges.addNode(e.getId(), e.getHost(), e.getPort());
			}
		}

		// cannot go below 2 sec
		if (state.getConf().getHeartbeatDt() > this.dt)
			this.dt = state.getConf().getHeartbeatDt();
	}

	public void createOutBoundIfNew(int ref, String host, int port) {
		outboundEdges.createIfNew(ref, host, port);
	}
	
	public void shutdown() {
		forever = false;
	}

	public Channel connectToChannel(String host, int port) {
        Bootstrap b = new Bootstrap();
        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup();
        WorkInit workInit = new WorkInit(state, false);

        try {
            b.group(nioEventLoopGroup).channel(NioSocketChannel.class).handler(workInit);
            b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
            b.option(ChannelOption.TCP_NODELAY, true);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            // Make the connection attempt.
        } catch (Exception e) {
            logger.error("Could not connect to the host " + host);
            return null;
        }
        return b.connect(host, port).syncUninterruptibly().channel();

    }
	boolean initial=true;
	public synchronized void newNodePing(){
		for(EdgeInfo ei:this.outboundEdges.map.values())
		 {
			System.out.println("in new node for");
			if(ei.isActive()&&ei.getChannel()!=null)
			{	
				System.out.println("in new node if");
				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(state.getConf().getNodeId());
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);
				String selfHost = " ";
				try {
					selfHost = Inet4Address.getLocalHost().getHostAddress();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				int selfPort=state.getConf().getWorkPort();
				AddNewNode.Builder ab=AddNewNode.newBuilder();
				ab.setHost(selfHost);
				ab.setPort(selfPort);
				
				WorkMessage.Builder wb = WorkMessage.newBuilder();
				wb.setHeader(hb);				
				wb.setAddnewnode(ab);
				wb.setSecret(10);
				sendMessage(wb.build());
				System.out.println("sent ping request to"+ei.getRef());				
			}				
		 }
		initial=false;
	}
	
	@Override
	//it will be taken care by leader
	public void run() {
		while (forever) {
			try {
                for (EdgeInfo ei : this.outboundEdges.map.values()) {
                    if (ei.isActive()) {
                        if (!ei.getChannel().isActive()) {
                            ei.setActive(false);                          	
                        	this.inboundEdges.addNode(ei.getRef(), ei.getHost(), ei.getPort());
                        }                        
                    } else {                    	 
                        try {
                            logger.info("looking for edge" + ei.getRef());
                            Channel channel = connectToChannel(ei.getHost(), ei.getPort());
                            ei.setChannel(channel);
                            if (channel.isActive()) {
                                ei.setActive(true);                                
                                this.inboundEdges.addNode(ei.getRef(), ei.getHost(), ei.getPort());
                            }
                        } catch (Throwable ex) {
                        }
                    }

                }
                if(initial==true)
                	newNodePing();
                Thread.sleep(dt);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
			
	}
	}
	public void sendMessage(WorkMessage wmsg){
		try {
			for (EdgeInfo ei : this.outboundEdges.map.values()) {				
				if (ei.isActive() && ei.getChannel() != null) {  					   
                    	WorkMessage wm = wmsg;
						ei.getChannel().writeAndFlush(wm);    						                    
				}
			}
            Thread.sleep(dt);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendChunk(WorkMessage wm){
		
			for (EdgeInfo ei : this.outboundEdges.map.values()) {				
				if (ei.isActive() && ei.getChannel() != null) {  					   
                    	
						ei.getChannel().writeAndFlush(wm);    						                    
				}
			}            
		
	}

	@Override
	public synchronized void onAdd(EdgeInfo ei) {
		// TODO check connection
	}

	@Override
	public synchronized void onRemove(EdgeInfo ei) {
		// TODO ?
	}
	public synchronized EdgeList getOutBoundEdges(){
		return outboundEdges;
	}
	
}