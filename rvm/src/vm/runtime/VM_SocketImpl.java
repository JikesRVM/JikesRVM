/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.io.IOException;
import java.io.FileDescriptor;
import java.io.FileDescriptor;
import java.net.*;

public class VM_SocketImpl {

private static final boolean trace = false;

/**
 * Query the IP stack for the local address to which this socket is bound.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD			the socket descriptor
 * @return		InetAddress	the local address to which the socket is bound
 */

public static InetAddress getSocketLocalAddressImpl(FileDescriptor aFD) {
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    int localaddr = VM.sysCall1(bootRecord.sysNetSocketLocalAddressIP, aFD.fd);

    if (localaddr == -1) VM.sysFail("Socket has no local address!");

    return new InetAddress( localaddr );
}


/**
 * Query the IP stack for the local port to which this socket is bound.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket descriptor
 * @return		int		the local port to which the socket is bound
 */

public static int getSocketLocalPortImpl(FileDescriptor aFD) {

	int localPort;

	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
        localPort = VM.sysCall1(bootRecord.sysNetSocketPortIP,
				   aFD.fd);
	return localPort;

}  // end method getSocketLocalPortImpl


/**
 * Query the IP stack for the nominated socket option.  Not that we
 * really support this.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket descriptor
 * @param		opt		the socket option type
 * @return		Object	the nominated socket option value
 * @exception	SocketException		thrown if the option is invalid
 */

public static Object getSocketOptionImpl(FileDescriptor aFD, int opt)
	throws SocketException {
	throw new VM_UnimplementedError("RVM code for SocketJpn.getSocketOptionImpl");
	//return null;
}


/**
 * Set the nominated socket option in the IP stack.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD			the socket descriptor
 * @param		opt			the option selector
 * @param		optVal		the nominated option value
 * @exception	SocketException		thrown if the option is invalid or cannot be set
 *
 * java 2 supports only 4 socket options: TCP_NODELAY, SO_LINGER,
 * SO_TIMEOUT, IP_MULTICAST_IF.  Of these, SO_TIMEOUT is handled by higher
 * level code, and the remaining three should be passed to the underlying
 * op sys socket code for handling.  This code, which passes the option
 * to the op sys, only handles TCP_NODELAY. The code for SO_LINGER is
 * untested and pre OTI library.  As for IP_MULTICAST_IF, that
 * only applies to MulticastSocket, which are also not implemented
 */

public static void setSocketOptionImpl(FileDescriptor aFD, int option, Object optVal)
	throws SocketException {

       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
       
       switch (option)
          {
          case SocketOptions.SO_LINGER: {
	  if (optVal instanceof Integer)
	      { // when socket is closed on this end, wait until unsent data has been received
		  // by other end or timeout expires
		  //
		  int rc = VM.sysCall3(bootRecord.sysNetSocketLingerIP, aFD.fd, 1, ((Integer)optVal).intValue());
		  if (rc == -1) throw new SocketException("SO_LINGER"); // !!TODO: what additional details should be supplied with exception?
	      }
	  else
	      { // when socket is closed on this end, discard any unsent data
		  //
		  int rc = VM.sysCall3(bootRecord.sysNetSocketLingerIP, aFD.fd, 0, 0);
		  if (rc == -1) throw new SocketException("SO_LINGER"); // !!TODO: what additional details should be supplied with exception?
	      }
	  } break;

          case SocketOptions.TCP_NODELAY:
          { // true:  send data immediately when socket is written to
            // false: delay sending, in order to coalesce packets
          int rc = VM.sysCall2(bootRecord.sysNetSocketNoDelayIP,
				     aFD.fd,
				     ((Boolean)optVal).booleanValue() ? 1 : 0);
          if (rc == -1) throw new SocketException("setTcpNoDelay");
          break;
          }
          
          default:
          VM._assert(VM.NOT_REACHED);
          }
    
}  // end method setSocketOptionImpl


/**
 * Bind the socket to the port/localhost in the IP stack.
 *
 * Associate a local address and port with this socket.
 * Taken:    desired local address
 *           desired local port
 * Returned: nothing (socket is bound)
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket descriptor
 * @param		localPort	the option selector
 * @param		localAddress	the nominated option value
 * @exception	SocketException		thrown if bind operation fails
 */

public static void socketBindImpl(FileDescriptor aFD, int localPort,
			   InetAddress localAddress)
	throws SocketException {

       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
       int rc = VM.sysCall4(bootRecord.sysNetSocketBindIP,
				  aFD.fd,
				  localAddress.family,
				  localAddress.address,
				  localPort);

       if (rc != 0)
          throw new BindException();

}  // end method socketBindImpl


/**
 * Close the socket in the IP stack.
 * from oti Socket
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket descriptor
 */

public static void socketCloseImpl(FileDescriptor aFD) {

       if (trace) VM_Scheduler.trace("VM_SocketImpl.socketCloseImpl",
				     "socketClose", aFD.fd);
       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;

       int fd = aFD.fd;
       int rc = VM.sysCall1(bootRecord.sysNetSocketCloseIP,
				  fd);
    
}  // end method socketCloseImpl


/**
 * Answer the result of attempting to accept a connection request
 * to this stream socket in the IP stack.
 *
 * Wait for connection to appear on this socket.
 * Taken:    place to put information about connection that appeared
 * Returned: nothing
 *
 * @author		Derek
 * @version		initial
 *
 * @param		fdServer	the server socket FileDescriptor
 * @param		newSocket	the host socket a connection will be accepted on
 * @param		fdnewSocket	the FileDescriptor for the host socket
 * @param		timeout		the timeout that the server should listen for
 * @exception	SocketException	if an error occurs while accepting connections
 */

public static FileDescriptor acceptStreamSocketImpl(
	FileDescriptor fdServer,
	SocketImpl newSocket,
	int timeout)
		throws SocketException {

    if (timeout != 0)
	VM.sysWrite("VM_SocketImpl.acceptStreamSocketImpl timeout(" +
		    timeout +
		    ") not implemented: ignoring\n"); // !!TODO
    
    if (trace) VM_Scheduler.trace("VM_SocketImpl.acceptStreamSocketImpl",
				  "socketAccept BEGIN",
				  fdServer.fd);
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) 
	VM_EventLogger.logNetAcceptBegin(fdServer.fd);
    
