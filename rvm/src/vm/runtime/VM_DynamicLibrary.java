/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Interface to dynamic libraries of underlying operating system.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_DynamicLibrary implements VM_SizeConstants{
  private String libName;
  private int libHandler;

  /**
   * load a dynamic library and maintain it in this object
   * @param libraryName library name
   * @return library system handler (-1: not found or couldn't be created)
   */ 
  public VM_DynamicLibrary(String libraryName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] asciiName = new byte[libraryName.length() + 1]; // +1 for null terminator
    libraryName.getBytes(0, libraryName.length(), asciiName, 0);

    // make sure we have enough stack to load the library.  
    // This operation has been known to require more than 20K of stack.
    VM_Thread myThread = VM_Thread.getCurrentThread();
    int stackNeededInBytes =  VM_StackframeLayoutConstants.STACK_SIZE_DLOPEN -
      (VM_Magic.getFramePointer().diff(myThread.stackLimit)).toInt();
    if (stackNeededInBytes > 0 ) {
      if (myThread.hasNativeStackFrame()) {
        throw new java.lang.StackOverflowError("dlopen");
      } else {
        VM_Thread.resizeCurrentStack(myThread.stack.length + (stackNeededInBytes >> LOG_BYTES_IN_ADDRESS),
                                     null); 
      }
    }

    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    libHandler = VM_SysCall.call_I_A(bootRecord.sysDlopenIP, 
                             VM_Magic.objectAsAddress(asciiName));

    if (libHandler==0) {
      VM.sysWrite("error loading library: " + libraryName);
      VM.sysWrite("\n");
      throw new UnsatisfiedLinkError();
    }

    libName = new String(libraryName);

    if (VM.verboseJNI) {
      VM.sysWriteln("[Loaded native library: "+libName+"]");
    }

    // initialize the JNI environment if not already done
    VM_JNIEnvironment.boot();
  }



  /**
   * look up this dynamic library for a symbol
   * @param symbolName symbol name
   * @return symbol system handler 
   * (actually an address to an AixLinkage triplet)
   *           (-1: not found or couldn't be created)
   */ 
  public VM_Address getSymbol(String symbolName) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    //
    byte[] asciiName = new byte[symbolName.length() + 1]; // +1 for null terminator
    symbolName.getBytes(0, symbolName.length(), asciiName, 0);
    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    return VM_SysCall.call_A_I_A(bootRecord.sysDlsymIP, libHandler, 
							VM_Magic.objectAsAddress(asciiName));
  }

  /**
   * unload a dynamic library
   * should destroy this object, maybe this should be in the finalizer?
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
}
