/*
 * Licensed Materials - Property of IBM,
 * (c) Copyright IBM Corp. 1998, 2001  All Rights Reserved
 */
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.librarySupport.FileSupport;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileDescriptor;

import java.net.*;

/**
 * Sockets using Jikes RVM non-blocking I/O support
 */
final class JikesRVMSocketImpl extends SocketImpl {

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

    private boolean streaming;
    private int receiveTimeout = 0;

    private FileDescriptor acceptInternal(JikesRVMSocketImpl newSocket) 
	throws SocketException, SocketTimeoutException 
    {
	int serverFD = java.io.JikesRVMSupport.getFd(fd);

	// If there is a timeout,
	// compute total wait time (total number of seconds that
	// we are willing to wait before a connection arrives)
	boolean hasTimeout = (receiveTimeout > 0);
	double totalWaitTime = hasTimeout
	    ? ((double) receiveTimeout) / 1000.0
	    : VM_ThreadEventConstants.WAIT_INFINITE;

	int connectionFd;
	double waitStartTime = hasTimeout ? VM_Time.now() : 0.0;

	// Main loop; keep trying to accept a connection until we succeed,
	// the timeout (if any) is reached, or an error occurs.
	for (;;) {
	    // Try to accept a connection
	    VM_ThreadIOQueue.selectInProgressMutex.lock();
	    
	    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	    connectionFd = VM.sysCall2(bootRecord.sysNetSocketAcceptIP,
				   serverFD,
				    VM_Magic.objectAsAddress(newSocket).toInt());
	    VM_ThreadIOQueue.selectInProgressMutex.unlock();

	    if (connectionFd >= 0)
		// Got a connection
		break;

	    switch (connectionFd) {
	    case -1:
		// accept() was interrupted; try again
		continue;
	    case -2: {
		// accept() would have blocked:
		// Wait for the fd to become ready.
		if (VM.VerifyAssertions) VM._assert(!hasTimeout || totalWaitTime >= 0.0);
		VM_ThreadIOWaitData waitData = VM_Wait.ioWaitRead(serverFD, totalWaitTime);
		
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
		
		continue;
	    }

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
  
	java.net.JikesRVMSupport.setHostName(newSocket.getInetAddress(), null);
	
	// Success!
	// Note that the file descriptor creation hook (in VM_FileSystem.java)
	// will take care of setting the socket fd to nonblocking mode.
	return java.io.JikesRVMSupport.createFileDescriptor(connectionFd, false);
    }

    /**
     * Accepts a connection on the provided socket, by calling the IP stack.
     *
     * @param		newImpl	the socket to accept connections on
     * @exception	SocketException	if an error occurs while accepting
     */
    protected void accept(SocketImpl newImpl) throws IOException {
	JikesRVMSocketImpl jksImpl = (JikesRVMSocketImpl) newImpl;

	// jksImpl.fd = java.io.JikesRVMSupport.ref(acceptInternal(jksImpl));
	jksImpl.address = InetAddress.getLocalHost();
	jksImpl.fd = acceptInternal(jksImpl);
	jksImpl.localport = getLocalPortInternal();
    }

    /**
     * Answer the number of bytes that may be read from this
     * socket without blocking.  This call does not block.
     *
     * @return		int		the number of bytes that may be read without blocking
     * @exception	SocketException	if an error occurs while peeking
     */
    protected synchronized int available() throws IOException {
	return VM_FileSystem.bytesAvailable(java.io.JikesRVMSupport.getFd(fd));
    }
    
    private void bindInternal() throws IOException {
	byte[] ip = this.address.getAddress();
	
	int family = java.net.JikesRVMSupport.getFamily(this.address);

	int address;
	address = ip[3] & 0xff;
	address |= ((ip[2] << 8) & 0xff00);
	address |= ((ip[1] << 16) & 0xff0000);
	address |= ((ip[0] << 24) & 0xff000000); 
	
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	int rc = VM.sysCall4(bootRecord.sysNetSocketBindIP,
			     java.io.JikesRVMSupport.getFd(fd),
			     family,
			     address,
			     localport);
	
	if (rc != 0) throw new IOException();
    }

    private int getLocalPortInternal() {
	int localPort;
	
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
        localPort = VM.sysCall1(bootRecord.sysNetSocketPortIP,
				java.io.JikesRVMSupport.getFd(fd));

	return localPort;
	
    }

    /**
     * Binds this socket to the specified local host/port.
     * Binding to the 0 port implies binding to any available port.
     * By not making the assignment to the instVar, the getLocalPort method
     * will lazily go to the stack and query for the assigned port
     *
     * @param		anAddr		the local machine address to bind the socket to
     * @param		aPort		the port on the local machine to bind the socket to
     * @exception	IOException	if an error occurs while binding
     */
    protected void bind(InetAddress anAddr, int aPort) throws IOException {
	this.address = anAddr;

	bindInternal();

	if (0 != aPort) {
		localport = aPort;
	} else {
		localport = getLocalPortInternal();
	};
    }

    /**
     * Close the socket.  Usage thereafter is invalid.
     *
     * @exception	IOException	if an error occurs while closing
     */
    protected void close() throws IOException {
	if (fd != null) 
	    synchronized (fd) {
		FileDescriptor fd = this.fd;
		this.fd = null;
		int ifd = java.io.JikesRVMSupport.getFd(fd);
		VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
		int rc = VM.sysCall1(bootRecord.sysNetSocketCloseIP, ifd);

		/*
		java.io.JikesRVMSupport.deref(fd);
		int ifd = java.io.JikesRVMSupport.getFd(fd);
		if (java.io.JikesRVMSupport.dead(fd)) {
		    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
		    int rc = VM.sysCall1(bootRecord.sysNetSocketCloseIP, ifd);
		} else
		    FileSupport.sync(ifd);
		*/
	    }
    }

    private static final int CLOSE_INPUT = 0;
    private static final int CLOSE_OUTPUT = 1;

    /**
     * Close the input side of the socket.
     * The output side of the socket is unaffected.
     */
    protected synchronized void shutdownInput() throws IOException {
	if (fd == null) throw new IOException("socket already closed");

	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	if (VM.sysCall2(bootRecord.sysNetSocketShutdownIP, java.io.JikesRVMSupport.getFd(fd), CLOSE_INPUT) != 0)
	    throw new IOException("could not close input side of socket");
    }
    
    /**
     * Close the output side of the socket.
     * The input side of the socket is unaffected.
     */
    protected synchronized void shutdownOutput() throws IOException {
	if (fd == null)	throw new IOException("socket already closed");
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	if (VM.sysCall2(bootRecord.sysNetSocketShutdownIP, java.io.JikesRVMSupport.getFd(fd), CLOSE_OUTPUT) != 0)
	    throw new IOException("could not close input side of socket");
    }

    /**
     * Connects this socket to the specified remote host/port.
     * This method assumes the sender has verified the host with
     * the security policy.
     *
     * @param		host		the remote host to connect to
     * @param		port		the remote port to connect to
     * @exception	IOException	if an error occurs while connecting
     */
    protected void connect(String aHost, int aPort) throws IOException {
	InetAddress anAddr = InetAddress.getByName(aHost);
	connect(anAddr, aPort);
    }

    public void connect(SocketAddress address, int timeout) {
	throw new VM_UnimplementedError ("JikesRVMSocketImpl.connect");
    }

    private void connectInternal() throws IOException {
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;

	int fd = java.io.JikesRVMSupport.getFd(this.fd);
       
	int rc = -1;
       
	byte[] ip = this.address.getAddress();
	  
	int family = java.net.JikesRVMSupport.getFamily(this.address);
       
	int address;
	address = ip[3] & 0xff;
	address |= ((ip[2] << 8) & 0xff00);
	address |= ((ip[1] << 16) & 0xff0000);
	address |= ((ip[0] << 24) & 0xff000000); 
	
	while (rc < 0) {
	    VM_ThreadIOQueue.selectInProgressMutex.lock();
	    rc = VM.sysCall4(bootRecord.sysNetSocketConnectIP,
			     fd, 
			     family,
			     address,
			     port);
	    VM_ThreadIOQueue.selectInProgressMutex.unlock();

	    switch (rc) {
	    case 0 : // success
		break;
		
	    case -1 : // operation interrupted by timer tick - retry
		Thread.currentThread().yield();
		break;
             
	    case -2 :  // operation would have blocked
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
    } 

    /**
     * Connects this socket to the specified remote host address/port.
     *
     * @author		OTI
     * @version		initial
     *
     * @param		address		the remote host address to connect to
     * @param		port		the remote port to connect to
     * @exception	IOException	if an error occurs while connecting
     */
    protected void connect(InetAddress anAddr, int aPort) throws IOException {
	this.address = anAddr;
	this.port = aPort;

	if (streaming) connectInternal();
    }
    
    /**
     * Creates a new unconnected socket. If streaming is true,
     * create a stream socket, else a datagram socket.
     * The deprecated datagram usage is not supported and will throw 
     * an exception.
     *
     * @param		streaming	true, if the socket is type streaming
     * @exception	SocketException	if error while creating the socket
     */
    protected void create(boolean streaming) throws SocketException {
	this.streaming = streaming;
	if (streaming) {
    	    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	    int ifd = VM.sysCall1(bootRecord.sysNetSocketCreateIP, 1);

	    if (ifd < 0)
		throw new SocketException(); 
 
	    else
		// Note that the file descriptor creation hook 
		// (in VM_FileSystem.java) will take care of setting 
		// the socket fd to nonblocking mode.
		fd = java.io.JikesRVMSupport.createFileDescriptor(ifd, false);
	}
    }

    /**
     * Listen for connection requests on this stream socket.
     * Incoming connection requests are queued, up to the limit
     * nominated by backlog.  Additional requests are rejected.
     * listen() may only be invoked on stream sockets.
     *
     * @param		backlog		the max number of outstanding connection requests
     * @exception	IOException	thrown if an error occurs while listening
     *
     */
    protected void listen(int backlog) throws java.io.IOException {
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	int rc = VM.sysCall2(bootRecord.sysNetSocketListenIP,
			     java.io.JikesRVMSupport.getFd(fd),
			     backlog);
	
	if (rc == -1)
	    throw new SocketException();
    }
    
    /**
     * In the IP stack, read at most <code>count</code> bytes off the socket into the <code>buffer</code>,
     * at the <code>offset</code>.  If the timeout is zero, block indefinitely waiting
     * for data, otherwise wait the specified period (in milliseconds).
     *
     * @param		fd			the socket descriptor
     * @param		buffer		the buffer to read into
     * @param		offset		the offset into the buffer
     * @param		count		the max number of bytes to read
     * @return		int			the actual number of bytes read
     * @exception	IOException	thrown if an error occurs while reading
     */
    
    int read(byte[] buffer, int offset, int count) throws IOException {
	int ifd = java.io.JikesRVMSupport.getFd(fd);

	double totalWaitTime = (receiveTimeout > 0)
	    ? ((double) receiveTimeout) / 1000.0
	    : VM_ThreadEventConstants.WAIT_INFINITE;

	int rc;
	try {
	    rc = VM_FileSystem.readBytes(ifd, buffer, offset, count, totalWaitTime);
  }
	catch (VM_TimeoutException e) {
	    throw new SocketTimeoutException("socket receive timed out");
	}
	
	return rc;
    }

    int write(byte[] buffer, int offset, int count) throws IOException {
	if (count == 0) return 0;

	int ifd = java.io.JikesRVMSupport.getFd(fd);

	int rc = VM_FileSystem.writeBytes(ifd, buffer, offset, count);

	return rc;
    }

    /**
     * Set the nominated socket option.  Receive timeouts are maintained
     * in Java, rather than in the JNI code.
     *
     * @param		optID		the socket option to set
     * @param		val			the option value
     * @exception	IOException	thrown if an error occurs while setting the option
     */
    public void setOption(int optID, Object val) throws SocketException {
	VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	int ifd = java.io.JikesRVMSupport.getFd(fd);
       
	switch (optID) {
	case SocketOptions.SO_LINGER: {
	    if (val instanceof Integer) {
		// when socket is closed on this end, wait until unsent 
		// data has been received by other end or timeout expires
		//
		int rc = VM.sysCall3(bootRecord.sysNetSocketLingerIP, ifd, 1, ((Integer)val).intValue());
		if (rc == -1) throw new SocketException("SO_LINGER");
	    } else {
		// when socket is closed on this end, discard any unsent data
		//
		int rc = VM.sysCall3(bootRecord.sysNetSocketLingerIP, ifd, 0, 0);
		if (rc == -1) throw new SocketException("SO_LINGER");
	    }
	} break;

	case SocketOptions.SO_KEEPALIVE: {
	    // TODO: implement this.
	    // val will be a java.lang.Boolean.
	    // Having it be a no-op is OK for now.
	} break;

	case SocketOptions.TCP_NODELAY: { 
	    // true:  send data immediately when socket is written to
            // false: delay sending, in order to coalesce packets
	    int rc = VM.sysCall2(bootRecord.sysNetSocketNoDelayIP,
				 ifd,
				 ((Boolean)val).booleanValue() ? 1 : 0);

	    if (rc == -1) throw new SocketException("setTcpNoDelay");
	} break;
         
	case SocketOptions.SO_TIMEOUT: {
	    receiveTimeout = ((Integer)val).intValue();
	} break;

	default:
	    VM._assert(VM.NOT_REACHED);
	}
    }

    /**
     * Answer the nominated socket option.  Receive timeouts are maintained
     * in Java, rather than in the JNI code.
     *
     * @param		optID		the socket option to retrieve
     * @return		Object		the option value
     * @exception	IOException	thrown if an error occurs while accessing the option
     */
    public Object getOption(int optID) throws SocketException {
	if (optID == SocketOptions.SO_TIMEOUT) 
	    return new Integer(receiveTimeout);
	else
	    throw new VM_UnimplementedError("JikesRVMSocketImpl.getOption");
    }

    /**
     * Answer the socket input stream.
     *
     * @return		InputStream	an InputStream on the socket
     * @exception	IOException	thrown if an error occurs while accessing the stream
     */
    protected synchronized InputStream getInputStream() throws IOException {
	return new InputStream() {
		
	    public int available() throws IOException {
		return JikesRVMSocketImpl.this.available();
	    }

	    public void close() throws IOException {
		shutdownInput();
	    }

	    public int read() throws IOException {
		byte[] buffer = new byte[1];
		int result = JikesRVMSocketImpl.this.read(buffer, 0, 1);
		return (-1 == result)? result : buffer[0] & 0xFF;
	    }

	    public int read(byte[] buffer) throws IOException {
		return JikesRVMSocketImpl.this.read(buffer, 0, buffer.length);
	    }

	    public int read(byte[] buf, int off, int len) throws IOException {
		return JikesRVMSocketImpl.this.read(buf, off, len);
	    }

	};
    }

    /**
     * Answer the socket output stream.
     *
     * @return		OutputStream	an OutputStream on the socket
     * @exception	IOException	thrown if an error occurs while accessing the stream
     */
    protected synchronized OutputStream getOutputStream() throws IOException {
	return new OutputStream() {

	    public void write (int b) throws IOException {
		byte[] buffer = new byte[]{ (byte)b };
		JikesRVMSocketImpl.this.write(buffer, 0, 1);
	    }

	    public void write (byte[] b) throws IOException {
		JikesRVMSocketImpl.this.write(b, 0, b.length);
	    }

	    public void write (byte[] b, int off, int len) throws IOException {
		JikesRVMSocketImpl.this.write(b, off, len);
	    }

	    public void flush () throws IOException {
		FileSupport.sync(java.io.JikesRVMSupport.getFd(fd));
	    }

	    public void close () throws IOException {
		shutdownOutput();
	    }

	};
    }	   

    protected void finalize() throws IOException {
	close();
    }

    public void sendUrgentData(int data) {
	throw new VM_UnimplementedError("JikesRVMSocketImpl.sendUrgentData");
    }
}
