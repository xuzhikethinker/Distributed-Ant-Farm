package communication;

import java.io.Serializable;

public class InvocationPackage extends AbstractPackage
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3620789372542250320L;
	private String methodName;
	private Object [] arguments;
	private boolean isSynchronous;
	
	public InvocationPackage(NodeId nodeId, MessageId messageId, String methodName, MessageType type, Object [] arguments)
	{
		super(nodeId, messageId);
		// TODO Auto-generated constructor stub
		
		if(arguments!= null)
		{	
			for(Object o : arguments)
			{
				if(!(o instanceof Serializable))
					throw new IllegalArgumentException("All arguments to an invocation package must be serializable");
			}
		}
		
		isSynchronous = (type == MessageType.ASYNCHRONOUS) ? false : true;			
			
		this.methodName = methodName;
		this.arguments = arguments;
	}


	public MessageId getMessageId() 
	{
		return messageId;
	}


	public boolean isSynchronous() 
	{
		return isSynchronous;
	}

	
	public String getMethodName() 
	{
		return methodName;
	}

	public Object[] getArguments()
	{
		return arguments;
	}

	@Override
	public String toString()
	{
		return "Method invocation package from: " + sourceId + " for method: " + methodName + " with arguments: " + arguments + " isSynchronous: " + isSynchronous + " messageId: " + messageId;	
	}

}