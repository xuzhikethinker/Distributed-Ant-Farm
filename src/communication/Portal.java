package communication;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rpc.AnnotatedObject;
import rpc.RPCInjectionModule;
import utilities.A;

import com.google.inject.Guice;
import com.google.inject.Injector;

import constants.iConstants;
import distributor.DistributorInterface;
import distributor.NetworkResource.NetworkLocation;
import distributor.iDistributor;


/**
 * Abstract class representing a portal object, which is an object
 * capable of communicating via network sockets with other objects on 
 * the network.  This exists at its highest level as an iface, so that
 * other objects needing to communicate via portals aren't aware of the 
 * complexity of the implementation.  The abstract class exists so that data
 * structures and methods common to Client, Distributor, SatelliteServer and 
 * Master can be implemented in once place, rather than multiple times over.
 * Specific changes needed for those classes to common methods can be done
 * by overriding at the implementation level.
 * 
 * @author Alex Woody
 *         CS 587 Fall 2011 - DAF Project Group
 *
 */
/**
 * @author Alex Woody
 *         CS 587 Fall 2011 - DAF Project Group
 * @param <E>
 * @param <E>
 * @param <E>
 *
 */
public abstract class Portal implements iPortal
{
	protected NodeId nodeId;
	protected final Map<NodeId, NodeConnection> allConnections;
	protected final Injector injector;
	protected Recipient recipient;
	protected DistributorInterface distributor;
	protected final iConstants constants;
	protected final ExecutorService threadPool;
	
