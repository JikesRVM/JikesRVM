/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.vm;

import org.mmtk.plan.*;

import com.ibm.JikesRVM.VM_Processor;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 *
 * @version $Revision$
 * @date $Date$
 */
//-#if RVM_WITH_SEMI_SPACE
public class Plan extends SemiSpace implements Uninterruptible {
//-#elif RVM_WITH_MARK_SWEEP
public class Plan extends MarkSweep implements Uninterruptible {
//-#elif RVM_WITH_REF_COUNT
public class Plan extends RefCount implements Uninterruptible {
//-#elif RVM_WITH_GEN_COPY
public class Plan extends GenCopy implements Uninterruptible {
//-#elif RVM_WITH_GEN_MS
public class Plan extends GenMS implements Uninterruptible {
//-#elif RVM_WITH_GEN_RC
public class Plan extends GenRC implements Uninterruptible {
//-#elif RVM_WITH_COPY_MS
public class Plan extends CopyMS implements Uninterruptible {
//-#elif RVM_WITH_NO_GC
public class Plan extends NoGC implements Uninterruptible {
//-#elif RVM_WITH_GCTRACE
public class Plan extends GCTrace implements Uninterruptible {
//-#elif RVM_WITH_SEMI_SPACE_GC_SPY
public class Plan extends SemiSpaceGCSpy implements Uninterruptible {
//-#endif

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * <code>true</code> if built with GCSpy
   */
  public static final boolean WITH_GCSPY =
    //-#if RVM_WITH_GCSPY
    true;
    //-#else
    false;
    //-#endif

  /**
   * Gets the plan instance associated with the current processor.
   *
   * @return the plan instance for the current processor
   */
  public static Plan getInstance() throws InlinePragma {
    //-#if RVM_WITH_JMTK_INLINE_PLAN
    return VM_Processor.getCurrentProcessor();
    //-#else
    return VM_Processor.getCurrentProcessor().mmPlan;
    //-#endif
  }
}
