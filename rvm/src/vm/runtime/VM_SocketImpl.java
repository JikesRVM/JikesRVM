/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import java.io.IOException;
import java.io.FileDescriptor;
import java.io.FileDescriptor;
import java.net.JikesRVMSupport;
import java.net.*;
import java.net.SocketTimeoutException;
import java.lang.reflect.Field;

public class VM_SocketImpl {

  private static final boolean trace = false;

/**
 * Utility method to check the result of an ioWaitRead()
 * for possible exceptions.
 */
private static void checkIoWaitRead(VM_ThreadIOWaitData waitData)
    throws SocketException, SocketTimeoutException {

  // Did the wait return because it timed out?
  if (waitData.timedOut())
    throw new SocketTimeoutException("socket operation timed out");

  // Is file descriptor actually valid?
  if ((waitData.readFds[0] & VM_ThreadIOConstants.FD_INVALID_BIT) != 0)
    throw new SocketException("invalid socket file descriptor");

}

/**
 * Utility method to check the result of an ioWaitWrite()
 * for possible exceptions.
 */
private static void checkIoWaitWrite(VM_ThreadIOWaitData waitData)
    throws SocketException, SocketTimeoutException {

  // Note that we do not check timeouts for socket writes.

  // Is file descriptor actually valid?
  if ((waitData.writeFds[0] & VM_ThreadIOConstants.FD_INVALID_BIT) != 0)
    throw new SocketException("invalid socket file descriptor");

}

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
    int localaddr = VM_SysCall.call1(bootRecord.sysNetSocketLocalAddressIP, java.io.JikesRVMSupport.getFd(aFD));

    if (localaddr == -1) VM.sysFail("Socket has no local address!");

