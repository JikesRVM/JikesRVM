/*
 * (C) Copyright IBM Corp 2002
 */
// $Id$
package com.ibm.JikesRVM;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.*;

/**
 * Sockets using Jikes RVM non-blocking I/O support
 *
 * @author Julian Dolby
 */
final class JikesRVMSocketImpl extends SocketImpl implements VM_SizeConstants {

  //
  // BEGIN API from SocketImpl class
  //

  /**
   * Creates a new unconnected socket. If streaming is true,
   * create a stream socket, else a datagram socket.
   * The deprecated datagram usage is not supported and will throw 
   * an exception.
   *
   * @param             streaming       true, if the socket is type streaming
   * @exception SocketException if error while creating the socket
   */
  protected synchronized void create(boolean streaming) throws SocketException {
    this.streaming = streaming;
    if (streaming) {
      int ifd = VM_SysCall.sysNetSocketCreate(1);

      if (ifd < 0) {
        throw new SocketException(); 
      } else {
        // Note that the file descriptor creation hook 
        // (in VM_FileSystem.java) will take care of setting 
        // the socket fd to nonblocking mode.
        VM_FileSystem.onCreateFileDescriptor(ifd, false);
        native_fd = ifd;
      }
    } else {
      throw new VM_UnimplementedError("non-streaming sockets");
    }
  }

  /**
   * Connects to the remote hostname and port specified as arguments.
   *
   * @param hostname The remote hostname to connect to
   * @param port The remote port to connect to
   *
   * @exception IOException If an error occurs
   */
  protected synchronized void connect(String remoteHost, int remotePort) 
    throws IOException {
    if (VM.VerifyAssertions) VM._assert(streaming);
    InetAddress remoteAddr = InetAddress.getByName(remoteHost);
    connectInternal(remoteAddr, remotePort, 0);
  }
    
  /**
   * Connects this socket to the specified remote host address/port.
   *
   * @author            OTI
   * @version           initial
   *
   * @param             address         the remote host address to connect to
   * @param             port            the remote port to connect to
   * @exception IOException     if an error occurs while connecting
   */
  protected synchronized void connect(InetAddress remoteAddr, int remotePort)
    throws IOException {
    if (VM.VerifyAssertions) VM._assert( streaming );
    connectInternal(remoteAddr, remotePort, 0);
  }
    
  public synchronized void connect(SocketAddress iaddress, int timeout) 
    throws IOException {
    InetSocketAddress address = (InetSocketAddress)iaddress;
    InetAddress remoteAddress = address.getAddress();
    int remotePort = address.getPort();
    connectInternal(remoteAddress, remotePort, timeout);
  }

  protected synchronized void bind(InetAddress localAddr, int localPort) throws IOException {
    byte[] ip = localAddr.getAddress();
    int family = java.net.JikesRVMSupport.getFamily(localAddr);
    int address;
    address = ip[3] & 0xff;
    address |= ((ip[2] << BITS_IN_BYTE) & 0xff00);
    address |= ((ip[1] << (2*BITS_IN_BYTE)) & 0xff0000);
    address |= ((ip[0] << (3*BITS_IN_BYTE)) & 0xff000000); 
        
    int rc = VM_SysCall.sysNetSocketBind(native_fd, family, address, localPort);
    if (rc != 0) throw new IOException();

    this.localAddress = localAddr;
  }

  /**
   * Listen for connection requests on this stream socket.
   * Incoming connection requests are queued, up to the limit
   * nominated by backlog.  Additional requests are rejected.
   * listen() may only be invoked on stream sockets.
   *
   * @param             backlog         the max number of outstanding connection requests
   * @exception IOException     thrown if an error occurs while listening
   *
   */
  protected synchronized void listen(int backlog) throws java.io.IOException {
    int rc = VM_SysCall.sysNetSocketListen(native_fd, backlog);
    if (rc == -1)
      throw new SocketException();
  }
    
