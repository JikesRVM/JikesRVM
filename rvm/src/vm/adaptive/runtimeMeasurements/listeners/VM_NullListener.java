/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * A VM_NullListener is an object that is invoked when
 * online measurement information must be collected.
 *
 * Defines update's interface.
 *
 * @author Peter Sweeney
 * @date   2 June 2000
 */

abstract class VM_NullListener extends VM_Listener implements VM_Uninterruptible {
  /**
   * Entry point when listener is awoken.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *             EPILOGUE?
   */
  abstract public void update(int whereFrom);
}