    return JikesRVMSupport.createInetAddress(localaddr);
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
        localPort = VM_SysCall.call1(bootRecord.sysNetSocketPortIP,
				   java.io.JikesRVMSupport.getFd(aFD));
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
 *
 * <p> Update: other socket options, including SO_KEEPALIVE, were added
 * in JDK 1.3.
 */

public static void setSocketOptionImpl(FileDescriptor aFD, int option, Object optVal)
	throws SocketException {

       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;

       int fd = java.io.JikesRVMSupport.getFd(aFD);
       
       switch (option)
          {
          case SocketOptions.SO_LINGER: {
	  if (optVal instanceof Integer)
	      { // when socket is closed on this end, wait until unsent data has been received
		  // by other end or timeout expires
		  //
		  int rc = VM_SysCall.call3(bootRecord.sysNetSocketLingerIP, fd, 1, ((Integer)optVal).intValue());
		  if (rc == -1) throw new SocketException("SO_LINGER"); // !!TODO: what additional details should be supplied with exception?
	      }
	  else
	      { // when socket is closed on this end, discard any unsent data
		  //
		  int rc = VM_SysCall.call3(bootRecord.sysNetSocketLingerIP, fd, 0, 0);
		  if (rc == -1) throw new SocketException("SO_LINGER"); // !!TODO: what additional details should be supplied with exception?
	      }
	  } break;

	  case SocketOptions.SO_KEEPALIVE:
	  {
		// TODO: implement this.
		// optVal will be a java.lang.Boolean.
		// Having it be a no-op is OK for now.
	  } break;

          case SocketOptions.TCP_NODELAY:
          { // true:  send data immediately when socket is written to
            // false: delay sending, in order to coalesce packets
          int rc = VM_SysCall.call2(bootRecord.sysNetSocketNoDelayIP,
				     fd,
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
		
       byte[] ip = localAddress.getAddress();
	  
       int address;
	  
       address = ip[3] & 0xff;
       address |= ((ip[2] << BITS_IN_BYTE) & 0xff00);
       address |= ((ip[1] << (2*BITS_IN_BYTE)) & 0xff0000);
       address |= ((ip[0] << (3*BITS_IN_BYTE)) & 0xff000000); 
	  
       int family = JikesRVMSupport.getFamily(localAddress);

       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
       int rc = VM_SysCall.call4(bootRecord.sysNetSocketBindIP,
				  java.io.JikesRVMSupport.getFd(aFD),
				  family,
				  address,
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

       int fd = java.io.JikesRVMSupport.getFd(aFD);

       if (trace) VM_Scheduler.trace("VM_SocketImpl.socketCloseImpl",
				     "socketClose", fd);
       VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;

       int rc = VM_SysCall.call1(bootRecord.sysNetSocketCloseIP, fd);
    
}  // end method socketCloseImpl

private static final int CLOSE_INPUT = 0;
private static final int CLOSE_OUTPUT = 1;

/**
 * Close the input side of the given socket file descriptor.
 * The output side is unaffected.
 *
 * @param		aFD		the socket descriptor
 */
public static void socketCloseInputImpl(FileDescriptor aFD) throws IOException {
  VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
  if (VM_SysCall.call2(bootRecord.sysNetSocketShutdownIP, java.io.JikesRVMSupport.getFd(aFD), CLOSE_INPUT) != 0)
    throw new IOException("could not close input side of socket");
}

/**
 * Close the output side of the given socket file descriptor.
 * The input side is unaffected.
 *
 * @param		aFD		the socket descriptor
 */
public static void socketCloseOutputImpl(FileDescriptor aFD) throws IOException {
  VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
  if (VM_SysCall.call2(bootRecord.sysNetSocketShutdownIP, java.io.JikesRVMSupport.getFd(aFD), CLOSE_OUTPUT) != 0)
    throw new IOException("could not close input side of socket");
}

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
 * @param		timeout		the timeout that the server should listen for:
 *                                        0 == infinite
 * @exception	SocketException	if an error occurs while accepting connections
 * @exception   SocketTimeoutException if the accept times out before a connection arrives
 */

public static FileDescriptor acceptStreamSocketImpl(
	FileDescriptor fdServer,
	SocketImpl newSocket,
	int timeout)
		throws SocketException, SocketTimeoutException {

  int fd = java.io.JikesRVMSupport.getFd(fdServer);

  if (trace) VM_Scheduler.trace("VM_SocketImpl.acceptStreamSocketImpl",
				  "socketAccept BEGIN",
				  fd);
  if (VM.BuildForEventLogging && VM.EventLoggingEnabled) 
	VM_EventLogger.logNetAcceptBegin(fd);

  // If there is a timeout,
  // compute total wait time (total number of seconds that
  // we are willing to wait before a connection arrives)
  boolean hasTimeout = (timeout > 0);
  double totalWaitTime = hasTimeout
    ? ((double) timeout) / 1000.0
    : VM_ThreadEventConstants.WAIT_INFINITE;

  int connectionFd;
  double waitStartTime = hasTimeout ? VM_Time.now() : 0.0;

  // Main loop; keep trying to accept a connection until we succeed,
  // the timeout (if any) is reached, or an error occurs.
  for (;;) {
    // Try to accept a connection
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    VM_ThreadIOQueue.selectInProgressMutex.lock();
    connectionFd = VM_SysCall.call_I_I_A(bootRecord.sysNetSocketAcceptIP,
				   fd,
				   VM_Magic.objectAsAddress(newSocket));
    VM_ThreadIOQueue.selectInProgressMutex.unlock();

    if (connectionFd >= 0)
      // Got a connection
      break;

    switch (connectionFd) {
    case -1:
      // accept() was interrupted; try again
      continue;
    case -2:
      {
	// accept() would have blocked:
	// Wait for the fd to become ready.
	if (VM.VerifyAssertions) VM._assert(!hasTimeout || totalWaitTime >= 0.0);
	VM_ThreadIOWaitData waitData = VM_Wait.ioWaitRead(fd, totalWaitTime);

	// Check for exceptions (including timeout)
	checkIoWaitRead(waitData);

	// Update timeout, and make sure it hasn't become negative
	// (which the IO queue treats as infinite).
	if (hasTimeout) {
	  double nextWaitStartTime = VM_Time.now();
	  totalWaitTime -= (nextWaitStartTime - waitStartTime);
	  if (totalWaitTime < 0.0)
	    throw new SocketTimeoutException("socket operation timed out");
	  waitStartTime = nextWaitStartTime;
	}
      }
      continue;
    default:
      // Some kind of error from accept()
      throw new SocketException("accept failed");
    }

  }

  // Note that sysNetSocketAccept fills in the InetAddress
  // in the new socket (family, and integer address); and
  // the port field.  In the unlikely event that someone
  // has already resolved the interger address and cached
  // this value in the object (see InetAddress.getHostName() )
  // reset the host value.
  
  JikesRVMSupport.setHostName(newSocket.getInetAddress(), null);

  if (trace)
    VM_Scheduler.trace("VM_SocketImpl.acceptStreamSocketImpl",
  			       "socketAccept END: new connection",
  			       connectionFd);
  if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
    VM_EventLogger.logNetAcceptEnd(fd, connectionFd);

  // Success!
  // Note that the file descriptor creation hook (in VM_FileSystem.java)
  // will take care of setting the socket fd to nonblocking mode.
  return java.io.JikesRVMSupport.createFileDescriptor(connectionFd, false);
    
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
    return VM_FileSystem.bytesAvailable( java.io.JikesRVMSupport.getFd(aFD) );
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

       int fd = java.io.JikesRVMSupport.getFd(aFD);
       
       if (trace) VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl", 
				     "socketConnect BEGIN", fd);

       if (VM.BuildForEventLogging && VM.EventLoggingEnabled) 
	   VM_EventLogger.logNetConnectBegin(fd);

       int rc = -1;
       
       byte[] ip = remoteAddress.getAddress();
	  
       int address;
	  
       address = ip[3] & 0xff;
       address |= ((ip[2] << BITS_IN_BYTE) & 0xff00);
       address |= ((ip[1] << (2*BITS_IN_BYTE)) & 0xff0000);
       address |= ((ip[0] << (3*BITS_IN_BYTE)) & 0xff000000); 
	  
       int family = JikesRVMSupport.getFamily(remoteAddress);
       
       while (rc < 0) {
	   VM_ThreadIOQueue.selectInProgressMutex.lock();
          rc = VM_SysCall.call4(bootRecord.sysNetSocketConnectIP,
				     fd, 
				     family,
				     address,
				     remotePort);
	  VM_ThreadIOQueue.selectInProgressMutex.unlock();

          switch (rc) {
	  case 0 : // success
             break;
             
          case -1 : // operation interrupted by timer tick - retry
             if (trace)
	          VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl",
		                     "socketConnect interrupted RETRY",
				     fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		 VM_EventLogger.logNetConnectRetry(fd);
             Thread.currentThread().yield();
             break;
             
          case -2 :  // operation would have blocked
             if (trace)
		 VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl",
				    "socketConnect would have blocked RETRY",
				    fd);
             if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
		 VM_EventLogger.logNetConnectRetry(fd);
	     VM_ThreadIOWaitData waitData = VM_Wait.ioWaitWrite(fd);
	     checkIoWaitWrite(waitData);
             break;

	  case -4 : // errno was ECONNREFUSED
	     throw new ConnectException("Connection refused");

	  case -5 : // errno was EHOSTUNREACH
	     throw new NoRouteToHostException();

	  case -3 :
	  default :
          throw new IOException("rc="+rc);
	  }

       }
       
       if (trace) VM_Scheduler.trace("VM_SocketImpl.connectStreamSocketImpl",
				     "socketConnect END ",
				     fd);
       if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
	   VM_EventLogger.logNetConnectEnd(fd);
       
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
    fd = VM_SysCall.call1(bootRecord.sysNetSocketCreateIP, isStream ? 1 : 0);

    if (fd < 0)
	// !!TODO: what additional details should be supplied with exception?
	throw new SocketException(); 
 
    if (trace) 
	VM_Scheduler.trace(
	   "VM_SocketImpl.createStreamSocketImpl", "socketCreate", fd);

    // Note that the file descriptor creation hook (in VM_FileSystem.java)
    // will take care of setting the socket fd to nonblocking mode.
    return java.io.JikesRVMSupport.createFileDescriptor(fd, false);    
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
       int rc = VM_SysCall.call2(bootRecord.sysNetSocketListenIP,
				  java.io.JikesRVMSupport.getFd(aFD), backlog);

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

  int fd = java.io.JikesRVMSupport.getFd(aFD);

  if (trace) VM_Scheduler.trace("VM_SocketImpl.receiveStreamImpl", "read BEGIN", fd);
  if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logNetReadBegin(fd);

  double totalWaitTime = (timeout > 0)
    ? ((double) timeout) / 1000.0
    : VM_ThreadEventConstants.WAIT_INFINITE;

  int rc;
  try {
    rc = VM_FileSystem.readBytes(fd, data, offset, count, totalWaitTime);
  }
  catch (VM_TimeoutException e) {
    throw new SocketTimeoutException("socket receive timed out");
  }

  if (trace) VM_Scheduler.trace("VM_SocketImpl.receiveStreamImpl", "read END", fd);
  if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logNetReadEnd(fd);
  if (VM.BuildForNetworkMonitoring) VM_Thread.getCurrentThread().netReads += 1;

  return rc;

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

       if (count == 0) return 0;

  int fd = java.io.JikesRVMSupport.getFd(ifd);

  if (trace) VM_Scheduler.trace("VM_SocketImpl.sendStreamImpl", "write BEGIN", fd);
  if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logNetWriteBegin(fd);

  int rc = VM_FileSystem.writeBytes(fd, data, offset, count);

  if (trace) VM_Scheduler.trace("VM_SocketImpl.sendStreamImpl", "write END", fd);
  if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logNetWriteEnd(fd);
  if (VM.BuildForNetworkMonitoring) VM_Thread.getCurrentThread().netWrites += 1;

  return rc;

}  // end method sendStreamImpl


}


