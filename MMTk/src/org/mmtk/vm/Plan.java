/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.vm;

import org.mmtk.plan.*;


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
   * <code>true</code> if built with GCSpy
   */
  public static final boolean WITH_GCSPY = false;

  /**
   * Gets the plan instance associated with the current processor.
   *
   * @return the plan instance for the current processor
   */
  public static Plan getInstance() 
  {
    return null;
  }
}
