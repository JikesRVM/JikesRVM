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

import com.ibm.JikesRVM.librarySupport.ClassLoaderSupport;
import com.ibm.JikesRVM.librarySupport.ReflectionSupport;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_ClassLoader;
import com.ibm.JikesRVM.VM_Atom;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
 */
final class VMClassLoader
{

  static final Class defineClass(ClassLoader cl, String name, byte[] data, int offset, int len) {
      return ClassLoaderSupport.defineClass(cl, name, data, offset, len);
  }

  static final Class defineClass(ClassLoader cl, String name,
                                 byte[] data, int offset, int len,
                                 ProtectionDomain pd)
    throws ClassFormatError
  {
    return ClassLoaderSupport.defineClass(cl, name, data, offset, len, pd);
  }

  static final void resolveClass(Class c) {
      ClassLoaderSupport.resolveClass( null, c );
  }

  static final Class loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
      return VM_SystemClassLoader.getVMClassLoader().loadClass(name, resolve);
  }

  static URL getResource(String name)
  {
    return VM_SystemClassLoader.getVMClassLoader().findResource(name);
  }

  static Enumeration getResources(String name) throws IOException
  {
    return VM_SystemClassLoader.getVMClassLoader().findResources(name);
  }

  static Package getPackage(String name)
  {
    return null;
  }

  static Package[] getPackages()
  {
    return new Package[0];
  }

  static final Class getPrimitiveClass(char type)
  {
    String t;
    switch (type)
      {
      case 'Z': 
        t = "boolean";
        break;
      case 'B':
        t = "byte";
        break;
      case 'C':
        t = "char";
        break;
      case 'D':
        t = "double";
        break;
      case 'F':
        t = "float";
        break;
      case 'I':
        t = "int";
        break;
      case 'J':
        t = "long";
        break;
      case 'S':
        t = "short";
        break;
      case 'V':
        t = "void";
        break;
      default:
        throw new NoClassDefFoundError("Invalid type specifier: " + type);
      }

    VM_Atom name = VM_Atom.findOrCreateAsciiAtom( t );
    VM_Atom desc = VM_Atom.findOrCreateAsciiAtom( String.valueOf( type ) );
    return VM_ClassLoader.findOrCreatePrimitiveType(name, desc).getClassForType();
  }

  static final boolean defaultAssertionStatus()
  {
    return true;
  }

  static final Map packageAssertionStatus()
  {
      return null;
  }

  static final Map classAssertionStatus()
  {
      return null;
  }

  static ClassLoader getSystemClassLoader() {
      return VM_SystemClassLoader.getVMClassLoader();
  }

}
