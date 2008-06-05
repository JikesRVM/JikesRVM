/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants;
import org.jikesrvm.VM;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.scheduler.greenthreads.VM_FileSystem;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.jikesrvm.util.VM_StringUtilities;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Interface to the dynamic libraries of our underlying operating system.
 */
public final class VM_DynamicLibrary {

  /**
   * Currently loaded dynamic libraries.
   */
  private static final ImmutableEntryHashMapRVM<String, VM_DynamicLibrary> dynamicLibraries =
      new ImmutableEntryHashMapRVM<String, VM_DynamicLibrary>();

  /**
   * Add symbol for the boot image runner to find symbols within it.
   */
  public static void boot() {
    System.loadLibrary("rvmdynlib");
  }

  /**
   * The name of the library
   */
  private final String libName;

  /**
   * Value returned from dlopen
   */
  private final Address libHandler;

  /**
   * Load a dynamic library and maintain it in this object.
   * @param libraryName library name
   */
  private VM_DynamicLibrary(String libraryName) {
    // Convert file name from unicode to filesystem character set.
    // (Assume file name is ASCII, for now).
    //
    byte[] asciiName = VM_StringUtilities.stringToBytesNullTerminated(libraryName);

    // make sure we have enough stack to load the library.
    // This operation has been known to require more than 20K of stack.
    VM_Thread myThread = VM_Scheduler.getCurrentThread();
    Offset remaining = VM_Magic.getFramePointer().diff(myThread.stackLimit);
    int stackNeededInBytes = VM_StackframeLayoutConstants.STACK_SIZE_DLOPEN - remaining.toInt();
    if (stackNeededInBytes > 0) {
      if (myThread.hasNativeStackFrame()) {
        throw new java.lang.StackOverflowError("dlopen");
      } else {
        VM_Thread.resizeCurrentStack(myThread.getStackLength() + stackNeededInBytes, null);
      }
    }

    libHandler = VM_SysCall.sysCall.sysDlopen(asciiName);

    if (libHandler.isZero()) {
      VM.sysWriteln("error loading library: " + libraryName);
      throw new UnsatisfiedLinkError();
    }

    libName = libraryName;
    try {
      callOnLoad();
    } catch (UnsatisfiedLinkError e) {
      unload();
      throw e;
    }

    if (VM.verboseJNI) {
      VM.sysWriteln("[Loaded native library: " + libName + "]");
    }
  }

  /**
   * Called after we've successfully loaded the shared library
   */
  private void callOnLoad() {
    // Run any JNI_OnLoad functions defined within the library
    Address JNI_OnLoadAddress = getSymbol("JNI_OnLoad");
    if (!JNI_OnLoadAddress.isZero()) {
      int version = runJNI_OnLoad(JNI_OnLoadAddress);
      checkJNIVersion(version);
    }
  }

  /**
   * Method call to run the onload method. Performed as a native
   * method as the JNI_OnLoad method may contain JNI calls and we need
   * the VM_Processor of the JNIEnv to be correctly populated (this
   * wouldn't happen with a VM_SysCall)
   *
   * @param JNI_OnLoadAddress address of JNI_OnLoad function
   * @return the JNI version returned by the JNI_OnLoad function
   */
  private static native int runJNI_OnLoad(Address JNI_OnLoadAddress);

  /**
   * Check JNI version is &le; 1.4 and if not throw an
   * UnsatisfiedLinkError
   * @param version to check
   */
  private static void checkJNIVersion(int version) {
    int major = version >>> 16;
    int minor = version & 0xFFFF;
    if (major > 1 || minor > 4) {
      throw new UnsatisfiedLinkError("Unsupported JNI version: " + major + "." + minor);
    }
  }

  /**
   * @return the true name of the dynamic library
   */
  public String getLibName() { return libName; }

  /**
   * look up this dynamic library for a symbol
   * @param symbolName symbol name
   * @return The <code>Address</code> of the symbol system handler
   * (actually an address to an AixLinkage triplet).
   *           (-1: not found or couldn't be created)
   */
  public Address getSymbol(String symbolName) {
    // Convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now).
    //
    byte[] asciiName = VM_StringUtilities.stringToBytesNullTerminated(symbolName);
    return VM_SysCall.sysCall.sysDlsym(libHandler, asciiName);
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
    return "dynamic library " + libName + ", handler=0x" + Long.toHexString(libHandler.toWord().toLong());
  }

  /**
   * Load a dynamic library
   * @param libname the name of the library to load.
   * @return 0 on failure, 1 on success
   */
  public static synchronized int load(String libname) {
    VM_DynamicLibrary dl = dynamicLibraries.get(libname);
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
   * @return the address of the symbol of Address.zero() if it cannot be resolved
   */
  public static synchronized Address resolveSymbol(String symbol) {
    for (VM_DynamicLibrary lib : dynamicLibraries.values()) {
      Address symbolAddress = lib.getSymbol(symbol);
      if (!symbolAddress.isZero()) {
        return symbolAddress;
      }
    }
    return Address.zero();
  }
}
