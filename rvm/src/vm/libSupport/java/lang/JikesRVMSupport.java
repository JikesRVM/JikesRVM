/*
 * (C) Copyright IBM Corp 2002, 2004
 */
//$Id$
package java.lang;

import java.security.ProtectionDomain;

import com.ibm.JikesRVM.classloader.VM_Type;

import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM;              // for VerifyAssertions and _assert()
import com.ibm.JikesRVM.VM_Thread;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 */
public class JikesRVMSupport {

  public static Class createClass(VM_Type type) {
    //-#if RVM_WITH_OWN_JAVA_LANG_CLASS
    return Class.create(type);
    //-#else
    // return new Class((Object) VMClass.create(type));
    return createClass(type, null);
    //-#endif
  }

  public static Class createClass(VM_Type type, ProtectionDomain pd) {
    //-#if RVM_WITH_OWN_JAVA_LANG_CLASS
    Class c = Class.create(type);
    setClassProtectionDomain(c, pd);
    return c;
    //-#else
      //-#if RVM_WITH_CLASSPATH_0_11_OR_LATER
      return new Class((Object) VMClass.create(type), pd);
      //-#else
      /* Classpath 0.10 doesn't seem to have any way to actually set the
         ProtectionDomain.  Ugly. */
        //-#if RVM_WITH_CLASSPATH_0_10_OR_LATER
        return new Class((Object) VMClass.create(type));
        //-#else
        // The constructor in 0.08 takes a VMClass, not an Object.
        return new Class(VMClass.create(type));
        //-#endif
      //-#endif
    //-#endif
  }

  public static VM_Type getTypeForClass(Class c) {
  //-#if RVM_WITH_OWN_JAVA_LANG_CLASS
    return c.type;
  //-#else
    //-#if RVM_WITH_CLASSPATH_0_10_OR_LATER
    VMClass vc = (VMClass) c.vmdata;
    //-#else
    VMClass vc = (VMClass) c.vmClass;
    //-#endif
    return vc.type;
  //-#endif
  }

  //-#if RVM_WITH_OWN_JAVA_LANG_CLASS
  /** XXX This isn't needed under GNU Classpath --- we properly want to
      create this at the time that we create the Class structure.  That's
      because the ProtectionDomain associated with a class is a final field in
      GNU Classpath 0.11's implementation.  */
  public static void setClassProtectionDomain(Class c, ProtectionDomain pd) {
    c.pd = pd;
  }
  //-#endif
  

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
