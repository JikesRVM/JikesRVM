/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$


package com.ibm.JikesRVM.librarySupport;
import java.net.*;
import java.io.*;
import com.ibm.JikesRVM.VM_SocketImpl;
import com.ibm.JikesRVM.VM_InetAddress;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library network/socket operations.
 *
 * @author Stephen Fink
 */
public class SocketSupport {

  /**
   * Query the IP stack for the local address to which this socket is bound.
   *
   * @param	aFD			the socket descriptor
   * @return    InetAddress	        the local address to which the socket is bound
   */
  public static InetAddress getSocketLocalAddressImpl(FileDescriptor aFD) {
    return VM_SocketImpl.getSocketLocalAddressImpl(aFD);
  }

  /**
   * Query the IP stack for the local port to which this socket is bound.
   * Use this function to implement 
   *
   * @param		aFD		the socket descriptor
   * @return		int		the local port to which the socket is bound
   */
  public static int getSocketLocalPortImpl(FileDescriptor aFD) {
    return VM_SocketImpl.getSocketLocalPortImpl(aFD);
  } 

  /**
   * Query the IP stack for the nominated socket option.  
   * Not that we really support this.
   *
   * @param		aFD		the socket descriptor
   * @param		opt		the socket option type
   * @return		the nominated socket option value
   * @exception	SocketException		thrown if the option is invalid
   */
  public static Object getSocketOptionImpl(FileDescriptor aFD, int opt) throws SocketException {
    return VM_SocketImpl.getSocketOptionImpl(aFD,opt);
  }

  /**
   * Set the nominated socket option in the IP stack.
   *
   * @param		aFD			the socket descriptor
   * @param		opt			the option selector
   * @param		optVal		the nominated option value
   * @exception	SocketException		thrown if the option is invalid or cannot be set
   */
  public static void setSocketOptionImpl(FileDescriptor aFD, int option, Object optVal) throws SocketException {
    VM_SocketImpl.setSocketOptionImpl(aFD,option,optVal);
  }  

  /**
   * Bind the socket to the port/localhost in the IP stack.
   *
   * @param		aFD		the socket descriptor
   * @param		localPort	the option selector
   * @param		localAddress	the nominated option value
   * @exception	SocketException		thrown if bind operation fails
   */
  public static void socketBindImpl(FileDescriptor aFD, int localPort,
                                    InetAddress localAddress) throws SocketException {
    VM_SocketImpl.socketBindImpl(aFD,localPort,localAddress);
  } 

  /**
   * Close the socket in the IP stack.
   *
   * @param		aFD		the socket descriptor
   */
  public static void socketCloseImpl(FileDescriptor aFD) {
    VM_SocketImpl.socketCloseImpl(aFD);
  }  

  /**
   * Close the input side of the given socket file descriptor.
   * The output side is unaffected.
   *
   * @param		aFD		the socket descriptor
   */
  public static void socketCloseInputImpl(FileDescriptor aFD) throws IOException {
    VM_SocketImpl.socketCloseInputImpl(aFD);
  }

  /**
   * Close the output side of the given socket file descriptor.
   * The input side is unaffected.
   *
   * @param		aFD		the socket descriptor
   */
  public static void socketCloseOutputImpl(FileDescriptor aFD) throws IOException {
    VM_SocketImpl.socketCloseOutputImpl(aFD);
  }

  /**
   * Answer the result of attempting to accept a connection request
   * to this stream socket in the IP stack.
   *
   * Wait for connection to appear on this socket.
   *
   * @param		fdServer	the server socket FileDescriptor
   * @param		newSocket	the host socket a connection will be accepted on
   * @param		fdnewSocket	the FileDescriptor for the host socket
   * @param		timeout		the timeout that the server should listen for
   * @exception	SocketException	if an error occurs while accepting connections
   */
  public static FileDescriptor acceptStreamSocketImpl(FileDescriptor fdServer,
                                                      SocketImpl newSocket,
                                                      int timeout)
      throws SocketException, SocketTimeoutException {
    return VM_SocketImpl.acceptStreamSocketImpl(fdServer,newSocket,timeout);
  }  

  /**
   * Answer the number of bytes available to read on the socket
   * without blocking.
   *
   * @param		aFD		the socket FileDescriptor
   * @exception	SocketException	if an error occurs while peeking
   */
  public static int availableStreamImpl(FileDescriptor aFD) throws SocketException {
    return VM_SocketImpl.availableStreamImpl(aFD);
  }

  /**
   * Connect the underlying socket to the nominated remotehost/port.
   *
   * @param		aFD		the socket FileDescriptor
   * @param		remotePort	the remote machine port to connect to
   * @param		remoteAddress	the address of the remote machine to connect to
   * @exception	ConnectException	connection refused at remote host
   * @exception	NoRouteToHostException	connection attempt timed out, or
   *					host unavailable
   * @exception	UnknownHostException	host address unresolved
   * @exception	SocketException		if an error occurs while connecting
   */
  public static void connectStreamSocketImpl(FileDescriptor aFD,
                                             int remotePort,
                                             InetAddress remoteAddress) throws IOException {
    VM_SocketImpl.connectStreamSocketImpl(aFD,remotePort,remoteAddress);
  } 

