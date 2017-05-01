package chainofresponsibility;

import gash.router.server.ServerState;
import io.netty.channel.Channel;
import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;
public abstract class Handler {

	protected Handler next;

    protected ServerState state;
    
    public Handler(ServerState state) {
        if (state != null) {
            this.state = state;
        }
    }

    public void setNext(Handler handler) {
        next = handler;
    }

    public abstract void processWorkMessage(WorkMessage message, Channel channel);

    public abstract void processCommandMessage(CommandMessage message, Channel channel);


}
