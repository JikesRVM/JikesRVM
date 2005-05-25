/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * No GC Constants 
 *
 * @author Daniel Frampton
 * @author Robin Garner 
 * @version $Revision$
 * @date $Date$
 */
public class NoGCConstants extends StopTheWorldGCConstants implements Uninterruptible {

  public static int GC_HEADER_BITS_REQUIRED() throws InlinePragma { 
    return 0;
  }

  public static int GC_HEADER_WORDS_REQUIRED() throws InlinePragma { 
    return 0;
  }
}
