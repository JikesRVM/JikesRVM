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
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class EmbeddedMetaData implements Constants, VM_Uninterruptible {
  public static final int LOG_BYTES_IN_REGION = 22;
  public static final int BYTES_IN_REGION = 1<<LOG_BYTES_IN_REGION;
  private static final VM_Word REGION_MASK = VM_Word.fromInt(BYTES_IN_REGION - 1);
  public static final int LOG_PAGES_IN_REGION = LOG_BYTES_IN_REGION - LOG_BYTES_IN_PAGE;
  public static final int PAGES_IN_REGION = 1<<LOG_PAGES_IN_REGION;

  public static final VM_Address getMetaDataBase(VM_Address address) 
    throws VM_PragmaInline {
    return address.toWord().and(REGION_MASK.not()).toAddress();
  }

  public static final VM_Extent getMetaDataOffset(VM_Address address,
                                                  int logCoverage,
                                                  int logAlign) {
    return address.toWord().and(REGION_MASK).rshl(logCoverage+logAlign).lsh(logAlign).toExtent();
  }
}
