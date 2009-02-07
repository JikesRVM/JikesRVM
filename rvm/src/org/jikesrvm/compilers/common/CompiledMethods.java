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
import org.jikesrvm.Services;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.jni.JNICompiledMethod;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * Manage pool of compiled methods. <p>
 * Original extracted from RVMClassLoader. <p>
 */
public class CompiledMethods implements SizeConstants {
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
  private static CompiledMethod[][] compiledMethods = new CompiledMethod[16][1 << LOG_ROW_SIZE];

  /**
   * Index of most recently allocated slot in compiledMethods[].
   */
  private static int currentCompiledMethodId = 0;

  /**
   * Used to communicate between {@link #setCompiledMethodObsolete}
   * and {@link #snipObsoleteCompiledMethods}
   */
  private static boolean scanForObsoleteMethods = false;

  /**
   * Ensure space in backing array for id
   */
  private static void ensureCapacity(int id) {
    int column = id >> LOG_ROW_SIZE;
    if (column >= compiledMethods.length) {
      CompiledMethod[][] tmp = new CompiledMethod[column+1][];
      for (int i=0; i < column; i++) {
        tmp[i] = compiledMethods[i];
      }
      tmp[column] = new CompiledMethod[1 << LOG_ROW_SIZE];
      compiledMethods = tmp;
      Magic.sync();
    }
  }

  /**
   * Fetch a previously compiled method without checking
   */
  @Uninterruptible
  public static CompiledMethod getCompiledMethodUnchecked(int cmid) {
    int column = cmid >> LOG_ROW_SIZE;
    return compiledMethods[column][cmid & ROW_MASK];
  }

  /**
   * Set entry in compiled method lookup
   */
  @Uninterruptible
  private static void setCompiledMethod(int cmid, CompiledMethod cm) {
    int column = cmid >> LOG_ROW_SIZE;
    CompiledMethod[] col = compiledMethods[column];
    Services.setArrayUninterruptible(col, cmid & ROW_MASK, cm);
  }

  /**
   * Fetch a previously compiled method.
   */
  @Uninterruptible
  public static CompiledMethod getCompiledMethod(int compiledMethodId) {
    Magic.isync();  // see potential update from other procs

    if (VM.VerifyAssertions) {
      if (!(0 < compiledMethodId && compiledMethodId <= currentCompiledMethodId)) {
        VM.sysWriteln("WARNING: attempt to get compiled method #", compiledMethodId);
        VM.sysFail("attempt to get an invalid compiled method ID");
        return null;
      }
    }

    return getCompiledMethodUnchecked(compiledMethodId);
  }

  /**
   * Create a CompiledMethod appropriate for the given compilerType
   */
  public static synchronized CompiledMethod createCompiledMethod(RVMMethod m, int compilerType) {
    int id = currentCompiledMethodId + 1;
    ensureCapacity(id);
    currentCompiledMethodId++;
    CompiledMethod cm = null;
    if (compilerType == CompiledMethod.BASELINE) {
      cm = new BaselineCompiledMethod(id, m);
    } else if (VM.BuildForOptCompiler && compilerType == CompiledMethod.OPT) {
      cm = new OptCompiledMethod(id, m);
    } else if (compilerType == CompiledMethod.JNI) {
      cm = new JNICompiledMethod(id, m);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected compiler type!");
    }
    setCompiledMethod(id, cm);
    return cm;
  }