	public Portal(Recipient r, iConstants constants)
	{
		this.recipient = r;
		this.constants = constants;
		allConnections = new HashMap<NodeId, NodeConnection>();
		injector = Guice.createInjector(new RPCInjectionModule(this));
		threadPool = Executors.newCachedThreadPool();		
		//if(getClass(). instanceof Distributor)
	}
	
	
	private void spinTilNodeId()
	{
		while(true)
		{
			if(this.getNodeId() != null)
				break;
			
			try
			{
				Thread.sleep(0100);
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	protected void connectToDistributor()
	{		
		Socket connection;
		AnnotatedObject distributor = null;
		try 
		{
			connection = new Socket(constants.getDefaultDistributorHostName(), constants.getDefaultDistributorPort());
			NodeConnection distributorConnection = new NodeConnection(connection);
			
			Thread distributorThread = new Thread(distributorConnection);
			distributorThread.start();
			
			NodeId distributorNodeId = constants.getDistributorNodeId();
			allConnections.put(distributorNodeId, distributorConnection);
			
			distributor = injector.getInstance(DistributorInterface.class);
			distributor.setNodeId(distributorNodeId);
			
			this.distributor = (DistributorInterface) distributor;
			
			this.spinTilNodeId();
		} 
		catch (UnknownHostException e) 
		{
			A.error("Unable to connect to distributor");
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			A.error("Unable to connect to distributor");
			e.printStackTrace();
		}		
	}
	
	
	@Override
	public AnnotatedObject makeNewConnection(String annotatedObjectName) 
	{
		NetworkLocation objectLocation = distributor.connectionToResourceForNode(nodeId, annotatedObjectName);
		
		try 
		{
			Socket connection = new Socket(objectLocation.getAddress(), objectLocation.getPortNumber());
			
			NodeConnection newConnection = new NodeConnection(connection);
			
			Thread connectionThread = new Thread(newConnection);
			connectionThread.start();
			
			allConnections.put(objectLocation.getNodeId(), newConnection);
			
			@SuppressWarnings("unchecked")
			AnnotatedObject object = injector.getInstance(constants.getAnnotatedObjectsByString().get(annotatedObjectName));
			object.setNodeId(objectLocation.getNodeId());
			
			return object;		
		} 
		catch (UnknownHostException e) 
		{
			A.fatalError("Couldn't find the specified host.");
		} 
		catch (IOException e)
		{
			A.fatalError("Couldn't establish connection.");
		}
		
		return null;
	}
	
	
	public iDistributor distributorConnection()
	{
		if(distributor == null)
		{
			this.connectToDistributor();
		}
		
		return distributor;
	}
	
	
	public void dispatchAsynchronousPackage(AbstractPackage aPackage,
			NodeId recipient)
	{
		NodeConnection connection = allConnections.get(recipient);
		connection.sendAsynchronousPackage(aPackage);
	}


	public Object dispatchSynchronousPackage(AbstractPackage aPackage,
			NodeId recipient) 
	{
		
		NodeConnection connection = allConnections.get(recipient);
		
		if(connection != null)
			return connection.sendSynchronousPackage(aPackage);
		
		return null;
	}
	
	
	/**
	 * Sets the nodeId of this portal.
	 * 
	 * @param id - This id of this portal.
	 */
	public void setNodeId(NodeId id) 
	{
		nodeId = id;
	}

	/* (non-Javadoc)
	 * @see communication.Portal#getNodeId()
	 */
	public NodeId getNodeId() 
	{
		return nodeId;
	}
	
	
	public MessageId generateMessageId()
	{
		return new MessageId(nodeId);
	}
	
	
	
	
	
	public class NodeConnection implements Runnable
	{
		private Socket s;
		private boolean isOpen;
		private final Map<MessageId, SynchronousCallHolder> outstandingSynchronousCalls;
		
		
		public NodeConnection(Socket s)
		{
			this.s = s;		
			outstandingSynchronousCalls = new ConcurrentHashMap<MessageId, SynchronousCallHolder>();
			
			//Establish input and output streams over the socket.
	

			isOpen = true;

		}
		
				
		@Override
		public void run() 
		{	
			SynchronousCallHolder.setSocketListenerThread(Thread.currentThread());
			
			
			while(isOpen)
			{	
				try 
				{

					ObjectInputStream inputStream = new ObjectInputStream(s.getInputStream());
					
					Object o;
					if((o = inputStream.readObject()) != null)
					{
						AbstractPackage p = (AbstractPackage) o;
							
						A.say("Read package: " + p.toString());
							
						//We want to keep the listener thread active as 
						//often as possible; so all package handling is
						//threaded off to a subclass capable of handling
						//that sort of work.
						PackageHandler ph = new PackageHandler(p);
							
						threadPool.submit(ph);
					}			
					
					//inputStream.close();
				} 
				catch (ClassNotFoundException e) 
				{
					e.printStackTrace();
					A.fatalError("Unknown class sent over the object input stream.");
				}
				catch(EOFException e)
				{
					closeConnection();
					A.fatalError("Shutting down; unexpected EOF.");
				}
				catch(SocketException e)
				{
					closeConnection();
					A.fatalError("Shutting down due to socket exception: " + e.getMessage());
				}
				catch(IOException e)
				{
					e.printStackTrace();
					closeConnection();
					A.fatalError("IO Exception was thrown for this socket; socket will be closed.");
					isOpen = false;
				}
			}
		}
		
//		
//		public void resetNodeConnection(int newPort)
//		{
//			isOpen = false; //close the connection.
//
//			
//			try 
//			{
//				outputStream.close();
//				inputStream.close();
//				s.close();
//			}
//			catch (IOException e1) 
//			{
//				
//			}
//			
//						
//			try 
//			{
//				A.say("Will attempt to open new connection to: " + newPort);
//				s = new Socket("localhost", newPort);	
//				outputStream = new SaltyBitch(s.getOutputStream());
//				inputStream = new ObjectInputStream(s.getInputStream());
//				isOpen = true;
//			} 
//			catch (IOException e) 
//			{
//				A.say("Warning, failed to properly reset the nodeConnection.  Socket is not open.");
//				isOpen = false;
//				e.printStackTrace();
//			}
//			
//		}
//		
		
		public NodeId getNodeId()
		{
			return nodeId;
		}
		
		
		public boolean canSend()
		{
			if(nodeId == null) 
			{
				A.error("Cannot send packages without valid nodeId");
				return false;
			}
			
			if(!isOpen)
			{
				A.error("Cannot send packages via a closed socket.");
				return false;
			}
			
			return true;
		}
		
				
		public Object sendSynchronousPackage(AbstractPackage aPackage)
		{
			//Without a node id, you can only receive.
			//If you aren't open, however, you can't do anything.
			if(!canSend())
			{
				A.error("This node was unable to send package: " + aPackage);
				return null;
			}
			
			Object o = null;
			
			writeToOutputStream(aPackage);
			A.say("Sent a synchronous package from " + nodeId + " The package was: " + aPackage.toString());
				
				
			SynchronousCallHolder holder = new SynchronousCallHolder(Thread.currentThread(), aPackage, this);
			
			
			outstandingSynchronousCalls.put(aPackage.messageId(), holder);
			
			//System.out.println("Blocking this thread with key: " + aPackage.messageId());
			holder.holdThread(); //This will cause execution to block here until the message returns.
				
			//When execution gets to here, it means the holder's return value has been set by the listener
			//thread and the thread which was trapped here has been resumed.  Good times.  We can return
			//from this RPC with our return value and continue on our merry way.
			o = holder.getReturnValue();
			//System.out.println("Thread was unblocked.");
			
			
			
			return o;
		}
		
		
		/**
		 * I think multiple threads might have been grabbing this stream 
		 * at the server and sending fucking crazy shit over it, resulting
		 * in wild exceptions at the client and all kinds of chaos.
		 * 
		 * @param o
		 */
		public synchronized void writeToOutputStream(Object o)
		{
			
			
			try 
			{
				ObjectOutputStream outputStream = new ObjectOutputStream(s.getOutputStream());
				outputStream.writeObject(o);
			}
			catch (IOException e)
			{
				A.fatalError("Unable to send the requested object from a Node Connection");
				//e.printStackTrace();
			}
		}
		
		
		public void sendAsynchronousPackage(AbstractPackage aPackage)
		{
			//Without a node id, you can only receive.
			//If you aren't open, however, you can't do anything.
			if(!canSend())
			{
				A.error("This node was unable to send package: " + aPackage);
				return;
			}
			
			writeToOutputStream(aPackage);
		}
		
		
		public void closeConnection()
		{
			try 
			{				
				s.close();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			finally
			{
				isOpen = false;
			}
			
		}
		
		private class PackageHandler implements Runnable
		{
			private AbstractPackage ap;
			
			public PackageHandler(AbstractPackage ap)
			{
				this.ap = ap;
			}

			@Override
			public void run() 
			{
				if(ap instanceof ResourceIdentificationPackage)
				{
					ResourceIdentificationPackage rip = (ResourceIdentificationPackage) ap;
					
					if(rip.isReturningToSender())
					{
						
						SynchronousCallHolder holder = outstandingSynchronousCalls.remove(rip.messageId());
						Object [] response = {rip.getResourceNodeId(), rip.getResourceObjectName()};
							
						holder.setReturnValue(response);
						holder.continueThread();
								
					}
					else
					{
						rip.setResourceNodeId(getNodeId());
						rip.setResourceObjectName(recipient.getResourceName());
						
						rip.flip();
						sendAsynchronousPackage(rip);
					}
				}							
				else if(ap instanceof InitializationPackage)
				{								
					nodeId = ((InitializationPackage) ap).getIdForNewNode();
					
					if(recipient != null)
						recipient.setNodeId(nodeId);
					
					//A.log("Node Connection received init package.  Setting nodeId to " + nodeId);
				}														
				else if(ap instanceof InvocationPackage)
				{
					
					InvocationPackage ip = (InvocationPackage) ap;
					
					if(ip.isSynchronous())
					{
						Object returnValue = recipient.invokeMethod(ip.getMethodName(), ip.getArguments());
						
						//Create a response package with the same message id since there is a thread waiting on
						//this return value on the other side.
						ResponsePackage responsePackage = new ResponsePackage(nodeId, ip.getMessageId(), returnValue);
						
						//Dispatch the package back where it came from.
						sendAsynchronousPackage(responsePackage);;
					}
					else
					{
						recipient.invokeMethod(ip.getMethodName(), ip.getArguments());
					}
				}
				else if(ap instanceof ResponsePackage)
				{
					//A.log("Received a response package.");
					ResponsePackage response = (ResponsePackage) ap;
					
					//This package must contain a response to something which was sent out at
					//an earlier point; there is a thread waiting on it, so first set the 
					//thread's holder's return value, and then resume the thread.
							
					SynchronousCallHolder holder = outstandingSynchronousCalls.remove(response.messageId());
					
					if(holder != null)
					{	
						holder.setReturnValue(response.getReturnValue());
						holder.continueThread();
					}
					else
						A.error("Something bizzare happened; there was a null holder for response package: " + response.getReturnValue());
				}
			}
		}
		
	}

}