    for (;;) {
	// PIN(newSocket);
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	VM_ThreadIOQueue.selectInProgressMutex.lock();
	int connectionFd = VM.sysCall2(bootRecord.sysNetSocketAcceptIP,
				       fdServer.fd,
				       VM_Magic.objectAsAddress(newSocket).toInt());
	VM_ThreadIOQueue.selectInProgressMutex.unlock();

	// Note that sysNetSocketAccept fills in the InetAddress
	// in the new socket (family, and integer address); and
	// the port field.  In the unlikely event that someone
	// has already resolved the interger address and cached
	// this value in the object (see InetAddress.getHostName() )
	// reset the host value.
	newSocket.getInetAddress().hostName = null;
	// UNPIN(newSocket);
	
	if (connectionFd >= 0)
	    { // success
		// fdnewSocket could work too
		// newSocket.localport       = ***  dont have this localport;
		
		// enable non-blocking reads/writes on connection
		//
		if (VM.sysCall2(bootRecord.sysNetSocketNoBlockIP, connectionFd, 1) != 0)
		    // !!TODO: what additional details should be supplied>
		    throw new SocketException(); 
		
		if (trace)
		    VM_Scheduler.trace("VM_SocketImpl.acceptStreamSocketImpl",
				       "socketAccept END: new connection",
				       connectionFd);
		if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		    VM_EventLogger.logNetAcceptEnd(fdServer.fd, connectionFd);
		return new FileDescriptor( connectionFd );
	    }
	
	if (connectionFd == -1)
	    { // operation interrupted by timer tick - retry
		if (trace)
		    VM_Scheduler.trace("VM_SocketImpl.acceptStreamSocketImpl",
				       "socketAccept interrupted RETRY",
				       fdServer.fd);
		if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		    VM_EventLogger.logNetAcceptRetry(fdServer.fd);
		Thread.currentThread().yield();
		continue;
	    }
	
	if (connectionFd == -2)
	    { // operation would have blocked
		if (trace)
		    VM_Scheduler.trace("VM_SocketImpl.acceptStreamSocketImpl",
				       "socketAccept would have blocked RETRY",
				       fdServer.fd);
		if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		    VM_EventLogger.logNetAcceptRetry(fdServer.fd);

		VM_Thread.ioWaitRead(fdServer.fd);
		continue;
	    }
	
	throw new SocketException(); // !!TODO: what additional details should be supplied with exception?
    }
    
}  // end method acceptStreamSocketImpl


/**
 * Answer the number of bytes available to read on the socket
 * without blocking.
 *
 * @author		Julian Dolby
 * @version		initial
 *
 * @param		aFD		the socket FileDescriptor
 * @exception	SocketException	if an error occurs while peeking
 */