  /**
   * Create a CompiledMethod for the synthetic hardware trap frame
   */
  public static synchronized CompiledMethod createHardwareTrapCompiledMethod() {
    int id = currentCompiledMethodId + 1;
    ensureCapacity(id);
    currentCompiledMethodId++;
    CompiledMethod cm = new HardwareTrapCompiledMethod(id, null);
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
   * RVMClassLoader.getCompiledMethod(Magic.getCompiledMethodID(fp))
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
  public static CompiledMethod findMethodForInstruction(Address ip) {
    for (int i = 0, n = numCompiledMethods(); i < n; ++i) {
      CompiledMethod compiledMethod = getCompiledMethodUnchecked(i);
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
  public static void setCompiledMethodObsolete(CompiledMethod compiledMethod) {
    // Currently, we avoid setting methods of java.lang.Object obsolete.
    // This is because the TIBs for arrays point to the original version
    // and are not updated on recompilation.
    // !!TODO: When replacing a java.lang.Object method, find arrays in JTOC
    //  and update TIB to use newly recompiled method.
    if (compiledMethod.getMethod().getDeclaringClass().isJavaLangObjectType()) {
      return;
    }

    compiledMethod.setObsolete();
    Magic.sync();
    scanForObsoleteMethods = true;
  }

  /**
   * Snip reference to CompiledMethod so that we can reclaim code space. If
   * the code is currently being executed, stack scanning is responsible for
   * marking it NOT obsolete. Keep such reference until a future GC.
   * NOTE: It's expected that this is processed during GC, after scanning
   *    stacks to determine which methods are currently executing.
   */
  @Uninterruptible
  public static void snipObsoleteCompiledMethods() {
    Magic.isync();
    if (!scanForObsoleteMethods) return;
    scanForObsoleteMethods = false;
    Magic.sync();

    int max = numCompiledMethods();
    for (int i = 0; i < max; i++) {
      CompiledMethod cm = getCompiledMethodUnchecked(i);
      if (cm != null) {
        if (cm.isActiveOnStack()) {
          if (cm.isObsolete()) {
            // can't get it this time; force us to look again next GC
            scanForObsoleteMethods = true;
            Magic.sync();
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
    int[] codeCount = new int[CompiledMethod.NUM_COMPILER_TYPES + 1];
    int[] codeBytes = new int[CompiledMethod.NUM_COMPILER_TYPES + 1];
    int[] mapBytes = new int[CompiledMethod.NUM_COMPILER_TYPES + 1];

    RVMArray codeArray = RVMType.CodeArrayType.asArray();
    for (int i = 0; i < numCompiledMethods(); i++) {
      CompiledMethod cm = getCompiledMethodUnchecked(i);
      if (cm == null || !cm.isCompiled()) continue;
      int ct = cm.getCompilerType();
      codeCount[ct]++;
      int size = codeArray.getInstanceSize(cm.numberOfInstructions());
      codeBytes[ct] += Memory.alignUp(size, BYTES_IN_ADDRESS);
      mapBytes[ct] += cm.size();
    }
    VM.sysWriteln("Compiled code space report\n");

    VM.sysWriteln("  Baseline Compiler");
    VM.sysWriteln("    Number of compiled methods =         " + codeCount[CompiledMethod.BASELINE]);
    VM.sysWriteln("    Total size of code (bytes) =         " + codeBytes[CompiledMethod.BASELINE]);
    VM.sysWriteln("    Total size of mapping data (bytes) = " + mapBytes[CompiledMethod.BASELINE]);

    if (codeCount[CompiledMethod.OPT] > 0) {
      VM.sysWriteln("  Optimizing Compiler");
      VM.sysWriteln("    Number of compiled methods =         " + codeCount[CompiledMethod.OPT]);
      VM.sysWriteln("    Total size of code (bytes) =         " + codeBytes[CompiledMethod.OPT]);
      VM.sysWriteln("    Total size of mapping data (bytes) = " + mapBytes[CompiledMethod.OPT]);
    }

    if (codeCount[CompiledMethod.JNI] > 0) {
      VM.sysWriteln("  JNI Stub Compiler (Java->C stubs for native methods)");
      VM.sysWriteln("    Number of compiled methods =         " + codeCount[CompiledMethod.JNI]);
      VM.sysWriteln("    Total size of code (bytes) =         " + codeBytes[CompiledMethod.JNI]);
      VM.sysWriteln("    Total size of mapping data (bytes) = " + mapBytes[CompiledMethod.JNI]);
    }
    if (!VM.runningVM) {
      TreeMap<String, Integer> packageData = new TreeMap<String, Integer>(
          new Comparator<String>() {
            public int compare(String a, String b) {
              return a.compareTo(b);
            }
          });
      for (int i = 0; i < numCompiledMethods(); ++i) {
        CompiledMethod compiledMethod = getCompiledMethodUnchecked(i);
        if (compiledMethod != null) {
          RVMMethod m = compiledMethod.getMethod();
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
