/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This object that is invoked when online measurement information must 
 * be collected.
 *
 * @author Peter Sweeney
 * @date   2 June 2000
 */
abstract class VM_ContextListener extends VM_Listener implements Uninterruptible {

  /**
   * Entry point when listener is awoken.
   *
   * @param sfp  pointer to stack frame where call stack should start 
   *             to be examined.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *            EPILOGUE?
   */
  abstract public void update(Address sfp, int whereFrom);
}
