/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.net;
import com.ibm.JikesRVM.VM_SizeConstants;

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
    //-#if RVM_WITH_CLASSPATH_POST_0_11_CVS_HEAD
    return new InetAddress(toArrayForm(address), hostname);
    //-#else
    return new InetAddress(toArrayForm(address), hostname, null);
    //-#endif
  }
    
  public static int getFamily(InetAddress inetaddress) {
    return inetaddress.family;
  }
    
  public static void setHostName(InetAddress inetaddress, String hostname) {
    inetaddress.hostName = hostname;
  }
}
