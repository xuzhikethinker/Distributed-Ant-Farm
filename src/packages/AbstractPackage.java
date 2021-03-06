package packages;

import java.io.Serializable;

import communication.NodeId;

/**
 * Baseline class for all packages; meant to be overridden
 * by specific packages, such as initialization, movement, etc.
 * There are currently no implementation specific details here;
 * it's meerly important that this class exist as a marker for now
 * so that the server objects and infrastructure can pass it amongst
 * themselves.
 * 
 * @author DeepBlue
 *
 */
public abstract class AbstractPackage implements Serializable
{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -708002699640630109L;
	protected final NodeId sourceId;
	protected final MessageId messageId;
	
	public AbstractPackage(NodeId id, MessageId messageId)
	{
		sourceId = id;
		this.messageId = messageId;
	}
	
	public NodeId nodeId()
	{
		return sourceId;
	}
	
	public MessageId messageId()
	{
		return messageId;
	}
	
	public abstract String toString();
}
