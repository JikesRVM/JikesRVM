/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.net.*;

public class VM_InetAddress {

  static {
    System.loadLibrary("jpninet");
  }

    private static final int MAX_HOSTNAME = 256;

  private static final boolean trace = false;

/**
 * Query the IP stack for aliases for the host.
 * The host is in dotted IP string form.
 *
 * @author		Maria
 * @version		initial
 *
 * @param		addr		the host name to lookup
 * @exception	UnknownHostException	if an error occurs during lookup
 */

public static InetAddress[] getAliasesByAddrImpl(String addr)
	throws UnknownHostException {
	throw new VM_UnimplementedError("RVM code for InetAddressJpn.getAliasesByAddrImpl");
	//// I do not believe this is reachable
	//return null;
}



/**
 * Query the IP stack for aliases for the host.
 * The host is in string name form.
 *
 * @author		Derek
 * @version		initial
 *
 * @param		name		the host name to lookup
 * @exception	UnknownHostException	if an error occurs during lookup
 */

public native static InetAddress[] getAliasesByNameImpl(String hostName)
  throws UnknownHostException;

/**
 * Query the IP stack for the host address.
 * The host is in address form.
 * (Get network name of machine at specifed internet address).
 *
 * @author		Derek
 * @version		initial
 *
 * @param		addr		the host address to lookup
 * @exception	UnknownHostException	if an error occurs during lookup
 */

    
public native static InetAddress getHostByAddrImpl(int addr)
  throws UnknownHostException;
    
/**
 * Convert a string containing an Ipv4 Internet Protocol
 * dotted address into a binary address.  Note, the special
 * case of '255.255.255.255' throws an exception, so this
 * value should not be used as an argument.
 * See also inetAddr(String).
 */
public static int inetAddrImpl(String host) throws UnknownHostException {

	int returnAddress;

	try {
	  int beginOctet = 0;
	  int endOctet = host.indexOf('.', beginOctet);
	  String octet = host.substring(beginOctet, endOctet);
	  returnAddress = (Short.parseShort(octet)) << 24;

	  beginOctet = endOctet + 1;
	  endOctet = host.indexOf('.', beginOctet);
	  octet = host.substring(beginOctet, endOctet);
	  returnAddress += (Short.parseShort(octet)) << 16;

	  beginOctet = endOctet + 1;
	  endOctet = host.indexOf('.', beginOctet);
	  octet = host.substring(beginOctet, endOctet);
	  returnAddress += (Short.parseShort(octet)) << 8;

	  beginOctet = endOctet + 1;
	  endOctet = host.length();
	  octet = host.substring(beginOctet, endOctet);
	  returnAddress += (Short.parseShort(octet));
	}
	catch (Throwable t) {
	  throw new UnknownHostException();
	}

	if (trace) {
	  System.out.println("inetAddrImpl(" +
			      host+
			      ") is " + returnAddress);
	}

	return returnAddress;

}  // end method inetAddrImpl

/**
 * Convert a binary address into a string containing
 * an Ipv4 Internet Protocol dotted address.
 */
public static String inetNtoaImpl(int hipAddr) {
	int octet1 = (hipAddr & 0xFF000000) >>> 24;
	int octet2 = (hipAddr & 0x00FF0000) >> 16;
	int octet3 = (hipAddr & 0x0000FF00) >> 8;
	int octet4 = hipAddr & 0x000000ff;

	return new String (octet1 + "." +
			   octet2 + "." +
			   octet3 + "." +
			   octet4);
}

/**
 * Query the IP stack for the host address.
 * The host is in string name form.
 *
 * @author		OTI
 * @version		initial
 *
 * @param		name		the host name to lookup
 * @return		InetAddress	the host address
 * @exception	UnknownHostException	if an error occurs during lookup
 */

public static InetAddress getHostByNameImpl(String name)
	throws UnknownHostException {
	return getAliasesByNameImpl(name)[0];
}  // emd method getHostByNameImpl


/**
 * Query the IP stack for the host machine name (network name of the machine
 *	we are running on)..
 *
 * @author		Derek
 * @version		initial
 *
 * @return		String		the host machine name
 */

    
public native static String getHostNameImpl() throws UnknownHostException;
    
}
