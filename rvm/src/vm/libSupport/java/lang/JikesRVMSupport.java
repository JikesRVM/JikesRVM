/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.security.ProtectionDomain;

import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 */
public class JikesRVMSupport {

  public static Class createClass(VM_Type type) {
    return Class.create(type);
  }

  public static VM_Type getTypeForClass(Class c) {
    return c.type;
  }

  public static void setClassProtectionDomain(Class c, ProtectionDomain pd) {
    c.pd = pd;
  }

  public static char[] getBackingCharArray(String str) throws VM_PragmaUninterruptible {
    return str.value;
  }

  public static int getStringLength(String str) throws VM_PragmaUninterruptible {
    return str.count;
  }

  public static int getStringOffset(String str) throws VM_PragmaUninterruptible {
    return str.offset;
  }
}
