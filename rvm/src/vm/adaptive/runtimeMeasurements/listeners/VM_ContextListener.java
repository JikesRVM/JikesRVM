/*
 * (C) Copyright IBM Corp. 2001
 */
/*
 * VM_ContextListeners.java  
 *
 * @author Peter Sweeney
 * @date   2 June 2000
 *
 * A VM_ContextListener is an object that is invoked when
 * online measurement information must be collected.
 *
 **/

abstract class VM_ContextListener extends VM_Listener implements VM_Uninterruptible {

  /**
   * Entry point when listener is awoken.
   *
   * @param sfp  pointer to stack frame where call stack should start 
   *             to be examined.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *            EPILOGUE?
   */
  abstract public void update(int sfp, int whereFrom);
}