public static int availableStreamImpl(FileDescriptor aFD)
	throws SocketException {
    return VM_FileSystem.bytesAvailable( aFD.fd );
}


/**
 * Connect the underlying socket to the nominated remotehost/port.
 *
 * Associate a remote address and port with this socket.
 * Taken:    desired remote address
 *           desired remote port
 * Returned: nothing (socket is connected)
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket FileDescriptor
 * @param		remotePort	the remote machine port to connect to
 * @param		remoteAddress	the address of the remote machine to connect to
 * @exception	ConnectException	connection refused at remote host
 * @exception	NoRouteToHostException	connection attempt timed out, or
 *					host unavailable
 * @excpetion	UnknownHostException	host address unresolved
 * @exception	SocketException		if an error occurs while connecting
 */

public static void connectStreamSocketImpl(FileDescriptor aFD,
				    int remotePort,
				    InetAddress remoteAddress)
	throws IOException {

       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
       
       if (trace) VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl", 
				     "socketConnect BEGIN", aFD.fd);

       if (VM.BuildForEventLogging && VM.EventLoggingEnabled) 
	   VM_EventLogger.logNetConnectBegin(aFD.fd);

       int rc = -1;
       while (rc < 0) {
	   VM_ThreadIOQueue.selectInProgressMutex.lock();
          rc = VM.sysCall4(bootRecord.sysNetSocketConnectIP,
				     aFD.fd, 
				     remoteAddress.family,
				     remoteAddress.address,
				     remotePort);
	  VM_ThreadIOQueue.selectInProgressMutex.unlock();

          switch (rc) {
	  case 0 : // success
             break;
             
          case -1 : // operation interrupted by timer tick - retry
             if (trace)
	          VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl",
		                     "socketConnect interrupted RETRY",
				     aFD.fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		 VM_EventLogger.logNetConnectRetry(aFD.fd);
             Thread.currentThread().yield();
             break;
             
          case -2 :  // operation would have blocked
             if (trace)
		 VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl",
				    "socketConnect would have blocked RETRY",
				    aFD.fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		 VM_EventLogger.logNetConnectRetry(aFD.fd);
	     VM_Thread.ioWaitWrite( aFD.fd );
             break;

	  case -4 : // errno was ECONNREFUSED
	     throw new ConnectException("Connection refused");

	  case -5 : // errno was EHOSTUNREACH
	     throw new NoRouteToHostException();

	  case -3 :
	  default :
          throw new IOException(); // !!TODO: what additional details should be supplied with exception?
	  }

       }
       
       if (trace) VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl",
				     "socketConnect END ",
				     aFD.fd);
       if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
	   VM_EventLogger.logNetConnectEnd(aFD.fd);
       
}  // end method connectStreamSocketImpl


/**
 * Answer the result of attempting to create a stream socket in
 * the IP stack.
 *
 * Create a socket, unassociated with any particular address + port.
 * Returned: nothing (fd is set to o/s socket descriptor)
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket FileDescriptor
 * @exception	SocketException	if an error occurs while creating the socket
 */

public static FileDescriptor createStreamSocketImpl() throws SocketException {

    int fd;
    boolean isStream = true;
    
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    
    /*
     * The field fd of FileDescription is is the integer UNIX file
     * descriptor.  This filed is not documented since it is normally
     * private.  Reference to the field is possible becuase the version
     * of FileDescriptor that we use has modified the field fd to
     * be public
     */
    fd = VM.sysCall1(bootRecord.sysNetSocketCreateIP, isStream ? 1 : 0);

    if (fd < 0)
	// !!TODO: what additional details should be supplied with exception?
	throw new SocketException(); 

    if (VM.sysCall2(bootRecord.sysNetSocketNoBlockIP, fd, 1) != 0)
	// !!TODO: what additional details should be supplied>
	throw new SocketException(); 
       
    if (trace) 
	VM_Scheduler.trace(
	   "VM_SocketImpl.createStreamSocketImpl", "socketCreate", fd);

    return new FileDescriptor(fd);    
}


/**
 * Answer the result of attempting to listen on a stream socket in
 * the IP stack.
 *
 * Make this socket into a "listener" so we can accept() connections on it.
 * Taken:    max number of pending connections to allow
 * Returned: nothing
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket FileDescriptor
 * @param		backlog	the number of connection requests that may be queued
 *						before requests are rejected
 * @exception	SocketException	if an error occurs while listening
 */

