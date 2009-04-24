/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.net;
import org.jikesrvm.SizeConstants;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport implements SizeConstants {

  private static byte[] toArrayForm(int address) {
    byte[] addr = new byte[4];
    addr[0] = (byte)((address>>(3*BITS_IN_BYTE)) & 0xff);
    addr[1] = (byte)((address>>(2*BITS_IN_BYTE)) & 0xff);
    addr[2] = (byte)((address>>BITS_IN_BYTE) & 0xff);
    addr[3] = (byte)(address & 0xff);
    return addr;
  }

  public static InetAddress createInetAddress(int address) {
    return createInetAddress(address, null);
  }

  public static InetAddress createInetAddress(int address, String hostname) {
    return new Inet4Address(toArrayForm(address), hostname);
  }

  public static int getFamily(InetAddress inetaddress) {
    if (inetaddress instanceof Inet4Address) {
      return 2;
    } else if (inetaddress instanceof Inet6Address) {
      return 10;
    } else {
      throw new org.jikesrvm.UnimplementedError("Unknown InetAddress family");
    }
  }

  public static void setHostName(InetAddress inetaddress, String hostname) {
    inetaddress.hostName = hostname;
  }
}
