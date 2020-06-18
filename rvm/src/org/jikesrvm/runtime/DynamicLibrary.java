/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import java.util.Iterator;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Interface to the dynamic libraries of our underlying operating system.
 */
public final class DynamicLibrary {

  /**
   * Currently loaded dynamic libraries.
   */
  private static final ImmutableEntryHashMapRVM<String, DynamicLibrary> dynamicLibraries =
      new ImmutableEntryHashMapRVM<String, DynamicLibrary>();

  /**
   * Add symbol for the bootloader to find symbols within it.
   */
  public static void boot() {
    System.loadLibrary("jvm_jni");
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
   * Address of JNI_OnLoad method
   */
  private final Address jniOnLoad;

  /**
   * Address of JNI_OnUnLoad
   */
  private final Address jniOnUnload;

  /**
   * Maintain a loaded library, call it's JNI_OnLoad function if present
   * @param libName library name
   * @param libHandler handle of loaded library
   */
  private DynamicLibrary(String libName, Address libHandler) {
    this.libName = libName;
    this.libHandler = libHandler;
    jniOnLoad = getJNI_OnLoad();
    jniOnUnload = getJNI_OnUnload();
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
   * Get the unique JNI_OnLoad symbol associated with this library
   * @return JNI_OnLoad address or zero if not present
   */
  private Address getJNI_OnLoad() {
    Address candidate = getSymbol("JNI_OnLoad");
    Iterator<DynamicLibrary> libs = dynamicLibraries.valueIterator();
    while (libs.hasNext()) {
      DynamicLibrary lib = libs.next();
      if (lib.jniOnLoad.EQ(candidate)) {
        return Address.zero();
      }
    }
    return candidate;
  }

  /**
   * Get the unique JNI_OnUnload symbol associated with this library
   * @return JNI_OnUnload address or zero if not present
   */
  private Address getJNI_OnUnload() {
    Address candidate = getSymbol("JNI_OnUnload");
    Iterator<DynamicLibrary> libs = dynamicLibraries.valueIterator();
    while (libs.hasNext()) {
      DynamicLibrary lib = libs.next();
      if (lib.jniOnUnload.EQ(candidate)) {
        return Address.zero();
      }
    }
    return candidate;
  }

  /**
   * Called after we've successfully loaded the shared library
   */
  private void callOnLoad() {
    // Run any JNI_OnLoad functions defined within the library
    if (!jniOnLoad.isZero()) {
      int version = runJNI_OnLoad(jniOnLoad);
      checkJNIVersion(version);
    }
  }

  /**
   * Method call to run the onload method. Performed as a native
   * method as the JNI_OnLoad method may contain JNI calls and we need
   * the RVMThread of the JNIEnv to be correctly populated (this
   * wouldn't happen with a SysCall)
   *
   * @param JNI_OnLoadAddress address of JNI_OnLoad function
   * @return the JNI version returned by the JNI_OnLoad function
   */
  private static native int runJNI_OnLoad(Address JNI_OnLoadAddress);

  /**
   * Check JNI version is &ge; 1 and &le; 1.4 and if not throw an
   * UnsatisfiedLinkError
   * @param version to check
   */
  private static void checkJNIVersion(int version) {
    int major = version >>> 16;
    int minor = version & 0xFFFF;
    if (major != 1 || minor > 4) {
      throw new UnsatisfiedLinkError("Unsupported JNI version: " + major + "." + minor);
    }
  }

  /**
   * @return the true name of the dynamic library
   */
  public String getLibName() {
    return libName;
  }

  /**
   * look up this dynamic library for a symbol
   * @param symbolName symbol name
   * @return The <code>Address</code> of the symbol system handler
   * (or an address of a PowerPC Linkage triplet).
   *           (-1: not found or couldn't be created)
   */
  public Address getSymbol(String symbolName) {
    // Convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now).
    //
    byte[] asciiName = StringUtilities.stringToBytesNullTerminated(symbolName);
    return SysCall.sysCall.sysDlsym(libHandler, asciiName);
  }

  /**
   * unload a dynamic library
   */
  public void unload() {
    VM.sysWriteln("DynamicLibrary.unload: not implemented yet. libName: " + libName);
  }

  /**
   * Tell the operating system to remove the dynamic library from the
   * system space.
   */
  public void clean() {
    VM.sysWriteln("DynamicLibrary.clean: not implemented yet");
  }

  @Override
  public String toString() {
    return "dynamic library " + libName + ", handler=0x" + Long.toHexString(libHandler.toWord().toLong());
  }

  /**
   * Load a dynamic library
   * @param libName the name of the library to load.
   * @return 0 on failure, 1 on success
   */
  public static synchronized int load(String libName) {
    DynamicLibrary dl = dynamicLibraries.get(libName);
    if (dl != null) {
      return 1; // success: already loaded
    } else {
      // Convert file name from unicode to filesystem character set.
      // (Assume file name is ASCII, for now).
      //
      byte[] asciiName = StringUtilities.stringToBytesNullTerminated(libName);

      // make sure we have enough stack to load the library.
      // This operation has been known to require more than 20K of stack.
      RVMThread myThread = RVMThread.getCurrentThread();
      Offset remaining = Magic.getFramePointer().diff(myThread.stackLimit);
      int stackNeededInBytes = StackFrameLayout.getStackSizeDLOpen() - remaining.toInt();
      if (stackNeededInBytes > 0) {
        if (myThread.hasNativeStackFrame()) {
          throw new java.lang.StackOverflowError("Not enough space to open shared library");
        } else {
          RVMThread.resizeCurrentStack(myThread.getStackLength() + stackNeededInBytes, null);
        }
      }

      Address libHandler = SysCall.sysCall.sysDlopen(asciiName);

      if (!libHandler.isZero()) {
        dynamicLibraries.put(libName, new DynamicLibrary(libName, libHandler));
        return 1;
      } else {
        return 0; // fail; file does not exist
      }
    }
  }

  /**
   * Resolves a symbol to an address in a currently loaded dynamic library.
   * @param symbol the symbol to resolves
   * @return the address of the symbol of Address.zero() if it cannot be resolved
   */
  public static synchronized Address resolveSymbol(String symbol) {
    for (DynamicLibrary lib : dynamicLibraries.values()) {
      Address symbolAddress = lib.getSymbol(symbol);
      if (!symbolAddress.isZero()) {
        return symbolAddress;
      }
    }
    return Address.zero();
  }

  public static synchronized Address getHandleForLibrary(String libName) {
    DynamicLibrary dl = dynamicLibraries.get(libName);
    if (dl != null) {
      return dl.libHandler;
    } else {
      return Address.zero();
    }
  }
}
