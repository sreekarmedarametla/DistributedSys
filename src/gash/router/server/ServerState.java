package gash.router.server;

import java.util.concurrent.LinkedBlockingDeque;

import gash.router.container.RoutingConf;
import gash.router.server.edges.EdgeMonitor;
import gash.router.server.tasks.TaskList;
import pipe.work.Work.WorkMessage;
//import raft.Candidate;
import raft.FollowerState;
import raft.RaftManager;
import routing.Pipe.CommandMessage;

public class ServerState {
	private RoutingConf conf;
	private EdgeMonitor emon;
	private TaskList tasks;	
	private RaftManager manager;
	private String state="";
	private Monitor monitor;
	
	public Monitor getMonitor(){
		return monitor;
	}
	
	public RaftManager getManager() {
		return manager;
	}

	public void setManager(RaftManager mgr) {
		manager = mgr;
	}


	public String getState() { 
		return state;
    }

	public void setState(String state) { 
		this.state = state;
    }
	
	public RoutingConf getConf() {
		return conf;
	}
	
	public void setConf(RoutingConf conf) {
		this.conf = conf;
	}

	public EdgeMonitor getEmon() {
		return emon;
	}

	public void setEmon(EdgeMonitor emon) {
		this.emon = emon;
	}

	public TaskList getTasks() {
		return tasks;
	}

	public void setTasks(TaskList tasks) {
		this.tasks = tasks;
	}
	
	

}
