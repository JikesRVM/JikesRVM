/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */

package org.mmtk.vm;

import com.ibm.JikesRVM.VM_SizeConstants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class VMConstants {
  public static final byte LOG_BYTES_IN_ADDRESS() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_ADDRESS; 
  }
  public static final byte LOG_BYTES_IN_WORD() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_WORD; 
  }
  public static final byte LOG_BYTES_IN_PAGE() throws InlinePragma { 
    return 12; 
  }

  public static final byte LOG_BYTES_IN_PARTICLE() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_INT;
  }
  public static final byte MAXIMUM_ALIGNMENT_SHIFT() throws InlinePragma { 
    return VM_SizeConstants.LOG_BYTES_IN_LONG - VM_SizeConstants.LOG_BYTES_IN_INT; 
  }
  public static final int MAX_BYTES_PADDING() throws InlinePragma { 
    return VM_SizeConstants.BYTES_IN_DOUBLE; 
  }
}

