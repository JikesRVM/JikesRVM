/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.security.ProtectionDomain;

import com.ibm.JikesRVM.classloader.VM_Type;

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
}
