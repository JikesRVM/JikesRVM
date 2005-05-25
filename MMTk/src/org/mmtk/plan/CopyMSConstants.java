/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.*;

/**
 * Constants for CopyMS.

 * @author Daniel Frampton 
 * @author Robin Garner 
 * @version $Revision$
 * @date $Date$
 */
public class CopyMSConstants extends StopTheWorldGCConstants implements Uninterruptible {

  public static boolean MOVES_OBJECTS() throws InlinePragma { 
    return true; 
  }

  public static int GC_HEADER_BITS_REQUIRED() throws InlinePragma {  
    return CopySpace.LOCAL_GC_BITS_REQUIRED;
  }

  public static int GC_HEADER_WORDS_REQUIRED() throws InlinePragma { 
    return CopySpace.GC_HEADER_WORDS_REQUIRED;
  }

}
