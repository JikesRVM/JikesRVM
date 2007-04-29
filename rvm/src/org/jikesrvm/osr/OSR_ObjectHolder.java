/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */

package org.jikesrvm.osr;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * OSR_ObjectHolder helps the specialized prologue to load reference
 * get around of GC problem
 *
 * @author Feng Qian
 */

@Uninterruptible public class OSR_ObjectHolder implements VM_SizeConstants {

  // initialize pool size
  private static final int POOLSIZE = 8;

  private static Object[][] refs; 

  @Interruptible
  public static void boot() { 
    refs = new Object[POOLSIZE][];
    
    // exercise the method to avoid lazy compilation in the future
    Object[] objs = new Object[1];
    int p = handinRefs(objs);
    getRefAt(p,0);
    cleanRefs(p);
        
        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("OSR_ObjectHolder booted...");
        }
  }

  /**
   * The VM scope descriptor extractor can hand in an object here
   */
  @Interruptible
  public static int handinRefs(Object[] objs) {
    int n = refs.length;
    for (int i=0; i<n; i++) {
      if (refs[i] == null) {
        refs[i] = objs;
        return i;
      }
    }
    // grow the array
    Object[][] newRefs = new Object[2*n][];
    System.arraycopy(refs, 0, newRefs, 0, n);
    newRefs[n] = objs;
    refs = newRefs;
        
    return n;
  }

  /**
   * Get the object handed in before, only called by specialized code.
   */
  @Inline
  public static Object getRefAt(int h, int i) {
        
        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("OSR_ObjectHolder getRefAt");
        }
        Object obj = refs[h][i];
    return obj;
  }

  /**
   * Clean objects. This method is called by specialized bytecode prologue
   * Uses magic because it must be uninterruptible
   */
  @Inline
  public static void cleanRefs(int i) {
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("OSR_ObjectHolder cleanRefs");
    }
    VM_Magic.setObjectAtOffset(refs, Offset.fromIntZeroExtend(i<<LOG_BYTES_IN_ADDRESS), null); // refs[i] = null;
  }
}
