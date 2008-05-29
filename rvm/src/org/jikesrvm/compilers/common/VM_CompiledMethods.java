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
package org.jikesrvm.compilers.common;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeMap;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.jni.VM_JNICompiledMethod;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * Manage pool of compiled methods. <p>
 * Original extracted from VM_ClassLoader. <p>
 */
public class VM_CompiledMethods implements VM_SizeConstants {
  /**
   * 2^LOG_ROW_SIZE is the number of elements per row
   */
  private static final int LOG_ROW_SIZE = 10;
  /**
   * Mask to ascertain row from id number
   */
  private static final int ROW_MASK = (1 << LOG_ROW_SIZE)-1;
  /**
   * Java methods that have been compiled into machine code.
   * Note that there may be more than one compiled versions of the same method
   * (ie. at different levels of optimization).
   */
  private static VM_CompiledMethod[][] compiledMethods = new VM_CompiledMethod[16][1 << LOG_ROW_SIZE];

  /**
   * Index of most recently allocated slot in compiledMethods[].
   */
  private static int currentCompiledMethodId = 0;

  /** {@see snipObsoleteMethods} */
  private static boolean scanForObsoleteMethods = false;

  /**
   * Ensure space in backing array for id
   */
  private static void ensureCapacity(int id) {
    int column = id >> LOG_ROW_SIZE;
    if (column >= compiledMethods.length) {
      VM_CompiledMethod[][] tmp = new VM_CompiledMethod[column+1][];
      for (int i=0; i < column; i++) {
        tmp[i] = compiledMethods[i];
      }
      tmp[column] = new VM_CompiledMethod[1 << LOG_ROW_SIZE];
      compiledMethods = tmp;
      VM_Magic.sync();
    }
  }

  /**
   * Fetch a previously compiled method without checking
   */
  @Uninterruptible
  public static VM_CompiledMethod getCompiledMethodUnchecked(int cmid) {
    int column = cmid >> LOG_ROW_SIZE;
    return compiledMethods[column][cmid & ROW_MASK];
  }

  /**
   * Set entry in compiled method lookup
   */
  private static void setCompiledMethod(int cmid, VM_CompiledMethod cm) {
    int column = cmid >> LOG_ROW_SIZE;
    compiledMethods[column][cmid & ROW_MASK] = cm;
  }

  /**
   * Fetch a previously compiled method.
   */
  @Uninterruptible
  public static VM_CompiledMethod getCompiledMethod(int compiledMethodId) {
    VM_Magic.isync();  // see potential update from other procs

    if (VM.VerifyAssertions) {
      if (!(0 < compiledMethodId && compiledMethodId <= currentCompiledMethodId)) {
        VM.sysWriteln("WARNING: attempt to get compiled method #", compiledMethodId);
        return null;
      }
    }

    return getCompiledMethodUnchecked(compiledMethodId);
  }

  /**
   * Create a VM_CompiledMethod appropriate for the given compilerType
   */
  public static synchronized VM_CompiledMethod createCompiledMethod(VM_Method m, int compilerType) {
    int id = currentCompiledMethodId + 1;
    ensureCapacity(id);
    currentCompiledMethodId++;
    VM_CompiledMethod cm = null;
    if (compilerType == VM_CompiledMethod.BASELINE) {
      cm = new VM_BaselineCompiledMethod(id, m);
    } else if (VM.BuildForOptCompiler && compilerType == VM_CompiledMethod.OPT) {
      cm = new OptCompiledMethod(id, m);
    } else if (compilerType == VM_CompiledMethod.JNI) {
      cm = new VM_JNICompiledMethod(id, m);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected compiler type!");
    }
    setCompiledMethod(id, cm);
    return cm;
  }

