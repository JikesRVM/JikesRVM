/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Interface to the dynamic libraries of our underlying operating system.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_DynamicLibrary {

  /**
   * Currently loaded dynamic libraries.
   */
  private static HashMap dynamicLibraries = new HashMap();

  /**
   * The name of the library
   */
  private String libName;

  /**
   * Value returned from dlopen
   */
  private VM_Address libHandler;

  /**
   * Load a dynamic library and maintain it in this object.
   * @param libraryName library name
   */ 
  private VM_DynamicLibrary(String libraryName) {
    // Convert file name from unicode to filesystem character set.
    // (Assume file name is ASCII, for now).
    //
    byte[] asciiName = new byte[libraryName.length() + 1]; // +1 for null terminator
    libraryName.getBytes(0, libraryName.length(), asciiName, 0);

    // make sure we have enough stack to load the library.  
    // This operation has been known to require more than 20K of stack.
    VM_Thread myThread = VM_Thread.getCurrentThread();
    VM_Offset remaining = VM_Magic.getFramePointer().diff(myThread.stackLimit);
    int stackNeededInBytes = VM_StackframeLayoutConstants.STACK_SIZE_DLOPEN - remaining.toInt();
    if (stackNeededInBytes > 0) {
      if (myThread.hasNativeStackFrame()) {
        throw new java.lang.StackOverflowError("dlopen");
      } else {
        VM_Thread.resizeCurrentStack(myThread.stack.length + stackNeededInBytes, null); 
      }
    }

    libHandler = VM_SysCall.sysDlopen(asciiName);

    if (libHandler.isZero()) {
      VM.sysWriteln("error loading library: " + libraryName);
      throw new UnsatisfiedLinkError();
    }

    libName = new String(libraryName);

    if (VM.verboseJNI) {
      VM.sysWriteln("[Loaded native library: "+libName+"]");
    }
  }

  /**
   * @return the true name of the dynamic library
   */
  public String getLibName() { return libName; }
  
  /**
   * look up this dynamic library for a symbol
   * @param symbolName symbol name
   * @return The <code>VM_Address</code> of the symbol system handler
   * (actually an address to an AixLinkage triplet).
   *           (-1: not found or couldn't be created)
   */ 
  public VM_Address getSymbol(String symbolName) {
    // Convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now).
    //
    byte[] asciiName = new byte[symbolName.length() + 1]; // +1 for null terminator
    symbolName.getBytes(0, symbolName.length(), asciiName, 0);
    return VM_SysCall.sysDlsym(libHandler, asciiName);
  }

  /**
   * unload a dynamic library
   */
  public void unload() {
    VM.sysWrite("VM_DynamicLibrary.unload: not implemented yet \n");
  }

  /**
   * Tell the operating system to remove the dynamic library from the 
   * system space.
   */
  public void clean() {
    VM.sysWrite("VM_DynamicLibrary.clean: not implemented yet \n");
  }

  public String toString() {
    return "dynamic library " + libName + ", handler=" + libHandler;
  }

  /**
   * Load a dynamic library
   * @param libname the name of the library to load.
   * @return 0 on failure, 1 on success
   */
  public static synchronized int load(String libname) {
    VM_DynamicLibrary dl = (VM_DynamicLibrary)dynamicLibraries.get(libname);
    if (dl != null) return 1; // success: already loaded
    
    if (VM_FileSystem.stat(libname, VM_FileSystem.STAT_EXISTS) == 1) {
      dynamicLibraries.put(libname, new VM_DynamicLibrary(libname));
      return 1;
    } else {
      return 0; // fail; file does not exist
    }
  }

  /**
   * Resolve a symbol to an address in a currently loaded dynamic library.
   * @return the address of the symbol of VM_Address.zero() if it cannot be resolved
   */
  public static synchronized VM_Address resolveSymbol(String symbol) {
    for (Iterator i = dynamicLibraries.values().iterator(); i.hasNext();) {
      VM_DynamicLibrary lib = (VM_DynamicLibrary)i.next();
      VM_Address symbolAddress = lib.getSymbol(symbol);
      if (!symbolAddress.isZero()) {
        return symbolAddress;
      }
    }
    return VM_Address.zero();
  }
}