  /**
   * Accepts a connection on the provided socket, by calling the IP stack.
   *
   * @param             newImpl the socket to accept connections on
   * @exception SocketException if an error occurs while accepting
   */
  protected synchronized void accept(SocketImpl newImpl) throws IOException {
    JikesRVMSocketImpl newSocket = (JikesRVMSocketImpl)newImpl;

    // this will be filled in by sys.C code
    newSocket.address = java.net.JikesRVMSupport.createInetAddress(0);

    // If there is a timeout,
    // compute total wait time (total number of seconds that
    // we are willing to wait before a connection arrives)
    boolean hasTimeout = (receiveTimeout > 0);
    double totalWaitTime = hasTimeout
      ? ((double) receiveTimeout) / 1000.0
      : VM_ThreadEventConstants.WAIT_INFINITE;

    int connectionFd;
    double waitStartTime = hasTimeout ? now() : 0.0;

    // Main loop; keep trying to accept a connection until we succeed,
    // the timeout (if any) is reached, or an error occurs.
    while (true) {
      // Try to accept a connection
      VM_ThreadIOQueue.selectInProgressMutex.lock();
            
      connectionFd = VM_SysCall.sysNetSocketAccept(native_fd, newSocket);
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
        if (VM.VerifyAssertions)
          VM._assert(!hasTimeout || totalWaitTime >= 0.0);

        VM_ThreadIOWaitData waitData = 
          VM_Wait.ioWaitRead(native_fd, totalWaitTime);
                
        // Check for exceptions (including timeout)
        checkIoWaitRead(waitData);
                
        // Update timeout, and make sure it hasn't become negative
        // (which the IO queue treats as infinite).
        if (hasTimeout) {
          double nextWaitStartTime = now();
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
    // has already resolved the integer address and cached
    // this value in the object (see InetAddress.getHostName() )
    // reset the host value.
    java.net.JikesRVMSupport.setHostName(newSocket.getInetAddress(), null);
        
    // Success!
    // Note that the file descriptor creation hook (in VM_FileSystem.java)
    // will take care of setting the socket fd to nonblocking mode.
    VM_FileSystem.onCreateFileDescriptor(connectionFd, false);

    // put accepted connection into given SocketImpl.
    newSocket.native_fd = connectionFd;
  }

  // TODO: Think about getting rid of this function and switching
  //       this whole layer over to cycles instead.
  private static double now() {
    return ((double)VM_Time.currentTimeMicros())/100000;
  }

  /**
   * Answer the socket input stream.
   *
   * @return            InputStream     an InputStream on the socket
   * @exception IOException     thrown if an error occurs while accessing the stream
   */
  protected synchronized InputStream getInputStream() throws IOException {
    return new InputStream() {
        private boolean closed = false;

        public int available() throws IOException {
          if (closed) throw new IOException("stream closed");
          return JikesRVMSocketImpl.this.available();
        }

        public void close() throws IOException {
          closed = true;
        }

        public int read() throws IOException {
          if (closed) throw new IOException("stream closed");
          byte[] buffer = new byte[1];
          int result = JikesRVMSocketImpl.this.read(buffer, 0, 1);
          return (-1 == result)? result : ((int)buffer[0])&0xFF;
        }

        public int read(byte[] buffer) throws IOException {
          if (closed) throw new IOException("stream closed");
          return JikesRVMSocketImpl.this.read(buffer, 0, buffer.length);
        }

        public int read(byte[] buf, int off, int len) throws IOException {
          if (closed) throw new IOException("stream closed");
          return JikesRVMSocketImpl.this.read(buf, off, len);
        }

      };
  }

  /**
   * Answer the socket output stream.
   *
   * @return            OutputStream    an OutputStream on the socket
   * @exception IOException     thrown if an error occurs while accessing the stream
   */
  protected synchronized OutputStream getOutputStream() throws IOException {
    return new OutputStream() {
        private boolean closed = false;

        public void write (int b) throws IOException {
          if (closed) throw new IOException("stream closed");
          byte[] buffer = new byte[]{ (byte)b };
          JikesRVMSocketImpl.this.write(buffer, 0, 1);
        }

        public void write (byte[] b) throws IOException {
          if (closed) throw new IOException("stream closed");
          JikesRVMSocketImpl.this.write(b, 0, b.length);
        }

        public void write (byte[] b, int off, int len) throws IOException {
          if (closed) throw new IOException("stream closed");
          JikesRVMSocketImpl.this.write(b, off, len);
        }

        public void flush () throws IOException {
          if (closed) throw new IOException("stream closed");
          VM_FileSystem.sync( native_fd );
        }

        public void close () throws IOException {
          closed = true;
        }

      };
  }        

  /**
   * Answer the number of bytes that may be read from this
   * socket without blocking.  This call does not block.
   *
   * @return            int             the number of bytes that may be read without blocking
   * @exception SocketException if an error occurs while peeking
   */
  protected synchronized int available() throws IOException {
    return VM_FileSystem.bytesAvailable(native_fd);
  }
    
  /**
   * Close the socket.  Usage thereafter is invalid.
   *
   * @exception IOException     if an error occurs while closing
   */
  protected synchronized void close() throws IOException {
    if (native_fd != -1) {
      int close_fd = native_fd;
      this.native_fd = -1;
      int rc = VM_SysCall.sysNetSocketClose(close_fd);
    }
  }

  /**
   * Close the input side of the socket.
   * The output side of the socket is unaffected.
   */
  protected synchronized void shutdownInput() throws IOException {
    if (native_fd == -1) throw new IOException("socket already closed");

    if (VM_SysCall.sysNetSocketShutdown(native_fd, CLOSE_INPUT) != 0)
      throw new IOException("could not close input side of socket");
  }
    
  /**
   * Close the output side of the socket.
   * The input side of the socket is unaffected.
   */
  protected synchronized void shutdownOutput() throws IOException {
    if (native_fd == -1) throw new IOException("socket already closed");
    if (VM_SysCall.sysNetSocketShutdown(native_fd, CLOSE_OUTPUT) != 0)
      throw new IOException("could not close input side of socket");
  }

  protected FileDescriptor getFileDescriptor() {
    throw new InternalError("no FDs for sockets");
  }

  protected boolean supportsUrgentData() {
    return false;
  }

  public void sendUrgentData(int data) {
    throw new VM_UnimplementedError("JikesRVMSocketImpl.sendUrgentData");
  }

  /**
   * Return the local port used by this socket impl.
   */
  public int getLocalPort() {
    getLocalPortInternal();  // someone has asked, so we'd better find out.
    return localport;
  }

  //
  // BEGIN API from SocketOption interface
  //

  /**
   * Set the nominated socket option.  Receive timeouts are maintained
   * in Java, rather than in the JNI code.
   *
   * @param             optID           the socket option to set
   * @param             val                     the option value
   * @exception SocketException thrown if an error occurs while setting the option
   */
  public synchronized void setOption(int optID, Object val) throws SocketException {
    switch (optID) {
    case SocketOptions.SO_LINGER: {
      if (val instanceof Integer) {
        // when socket is closed on this end, wait until unsent 
        // data has been received by other end or timeout expires
        //
        int rc = VM_SysCall.sysNetSocketLinger(native_fd, 1, ((Integer)val).intValue());
        if (rc == -1) throw new SocketException("SO_LINGER");
      } else {
        // when socket is closed on this end, discard any unsent data
        //
        int rc = VM_SysCall.sysNetSocketLinger(native_fd, 0, 0);
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
      int rc = VM_SysCall.sysNetSocketNoDelay(native_fd,
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
   * @param             optID           the socket option to retrieve
   * @return            Object          the option value
   * @exception SocketException thrown if an error occurs while accessing the option
   */
  public synchronized Object getOption(int optID) throws SocketException {
    if (optID == SocketOptions.SO_TIMEOUT) {
      return new Integer(receiveTimeout);
    } else if (optID == SocketOptions.SO_BINDADDR) {
      return localAddress;
    } else if (optID == SocketOptions.SO_SNDBUF) {
      return new Integer(VM_SysCall.sysNetSocketSndBuf(native_fd));
    } else {
      throw new VM_UnimplementedError("JikesRVMSocketImpl.getOption: " + optID);
    }
  }

  //
  // BEGIN private state and helper methods
  //

  private static final int CLOSE_INPUT = 0;
  private static final int CLOSE_OUTPUT = 1;

  private InetAddress localAddress = null;
  private boolean streaming = true;
  private int receiveTimeout = -1;
  private int native_fd = -1;

  /**
   * Initialize socket impl with invalid values; will really
   * be initialized bv .create, .bind, etc.
   */
  JikesRVMSocketImpl() {
    address = null;
    port = -1;
    localport = -1;
  }

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
        
    // Did the wait return because it timed out?
    if (waitData.timedOut())
      throw new SocketTimeoutException("socket operation timed out");
        
    // Is file descriptor actually valid?
    if ((waitData.writeFds[0] & VM_ThreadIOConstants.FD_INVALID_BIT) != 0)
      throw new SocketException("invalid socket file descriptor");
        
  }

  /**
   * We do not store the local port by default, so this method makes sure
   * that we have it before we try to return it.
   */
  private int getLocalPortInternal() {
    localport = VM_SysCall.sysNetSocketPort(native_fd);
    return localport;
  }

  /**
   * Connects this socket to the specified remote host/port.
   * This method assumes the sender has verified the host with
   * the security policy.
   *
   * @param             host            the remote host to connect to
   * @param             port            the remote port to connect to
   * @param           timeout         a timeout in milliseconds
   * @exception IOException     if an error occurs while connecting
   */
  private void connectInternal(InetAddress remoteAddr, int remotePort, 
                               int timeout) throws IOException {
    int rc = -1;

    double totalWaitTime = (timeout > 0)
      ? ((double) timeout) / 1000.0
      : VM_ThreadEventConstants.WAIT_INFINITE;

    byte[] ip = remoteAddr.getAddress();
        
    int family = java.net.JikesRVMSupport.getFamily(remoteAddr);
       
    int address;
    address = ip[3] & 0xff;
    address |= ((ip[2] << BITS_IN_BYTE) & 0xff00);
    address |= ((ip[1] << (2*BITS_IN_BYTE)) & 0xff0000);
    address |= ((ip[0] << (3*BITS_IN_BYTE)) & 0xff000000); 
        
    while (rc < 0) {
      VM_ThreadIOQueue.selectInProgressMutex.lock();
      rc = VM_SysCall.sysNetSocketConnect(native_fd, 
                                          family,
                                          address,
                                          remotePort);
      VM_ThreadIOQueue.selectInProgressMutex.unlock();

      switch (rc) {
      case 0 : // success
        this.address = remoteAddr;
        this.port = remotePort;
        break;
                
      case -1 : // operation interrupted by timer tick - retry
        Thread.currentThread().yield();
        break;
             
      case -2 :  // operation would have blocked
        VM_ThreadIOWaitData waitData = 
          VM_Wait.ioWaitWrite(native_fd, totalWaitTime);

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
   * In the IP stack, read at most <code>count</code> bytes off the socket 
   * into the <code>buffer</code>, at the <code>offset</code>.  
   * If the timeout is zero, block indefinitely waiting
   * for data, otherwise wait the specified period (in milliseconds).
   *
   * @param             buffer          the buffer to read into
   * @param             offset          the offset into the buffer
   * @param             count           the max number of bytes to read
   * @return            int             the actual number of bytes read
   * @exception IOException     thrown if an error occurs while reading
   */
  synchronized int read(byte[] buffer, int offset, int count) throws IOException {
    if (count == 0) return 0;

    double totalWaitTime = (receiveTimeout > 0)
      ? ((double) receiveTimeout) / 1000.0
      : VM_ThreadEventConstants.WAIT_INFINITE;
        
    int rc;
    try {
      rc = VM_FileSystem.readBytes(native_fd, buffer, offset, count, totalWaitTime);
    } catch (VM_TimeoutException e) {
      throw new SocketTimeoutException("socket receive timed out");
    }
        
    return (rc == 0) ? -1 : rc;
  }

  synchronized int write(byte[] buffer, int offset, int count) throws IOException {
    if (count == 0) return 0;
    return VM_FileSystem.writeBytes(native_fd, buffer, offset, count);
  }

  protected void finalize() throws IOException {
    close();
  }

  /**
   * Set up socket factories to use JikesRVMSocketImpl
   */
  static void boot() {
    try {
      Socket.setSocketImplFactory(new SocketImplFactory() {
          public SocketImpl createSocketImpl() { return new JikesRVMSocketImpl(); }
        });
      ServerSocket.setSocketFactory(new SocketImplFactory() {
          public SocketImpl createSocketImpl() { return new JikesRVMSocketImpl(); }
        });
      DatagramSocket.setDatagramSocketImplFactory(new DatagramSocketImplFactory() {
          public DatagramSocketImpl createDatagramSocketImpl() { 
            throw new VM_UnimplementedError ("Need to implement JikesRVMDatagramSocketImpl");
          }});
    } catch (java.io.IOException e) {
      VM.sysFail("trouble setting socket impl factories");
    }
  }

}
