package gash.router.server;

import java.util.concurrent.LinkedBlockingDeque;

import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public class Monitor {
	public LinkedBlockingDeque<WorkMessage> incomingWorkMessageQueue;
	public LinkedBlockingDeque<WorkMessage> outgoingWorkMessageQueue;
	public LinkedBlockingDeque<CommandMessage> incomingcommandMessageQueue;
	public LinkedBlockingDeque<CommandMessage> outgingcommandMessageQueue;
	
	public void enqueueIncomingWork(WorkMessage msg){
		try {
			incomingWorkMessageQueue.put(msg);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
