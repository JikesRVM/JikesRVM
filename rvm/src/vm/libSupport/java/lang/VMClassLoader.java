/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.security.ProtectionDomain;
import java.net.URL;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;

import com.ibm.JikesRVM.classloader.VM_SystemClassLoader;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_Type;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 */
final class VMClassLoader {

  static final Class defineClass(ClassLoader cl, String name, 
                                 byte[] data, int offset, int len,
                                 ProtectionDomain pd) 
    throws ClassFormatError 
  {
    VM_Type vmType = VM_ClassLoader.defineClassInternal(name, data, offset, len, cl);
    return vmType.createClassForType(pd);
  }

  static final Class defineClass(ClassLoader cl, String name,
                                 byte[] data, int offset, int len)
    throws ClassFormatError 
  {
    return defineClass(cl, name, data, offset, len, null);
  }

  static final void resolveClass(Class c) {
    VM_Type cls = JikesRVMSupport.getTypeForClass(c);
    cls.resolve();
    cls.instantiate();
    cls.initialize();
  }

  static final Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
    return VM_SystemClassLoader.getVMClassLoader().loadClass(name, resolve);
  }

  static URL getResource(String name)  {
    return VM_SystemClassLoader.getVMClassLoader().findResource(name);
  }

  static Enumeration getResources(String name) throws IOException {
    return VM_SystemClassLoader.getVMClassLoader().findResources(name);
  }

  static Package getPackage(String name) {
    return null;
  }

  static Package[] getPackages() {
    return new Package[0];
  }

  static final Class getPrimitiveClass(char type) {
    VM_Type t;
    switch (type) {
    case 'Z': 
      t = VM_Type.BooleanType;
      break;
    case 'B':
      t = VM_Type.ByteType;
      break;
    case 'C':
      t = VM_Type.CharType;
      break;
    case 'D':
      t = VM_Type.DoubleType;
      break;
    case 'F':
      t = VM_Type.FloatType;
      break;
    case 'I':
      t = VM_Type.IntType;
      break;
    case 'J':
      t = VM_Type.LongType;
      break;
    case 'S':
      t = VM_Type.ShortType;
      break;
    case 'V':
      t = VM_Type.VoidType;
      break;
    default:
      throw new NoClassDefFoundError("Invalid type specifier: " + type);
    }
    return t.getClassForType();
  }

  static final boolean defaultAssertionStatus() {
    return true;
  }

  static final Map packageAssertionStatus() {
    return null;
  }

  static final Map classAssertionStatus() {
    return null;
  }

  static ClassLoader getSystemClassLoader() {
    return VM_ClassLoader.getApplicationClassLoader();
  }
}
