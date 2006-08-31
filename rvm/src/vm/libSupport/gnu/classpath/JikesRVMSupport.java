/*
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
//$Id: JikesRVMSupport.java,v 1.16 2006/03/01 12:23:56 dgrove-oss Exp $
package gnu.classpath;

import org.vmmagic.unboxed.Address;

/**
 * Library support interface of Jikes RVM
 *
 * @author Elias Naur
 */
public class JikesRVMSupport {
  public static Address getAddressFromPointer(Pointer pointer) {
    //-#if RVM_FOR_32_ADDR
    return Address.fromIntSignExtend(((Pointer32)pointer).data);
    //-#elif RVM_FOR_64_ADDR
    return Address.fromLong(((Pointer64)pointer).data);
    //-#endif
  }

  public static Pointer getPointerFromAddress(Address address) {
    //-#if RVM_FOR_32_ADDR
    return new Pointer32(address.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Pointer64(address.toLong());
    //-#endif
  }
}
