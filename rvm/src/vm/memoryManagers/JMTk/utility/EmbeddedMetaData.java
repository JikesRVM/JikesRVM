/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2004
 */
package org.mmtk.utility.alloc;

import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.utility.heap.FreeListVMResource;
import org.mmtk.utility.Memory;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This plan implements constants and access methods for meta data
 * that is embeded in allocation spaces (rather than kept on the
 * side).  The basic idea is that meta data be embeded at a very
 * coarse power of two granularity for fast access, minimal wastage
 * and by making the regions coarse, the contigious meta-data will be
 * relatively large and thus the probability of L1 conflict misses
 * will be reduced (as compared with embedding meta-data at the start
 * of each page which will cause those few cache lines corresponding
 * to the start of each page to be heavily conflicted).
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class EmbeddedMetaData implements Constants, VM_Uninterruptible {

  /* The (log of the) size of each region of meta data management */
  public static final int LOG_BYTES_IN_REGION = 22;
  public static final int BYTES_IN_REGION = 1<<LOG_BYTES_IN_REGION;
  private static final VM_Word REGION_MASK = VM_Word.fromInt(BYTES_IN_REGION - 1);
  public static final int LOG_PAGES_IN_REGION = LOG_BYTES_IN_REGION - LOG_BYTES_IN_PAGE;
  public static final int PAGES_IN_REGION = 1<<LOG_PAGES_IN_REGION;

  /**
   * Given an address, return the begining of the meta data for the
   * region containing the address.  This is a fast operation because
   * it only involves masking out low order bits.
   *
   * @param address The address whose meta data is sought.
   * @return The address of the start of the meta data for the meta
   * region in which the address is located.
   */
  public static final VM_Address getMetaDataBase(VM_Address address) 
    throws VM_PragmaInline {
    return address.toWord().and(REGION_MASK.not()).toAddress();
  }

  /**
   * Given an address, the density (coverage) of a meta data type, and
   * the granularity (alignment) of the meta data, return the offset
   * into the meta data the address.
   *
   * @param address The address whose meta data offset is sought.
   * @param logCoverage The log base two of the coverage of the meta
   * data in question. For example, a value of 4 would indicate a
   * coverage of 16; one metadata byte for every 16 bytes of data.
   * @param logAlign The log base two of the aligment or granularity
   * of the meta-data (it may be arranged in bytes, words, double
   * words etc).
   * @return The offset into the meta-data for this region, given the
   * specified address and coverage and aligment requirements.
   */
  public static final VM_Extent getMetaDataOffset(VM_Address address,
                                                  int logCoverage,
                                                  int logAlign) {
    return address.toWord().and(REGION_MASK).rshl(logCoverage+logAlign).lsh(logAlign).toExtent();
  }
}
