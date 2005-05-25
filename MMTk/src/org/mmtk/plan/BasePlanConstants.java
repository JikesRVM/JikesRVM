/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * This class contains default value for Plan constants
 * 
 * @author Daniel Frampton 
 * @author Robin Garner 
 * @version $Revision$
 * @date $Date$
 */
public class BasePlanConstants implements Uninterruptible {
  public static boolean NEEDS_WRITE_BARRIER() throws InlinePragma { return false; }
  public static boolean GENERATIONAL_HEAP_GROWTH() throws InlinePragma { return false; }
  public static boolean NEEDS_PUTSTATIC_WRITE_BARRIER() throws InlinePragma { return false; }
  public static boolean NEEDS_TIB_STORE_WRITE_BARRIER() throws InlinePragma { return false; }
  public static boolean NEEDS_LINEAR_SCAN() throws InlinePragma { return false; }
  public static boolean SUPPORTS_PARALLEL_GC() throws InlinePragma { return true; }
  public static boolean MOVES_TIBS() throws InlinePragma { return false; }
  public static boolean MOVES_OBJECTS() throws InlinePragma { return false; }
  public static boolean STEAL_NURSERY_GC_HEADER() throws InlinePragma { return false; }
  public static boolean GENERATE_GC_TRACE() throws InlinePragma { return false; }
  public static boolean WITH_GCSPY() throws InlinePragma { return false; }
  public static boolean COPY_MATURE() throws InlinePragma { return false; }
}
