/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

import org.vmmagic.pragma.*; 
import org.vmmagic.unboxed.*; 

/**
 * Manage pool of compiled methods. <p>
 * Original extracted from VM_ClassLoader. <p>
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 * @author Arvin Shepherd
 */
public class VM_CompiledMethods implements VM_SizeConstants {

  /**
   * Create a VM_CompiledMethod appropriate for the given compilerType
   */
  public synchronized static VM_CompiledMethod createCompiledMethod(VM_Method m, int compilerType) {
    int id = ++currentCompiledMethodId;
    if (id == compiledMethods.length) {
      compiledMethods = growArray(compiledMethods, 2 * compiledMethods.length); 
    }
    VM_CompiledMethod cm = null;
    if (compilerType == VM_CompiledMethod.BASELINE) {
      cm = new VM_BaselineCompiledMethod(id, m);
    } else if (compilerType == VM_CompiledMethod.OPT) {
      //-#if RVM_WITH_OPT_COMPILER
      cm = new VM_OptCompiledMethod(id, m);
      //-#endif
    } else if (compilerType == VM_CompiledMethod.JNI) {
      cm = new VM_JNICompiledMethod(id, m);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected compiler type!");
    }
    compiledMethods[id] = cm;
    return cm;
  }

  /**
   * Create a VM_CompiledMethod for the synthetic hardware trap frame
   */
  static synchronized VM_CompiledMethod createHardwareTrapCompiledMethod() {
    int id = ++currentCompiledMethodId;
    if (id == compiledMethods.length) {
      compiledMethods = growArray(compiledMethods, 2 * compiledMethods.length); 
    }
    VM_CompiledMethod cm = new VM_HardwareTrapCompiledMethod(id, null);
    compiledMethods[id] = cm;
    return cm;
  }


  // Fetch a previously compiled method.
  //
  public static VM_CompiledMethod getCompiledMethod(int compiledMethodId) throws UninterruptiblePragma {
    VM_Magic.isync();  // see potential update from other procs

    if (VM.VerifyAssertions) {
        if (!(0 < compiledMethodId && compiledMethodId <= currentCompiledMethodId)) {
            VM.sysWriteln(compiledMethodId);
            VM._assert(false);
        }
    }

    return compiledMethods[compiledMethodId];
  }

  // Get number of methods compiled so far.
  //
  public static int numCompiledMethods() throws UninterruptiblePragma {
    return currentCompiledMethodId + 1;
  }

  // Getter method for the debugger, interpreter.
  //
  public static VM_CompiledMethod[] getCompiledMethods() throws UninterruptiblePragma {
    return compiledMethods;
  }

  // Getter method for the debugger, interpreter.
  //
  static int numCompiledMethodsLess1() throws UninterruptiblePragma {
    return currentCompiledMethodId;
  }

   // Find method whose machine code contains specified instruction.
   // Taken:      instruction address
   // Returned:   method (null --> not found)
   // Assumption: caller has disabled gc (otherwise collector could move
   //             objects without fixing up raw "ip" pointer)
   //
   // Usage note: "ip" must point to the instruction *following* the actual instruction
   // whose method is sought. This allows us to properly handle the case where
   // the only address we have to work with is a return address (ie. from a stackframe)
   // or an exception address (ie. from a null pointer dereference, array bounds check,
   // or divide by zero) on a machine architecture with variable length instructions.
   // In such situations we'd have no idea how far to back up the instruction pointer
   // to point to the "call site" or "exception site".
   //
   // Note: this method is highly inefficient. Normally you should use the following instead:
   //   VM_ClassLoader.getCompiledMethod(VM_Magic.getCompiledMethodID(fp))
   //
  public static VM_CompiledMethod findMethodForInstruction(Address ip) throws UninterruptiblePragma {
    for (int i = 0, n = numCompiledMethods(); i < n; ++i) {
      VM_CompiledMethod compiledMethod = compiledMethods[i];
      if (compiledMethod == null || !compiledMethod.isCompiled())
        continue; // empty slot

      VM_CodeArray instructions = compiledMethod.getInstructions();
      Address   beg          = VM_Magic.objectAsAddress(instructions);
      Address   end          = beg.add(instructions.length() << VM.LG_INSTRUCTION_WIDTH);

      // note that "ip" points to a return site (not a call site)
      // so the range check here must be "ip <= beg || ip >  end"
      // and not                         "ip <  beg || ip >= end"
      //
      if (ip.LE(beg) || ip.GT(end))
        continue;

      return compiledMethod;
    }

    return null;
  }

