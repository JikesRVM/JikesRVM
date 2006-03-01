/*
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
//$Id$
package java.lang;

import java.security.ProtectionDomain;

import com.ibm.JikesRVM.classloader.VM_Type;

import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM;              // for VerifyAssertions and _assert()
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Thread;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 */
public class JikesRVMSupport {

  public static Class createClass(VM_Type type) {
    return Class.create(type);
  }

  public static Class createClass(VM_Type type, ProtectionDomain pd) {
    Class c = Class.create(type);
    setClassProtectionDomain(c, pd);
    return c;
  }

  public static VM_Type getTypeForClass(Class c) {
    return c.type;
  }

  public static void setClassProtectionDomain(Class c, ProtectionDomain pd) {
    c.pd = pd;
  }

  /***
   * String stuff
   * */

  public static char[] getBackingCharArray(String str) throws UninterruptiblePragma {
    return str.value;
  }

  public static int getStringLength(String str) throws UninterruptiblePragma {
    return str.count;
  }

  public static int getStringOffset(String str) throws UninterruptiblePragma {
    return str.offset;
  }

  /***
   * Thread stuff
   * */
  public static Thread createThread(VM_Thread vmdata, String myName) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    return new Thread(vmdata, myName);
  }
}