public static void listenStreamSocketImpl(FileDescriptor aFD, int backlog)
	throws SocketException {

       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
       int rc = VM.sysCall2(bootRecord.sysNetSocketListenIP,
				  aFD.fd, backlog);

       if (rc == -1)
          throw new SocketException(); // !!TODO: what additional details should be supplied with exception?

}  // end method listenStreamSocketImpl


/**
 * Recieve at most <code>count</code> bytes into the buffer <code>data</code>
 * at the <code>offset</code> on the socket.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		aFD		the socket FileDescriptor
 * @param		data	the receive buffer
 * @param		offset	the offset into the buffer
 * @param		count	the max number of bytes to receive
 * @param		timeout	the max time the read operation should block waiting for data
 * @return		int		the actual number of bytes read
 * @exception	SocketException	if an error occurs while reading
 */

public static int receiveStreamImpl(
		FileDescriptor aFD,
		byte[] data,
		int offset,
		int count,
		int timeout
		) throws IOException {

	/*
       if (timeout != 0)
          VM.sysWrite("VM_SocketImpl.receiveStreamImpl timeout(" +
	              timeout +
		      ") not implemented: ignoring\n"); // !!TODO
	*/
          
       int fd = aFD.fd;

       if (trace) VM_Scheduler.trace("VM_SocketImpl.receiveStreamImpl",
				     "read BEGIN", fd);
       if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
	      VM_EventLogger.logNetReadBegin(fd);

       for (;;)
          {
          int rc = VM_FileSystem.readBytes(fd, data, offset, count);
          if (rc > 0)
             { // ok
             if (trace) VM_Scheduler.trace("VM_SocketImpl.receiveStreamImpl",
					   "read END", fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		   VM_EventLogger.logNetReadEnd(fd);

             if (VM.BuildForNetworkMonitoring)
		    VM_Thread.getCurrentThread().netReads += 1;
             return rc;
             }

          if (rc == 0)
             { // eof
             if (trace) VM_Scheduler.trace("VM_SocketImpl.receiveStreamImpl",
					   "read END", fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		   VM_EventLogger.logNetReadEnd(fd);

             if (VM.BuildForNetworkMonitoring)
		    VM_Thread.getCurrentThread().netReads += 1;
             return -1;
             }

          if (rc == -1)
             { // operation would have blocked
             if (trace) VM_Scheduler.trace("VM_SocketImpl.receiveStreamImpl",
					   "read would have blocked RETRY",
					   fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		     VM_EventLogger.logNetReadRetry(fd);
	     VM_Thread.ioWaitRead(fd);
             continue;
             }
          throw new IOException(); // !!TODO: what additional details should be supplied with exception?
          }

}  // end method receiveStreamImpl


/**
 * Send <code>count</code> bytes from the buffer <code>data</code>
 * at the <code>offset</code>, on the socket.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		data	the send buffer
 * @param		offset	the offset into the buffer
 * @param		count	the number of bytes to receive
 * @return		int		the actual number of bytes sent
 * @exception	SocketException	if an error occurs while writing
 */

public static int sendStreamImpl(
		FileDescriptor ifd,
		byte[] data,
		int offset,
		int count
		) throws IOException {

       int fd = ifd.fd;

       if (trace) VM_Scheduler.trace("VM_SocketImpl.sendStreamImpl",
				     "write BEGIN", fd);
       if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
	     VM_EventLogger.logNetWriteBegin(fd);

       for (;;)
          {
          int rc = VM_FileSystem.writeBytes(fd, data, offset, count);

          if (rc == count)
             { // ok
             if (trace) VM_Scheduler.trace("VM_SocketImpl.sendStreamImpl",
					   "write END", fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		    VM_EventLogger.logNetWriteEnd(fd);
             if (VM.BuildForNetworkMonitoring)
		    VM_Thread.getCurrentThread().netWrites += 1;
             return rc;
             }

	  if (rc > 0 && rc < count) {
	      offset += rc;
	      count -= rc;
	      VM_Thread.ioWaitWrite(fd);
	      continue;
	  }

          if (rc == -1 || rc == 0)
             { // operation would have blocked
             if (trace) VM_Scheduler.trace("VM_SocketImpl.sendStreamImpl",
					  "write would have blocked RETRY",
					  fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		     VM_EventLogger.logNetWriteRetry(fd);
	     VM_Thread.ioWaitWrite(fd);
             continue;
             }
	  
	  if (rc == -3) // EPIPE error
	    {
	     throw new SocketException("Broken pipe");
	     }

          throw new IOException(); // !!TODO: what additional details should be supplied with exception?
          }

}  // end method sendStreamImpl


}