  // We keep track of compiled methods that become obsolete because they have
  // been replaced by another version. These are candidates for GC. But, they
  // can only be collected once we are certain that they are no longer being
  // executed. Here, we keep track of them until we know they are no longer
  // in use.
  public static synchronized void setCompiledMethodObsolete(VM_CompiledMethod compiledMethod) {
    if (compiledMethod.isObsolete()) return; // someone else already marked it as obsolete.
    int cmid = compiledMethod.getId();

    // Currently, we avoid setting methods of java.lang.Object obsolete.
    // This is because the TIBs for arrays point to the original version
    // and are not updated on recompilation.
    // !!TODO: When replacing a java.lang.Object method, find arrays in JTOC
    //  and update TIB to use newly recompiled method.
    if (compiledMethod.getMethod().getDeclaringClass().isJavaLangObjectType())
      return;

    if (VM.VerifyAssertions) VM._assert(compiledMethods[cmid] != null);

    if (obsoleteMethods == null) {
      // This should tend not to get too big as it gets compressed as we
      // snip obsolete code at GC time.
      obsoleteMethods = new int[100];
    } else if (obsoleteMethodCount >= obsoleteMethods.length) {
      int newArray[] = new int[obsoleteMethods.length*2];
      // Disable GC during array copy because GC can alter the source array
      VM.disableGC();
      for (int i = 0, n = obsoleteMethods.length; i < n; ++i) {
        newArray[i] = obsoleteMethods[i];
      }
      VM.enableGC();
      obsoleteMethods = newArray;
    }
    compiledMethod.setObsolete(true);
    obsoleteMethods[obsoleteMethodCount++] = cmid;
  }

  // Snip reference to CompiledMethod so that we can reclaim code space. If
  // the code is currently being executed, stack scanning is responsible for
  // marking it NOT obsolete. Keep such reference until a future GC.
  // NOTE: It's expected that this is processed during GC, after scanning
  //    stacks to determine which methods are currently executing.
  public static void snipObsoleteCompiledMethods() {
    if (obsoleteMethods == null) return;
    
    int oldCount = obsoleteMethodCount;
    obsoleteMethodCount = 0;

    for (int i = 0; i < oldCount; i++) {
      int currCM = obsoleteMethods[i];
      if (compiledMethods[currCM].isObsolete()) {
        compiledMethods[currCM] = null;         // break the link
      } else {
        obsoleteMethods[obsoleteMethodCount++] = currCM; // keep it
        compiledMethods[currCM].setObsolete(true);       // maybe next time
      }
    }
  }

  /**
   * Report on the space used by compiled code and associated mapping information
   */
  public static void spaceReport() {
    int[] codeCount = new int[5];
    int[] codeBytes = new int[5];
    int[] mapBytes = new int[5];
    VM_Array codeArray = VM_Type.CodeArrayType.asArray();
    for (int i=0; i<compiledMethods.length; i++) {
      VM_CompiledMethod cm = compiledMethods[i];
      if (cm == null || !cm.isCompiled()) continue;
      int ct = cm.getCompilerType();
      VM_CodeArray code = cm.getInstructions();
      codeCount[ct]++;
      int size = codeArray.getInstanceSize(code.length());
      codeBytes[ct] += VM_Memory.alignUp(size, BYTES_IN_ADDRESS);
      mapBytes[ct] += cm.size();
    }
    VM.sysWriteln("Compiled code space report\n");

    VM.sysWriteln("  Baseline Compiler");
    VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.BASELINE]);
    VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.BASELINE]);
    VM.sysWriteln("\tTotal size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.BASELINE]);

    if (codeCount[VM_CompiledMethod.OPT] > 0) {
      VM.sysWriteln("  Optimizing Compiler");
      VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.OPT]);
      VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.OPT]);
      VM.sysWriteln("\tTotal size of mapping data (bytes) = " +mapBytes[VM_CompiledMethod.OPT]);
    }

    if (codeCount[VM_CompiledMethod.JNI] > 0) {
      VM.sysWriteln("  JNI Stub Compiler (Java->C stubs for native methods)");
      VM.sysWriteln("\tNumber of compiled methods = " + codeCount[VM_CompiledMethod.JNI]);
      VM.sysWriteln("\tTotal size of code (bytes) =         " + codeBytes[VM_CompiledMethod.JNI]);
      VM.sysWriteln("\tTotal size of mapping data (bytes) = " + mapBytes[VM_CompiledMethod.JNI]);
    }
 }


  //----------------//
  // implementation //
  //----------------//

  // Java methods that have been compiled into machine code.
  // Note that there may be more than one compiled versions of the same method
  // (ie. at different levels of optimization).
  //
  private static VM_CompiledMethod[] compiledMethods = new VM_CompiledMethod[16000];

  // Index of most recently allocated slot in compiledMethods[].
  //
  private static int currentCompiledMethodId;

  // See usage above
  private static int[]  obsoleteMethods;
  private static int    obsoleteMethodCount;

  // Expand an array.
  //
  private static VM_CompiledMethod[] growArray(VM_CompiledMethod[] array, 
                                               int newLength) {
    VM_CompiledMethod[] newarray = MM_Interface.newContiguousCompiledMethodArray(newLength);
    System.arraycopy(array, 0, newarray, 0, array.length);
    VM_Magic.sync();
    return newarray;
  }

}
