/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;

/**
 * This interface holds constants for the Opt GC map code specific to IA32
 *
 * @author Michael Hind
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
  static final int FIRST_GCMAP_REG = 0;  

  /*
   * the index of last register that may hold a reference
   */
  static final int LAST_GCMAP_REG = 7;

}
