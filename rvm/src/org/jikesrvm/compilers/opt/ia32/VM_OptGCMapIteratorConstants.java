/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ia32;

/**
 * This interface holds constants for the Opt GC map code specific to IA32
 */
public interface VM_OptGCMapIteratorConstants extends OPT_PhysicalRegisterConstants {

  // NOTE: The following two constants seem to imply that registers 
  //       that can hold references are contiguous.  This is not true,
  //       in general, however, for the GC map code we only need to make
  //       sure that all such registers are included in the range defined
  //       below these contants.

  /*
   * The index of the first nonvolatile register that may hold a reference, 
   */
  int FIRST_GCMAP_REG = 0;

  /*
   * the index of last register that may hold a reference
   */
  int LAST_GCMAP_REG = 7;

}
