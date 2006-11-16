/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.net;
import com.ibm.jikesrvm.VM_SizeConstants;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 */
public class JikesRVMSupport implements VM_SizeConstants {

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
      throw new com.ibm.jikesrvm.VM_UnimplementedError("Unknown InetAddress family");
    }
  }
    
  public static void setHostName(InetAddress inetaddress, String hostname) {
    inetaddress.hostName = hostname;
  }
}
