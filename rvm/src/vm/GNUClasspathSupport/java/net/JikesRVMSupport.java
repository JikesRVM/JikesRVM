/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.net;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
class JikesRVMSupport {

    int[] toArrayForm(int address) {
	int[] addr = new int[4];
	addr[0] = (address>>24) & 0xff;
	addr[1] = (address>>16) & 0xff;
	addr[2] = (address>>8) & 0xff;
	addr[3] = address & 0xff;
	return addr;
    }

    public static InetAddress createInetAddress(int address) {
	return new InetAddress( toArrayForm(address) );
    }
    
    public static InetAddress createInetAddress(int address, String hostname) {
	return new InetAddress(toArrayForm(address), hostname);
    }
    
    public static int getFamily(InetAddress inetaddress) {
	return inetaddress.family;
    }
    
    public static void setHostName(InetAddress inetaddress, String hostname) {
	inetaddress.hostName = hostname;
    }
}