  /**
   * Answer the result of attempting to create a stream socket in
   * the IP stack.
   *
   * Create a socket, unassociated with any particular address + port.
   * Returned: nothing (fd is set to o/s socket descriptor)
   *
   * @param		aFD		the socket FileDescriptor
   * @exception	SocketException	if an error occurs while creating the socket
   */
  public static FileDescriptor createStreamSocketImpl() throws SocketException {
    return VM_SocketImpl.createStreamSocketImpl();
  }

  /**
   * Answer the result of attempting to listen on a stream socket in
   * the IP stack.
   *
   * Make this socket into a "listener" so we can accept() connections on it.
   *
   * @param		aFD		the socket FileDescriptor
   * @param		backlog	the number of connection requests that may be queued
   *						before requests are rejected
   * @exception	SocketException	if an error occurs while listening
   */
  public static void listenStreamSocketImpl(FileDescriptor aFD, int backlog)
    throws SocketException, SocketTimeoutException {
    VM_SocketImpl.listenStreamSocketImpl(aFD,backlog);
  }  

  /**
   * Recieve at most <code>count</code> bytes into the buffer <code>data</code>
   * at the <code>offset</code> on the socket.
   *
   * @param		aFD		the socket FileDescriptor
   * @param		data	the receive buffer
   * @param		offset	the offset into the buffer
   * @param		count	the max number of bytes to receive
   * @param		timeout	the max time the read operation should block waiting for data
   * @return		int		the actual number of bytes read
   * @exception	SocketException	if an error occurs while reading
   */
  public static int receiveStreamImpl(FileDescriptor aFD,
                                      byte[] data,
                                      int offset,
                                      int count,
                                      int timeout) throws IOException {
    return VM_SocketImpl.receiveStreamImpl(aFD,data,offset,count,timeout);
  }  

  /**
   * Send <code>count</code> bytes from the buffer <code>data</code>
   * at the <code>offset</code>, on the socket.
   *
   * @param		data	the send buffer
   * @param		offset	the offset into the buffer
   * @param		count	the number of bytes to receive
   * @return		int		the actual number of bytes sent
   * @exception	SocketException	if an error occurs while writing
   */
  public static int sendStreamImpl(FileDescriptor ifd,
                                   byte[] data,
                                   int offset,
                                   int count) throws IOException {
    return VM_SocketImpl.sendStreamImpl(ifd,data,offset,count);
  } 

  /**
   * Query the IP stack for aliases for the host.
   * The host is in dotted IP string form.
   *
   * @param		addr		the host name to lookup
   * @exception	UnknownHostException	if an error occurs during lookup
   */
  public static InetAddress[] getAliasesByAddrImpl(String addr) throws UnknownHostException {
    return VM_InetAddress.getAliasesByAddrImpl(addr);
  }

  /**
   * Query the IP stack for aliases for the host.
   * The host is in string name form.
   *
   * @exception	UnknownHostException	if an error occurs during lookup
   */
  public static InetAddress[] getAliasesByNameImpl(String hostName) throws UnknownHostException{
    return VM_InetAddress.getAliasesByNameImpl(hostName);
  }

  /**
   * Query the IP stack for the host address.
   * The host is in address form.
   * (Get network name of machine at specifed internet address).
   *
   * @param		addr		the host address to lookup
   * @exception	UnknownHostException	if an error occurs during lookup
   */
  public static InetAddress getHostByAddrImpl(int addr) throws UnknownHostException {
    return VM_InetAddress.getHostByAddrImpl(addr);
  }

  /**
   * Convert a string containing an Ipv4 Internet Protocol
   * dotted address into a binary address.  Note, the special
   * case of '255.255.255.255' throws an exception, so this
   * value should not be used as an argument.
   * See also inetAddr(String).
   */
  public static int inetAddrImpl(String host) throws UnknownHostException {
    return VM_InetAddress.inetAddrImpl(host);
  }

  /**
   * Query the IP stack for the host address.
   * The host is in string name form.
   *
   * @param		name		the host name to lookup
   * @return		InetAddress	the host address
   * @exception	UnknownHostException	if an error occurs during lookup
   */
  public static InetAddress getHostByNameImpl(String name) throws UnknownHostException {
    return VM_InetAddress.getHostByNameImpl(name);
  }

  /**
   * Query the IP stack for the host machine name (network name of the machine
   *	we are running on)..
   *
   * @return		String		the host machine name
   */
  public static String getHostNameImpl() throws UnknownHostException {
    return VM_InetAddress.getHostNameImpl();
  }

  /**
   * Convert a binary address into a string containing
   * an Ipv4 Internet Protocol dotted address.
   */
  public static String inetNtoaImpl(int hipAddr) {
    return VM_InetAddress.inetNtoaImpl(hipAddr);
  }
}