  /**
   * Create a VM_CompiledMethod for the synthetic hardware trap frame
   */
  public static synchronized VM_CompiledMethod createHardwareTrapCompiledMethod() {
    int id = currentCompiledMethodId + 1;
    ensureCapacity(id);
    currentCompiledMethodId++;
    VM_CompiledMethod cm = new VM_HardwareTrapCompiledMethod(id, null);
    setCompiledMethod(id, cm);
    return cm;
  }

  /**
   * Get number of methods compiled so far.
   */
  @Uninterruptible
  public static int numCompiledMethods() {
    return currentCompiledMethodId + 1;
  }

  /**
   * Find the method whose machine code contains the specified instruction.
   *
   * Assumption: caller has disabled gc (otherwise collector could move
   *                objects without fixing up the raw <code>ip</code> pointer)
   *
   * Note: this method is highly inefficient. Normally you should use the
   * following instead:
   *
   * <code>
   * VM_ClassLoader.getCompiledMethod(VM_Magic.getCompiledMethodID(fp))
   * </code>
   *
   * @param ip  instruction address
   *
   * Usage note: <code>ip</code> must point to the instruction *following* the
   * actual instruction whose method is sought. This allows us to properly
   * handle the case where the only address we have to work with is a return
   * address (ie. from a stackframe) or an exception address (ie. from a null
   * pointer dereference, array bounds check, or divide by zero) on a machine
   * architecture with variable length instructions.  In such situations we'd
   * have no idea how far to back up the instruction pointer to point to the
   * "call site" or "exception site".
   *
   * @return method (<code>null</code> --> not found)
   */
  @Uninterruptible
  public static VM_CompiledMethod findMethodForInstruction(Address ip) {
    for (int i = 0, n = numCompiledMethods(); i < n; ++i) {
      VM_CompiledMethod compiledMethod = getCompiledMethodUnchecked(i);
      if (compiledMethod == null || !compiledMethod.isCompiled()) {
        continue; // empty slot
      }

      if (compiledMethod.containsReturnAddress(ip)) {
        return compiledMethod;
      }
    }

    return null;
  }

  // We keep track of compiled methods that become obsolete because they have
  // been replaced by another version. These are candidates for GC. But, they
  // can only be collected once we are certain that they are no longer being
  // executed. Here, we keep track of them until we know they are no longer
  // in use.
  public static void setCompiledMethodObsolete(VM_CompiledMethod compiledMethod) {
    // Currently, we avoid setting methods of java.lang.Object obsolete.
    // This is because the TIBs for arrays point to the original version
    // and are not updated on recompilation.
    // !!TODO: When replacing a java.lang.Object method, find arrays in JTOC
    //  and update TIB to use newly recompiled method.
    if (compiledMethod.getMethod().getDeclaringClass().isJavaLangObjectType()) {
      return;
    }

    compiledMethod.setObsolete();
    VM_Magic.sync();
    scanForObsoleteMethods = true;
  }

  /**
   * Snip reference to CompiledMethod so that we can reclaim code space. If
   * the code is currently being executed, stack scanning is responsible for
   * marking it NOT obsolete. Keep such reference until a future GC.
   * NOTE: It's expected that this is processed during GC, after scanning
   *    stacks to determine which methods are currently executing.
   */
  public static void snipObsoleteCompiledMethods() {
    VM_Magic.isync();
    if (!scanForObsoleteMethods) return;
    scanForObsoleteMethods = false;
    VM_Magic.sync();

    int max = numCompiledMethods();
    for (int i = 0; i < max; i++) {
      VM_CompiledMethod cm = getCompiledMethodUnchecked(i);
      if (cm != null) {
        if (cm.isActiveOnStack()) {
          if (cm.isObsolete()) {
            // can't get it this time; force us to look again next GC
            scanForObsoleteMethods = true;
            VM_Magic.sync();
          }
          cm.clearActiveOnStack();
        } else {
          if (cm.isObsolete()) {
            // obsolete and not active on a thread stack: it's garbage!
            setCompiledMethod(i, null);
          }
        }
      }
    }
  }

