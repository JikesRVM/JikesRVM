/**
  * (C) Copyright Department of Computer Science,
  * Australian National University. 2004.
  */
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;
import org.mmtk.vm.TraceInterface;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class specializes SortSharedQueue to sort objects according to
 * their time of death (TOD).
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
final public class SortTODSharedDeque extends SortSharedDeque 
  implements Uninterruptible {
  public final static String Id = "$Id$"; 

  private static final int BYTES_PUSHED = BYTES_IN_ADDRESS * 5;
  private static final int MAX_STACK_SIZE = BYTES_PUSHED * 64;
  private static final Offset INSERTION_SORT_LIMIT = Offset.fromInt(80);
  
  /**
   * Constructor
   *
   * @param rps The space from which the instance should obtain buffers.
   * @param arity The arity of the data to be enqueued
   */
  public SortTODSharedDeque(RawPageSpace rps, int arity) {
    super(rps, arity);
  }
  
  /**
   * Return the sorting key for the object passed as a parameter.
   *
   * @param obj The address of the object whose key is wanted
   * @return The value of the sorting key for this object
   */
  protected final Word getKey(Address obj) {
    return TraceInterface.getDeathTime(obj.toObjectReference());
  }
}
