/*
 * (C) Copyright IBM Corp. 2002
 */

package java.net;

/**
 * This class provides a set of static methods
 * implementing privileged functionalty for 
 * JikesRVM without breaking Java Platform Specification
 *
 * @author Sergiy Kyrylkov
 */

public class JikesRVMSupport {

  public static InetAddress createInetAddress(int address) {
    return new InetAddress(address);
  }

  public static InetAddress createInetAddress(int address, String hostname) {
    return new InetAddress(address, hostname);
  }

  public static int getFamily(InetAddress inetaddress) {
    return inetaddress.family;
  }
  
  public static void setHostName(InetAddress inetaddress, String hostname) {
    inetaddress.hostName = hostname;
  }
    
} 