  /**
   * Report on the space used by compiled code and associated mapping information
   */
  public static void spaceReport() {
    int[] codeCount = new int[VM_CompiledMethod.NUM_COMPILER_TYPES + 1];
    int[] codeBytes = new int[VM_CompiledMethod.NUM_COMPILER_TYPES + 1];
    int[] mapBytes = new int[VM_CompiledMethod.NUM_COMPILER_TYPES + 1];

    VM_Array codeArray = VM_Type.CodeArrayType.asArray();
    for (int i = 0; i < numCompiledMethods(); i++) {
      VM_CompiledMethod cm = getCompiledMethodUnchecked(i);
      if (cm == null || !cm.isCompiled()) continue;
      int ct = cm.getCompilerType();
      codeCount[ct]++;
      int size = codeArray.getInstanceSize(cm.numberOfInstructions());
      codeBytes[ct] += VM_Memory.alignUp(size, BYTES_IN_ADDRESS);
      mapBytes[ct] += cm.size();
    }
    VM.sysWriteln("Compiled code space report\n");

    VM.sysWriteln("  Baseline Compiler");
    VM.sysWriteln("    Number of compiled methods =         " + codeCount[VM_CompiledMethod.BASELINE]);
    VM.sysWriteln("    Total size of code (bytes) =         " + codeBytes[VM_CompiledMethod.BASELINE]);
    VM.sysWriteln("    Total size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.BASELINE]);

    if (codeCount[VM_CompiledMethod.OPT] > 0) {
      VM.sysWriteln("  Optimizing Compiler");
      VM.sysWriteln("    Number of compiled methods =         " + codeCount[VM_CompiledMethod.OPT]);
      VM.sysWriteln("    Total size of code (bytes) =         " + codeBytes[VM_CompiledMethod.OPT]);
      VM.sysWriteln("    Total size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.OPT]);
    }

    if (codeCount[VM_CompiledMethod.JNI] > 0) {
      VM.sysWriteln("  JNI Stub Compiler (Java->C stubs for native methods)");
      VM.sysWriteln("    Number of compiled methods =         " + codeCount[VM_CompiledMethod.JNI]);
      VM.sysWriteln("    Total size of code (bytes) =         " + codeBytes[VM_CompiledMethod.JNI]);
      VM.sysWriteln("    Total size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.JNI]);
    }
    if (!VM.runningVM) {
      TreeMap<String, Integer> packageData = new TreeMap<String, Integer>(
          new Comparator<String>() {
            public int compare(String a, String b) {
              return a.compareTo(b);
            }
          });
      for (int i = 0; i < numCompiledMethods(); ++i) {
        VM_CompiledMethod compiledMethod = getCompiledMethodUnchecked(i);
        if (compiledMethod != null) {
          VM_Method m = compiledMethod.getMethod();
          if (m != null && compiledMethod.isCompiled()) {
            String packageName = m.getDeclaringClass().getPackageName();
            int numInstructions = compiledMethod.numberOfInstructions();
            Integer val = packageData.get(packageName);
            if (val == null) {
              val = numInstructions;
            } else {
              val = val + numInstructions;
            }
            packageData.put(packageName, val);
          }
        }
      }
      VM.sysWriteln("------------------------------------------------------------------------------------------");
      VM.sysWriteln("  Break down of code space usage by package (bytes):");
      VM.sysWriteln("------------------------------------------------------------------------------------------");
      Set<String> keys = packageData.keySet();
      int maxPackageNameSize = 0;
      for (String packageName : keys) {
        maxPackageNameSize = Math.max(maxPackageNameSize, packageName.length());
      }
      maxPackageNameSize++;
      for (String packageName : keys) {
        VM.sysWriteField(maxPackageNameSize, packageName);
        VM.sysWriteField(10, packageData.get(packageName));
        VM.sysWriteln();
      }
    }
  }
}
