/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.vm;

import org.mmtk.plan.*;
import org.mmtk.policy.RefCountLocal;
import org.vmmagic.pragma.InlinePragma;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;


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
public class Plan extends SemiSpace {

  /***********************************************************************
   *
   * Class variables
   */


  /**
   * Gets the plan instance associated with the current processor.
   *
   * @return the plan instance for the current processor
   */
  public static Plan getInstance() 
  {
    return null;
  }

  /**
   * Stubs required by the generational collectors
   * 
   * It may seem that the visibility of these methods is being changed
   * (Against Java's rules), but this is not the case, since this stub
   * extends SemiSpace.
   */
  public static final ObjectReference traceMatureObject(ObjectReference object) {
	  return object;
  }
  public static void forwardMatureObjectLocation(Address location,
          ObjectReference object) {}
  public static final ObjectReference getForwardedMatureReference(ObjectReference object) {
	    return object;
  }

  /**
   * Stubs required by the reference counting collectors
   */
  public final void addToRootSet(ObjectReference root) 
    throws InlinePragma {
  }
  public RefCountLocal rc;
  public final void enumerateDecrementPointerLocation(Address location) { }
  public final void enumerateModifiedPointerLocation(Address objLoc) {}
  public final void checkSanityTrace(ObjectReference object, Address location) {}
  public final void incSanityTrace(ObjectReference object, Address location,
          boolean root) {}
  public final void addToDecBuf(ObjectReference object) { }
}
