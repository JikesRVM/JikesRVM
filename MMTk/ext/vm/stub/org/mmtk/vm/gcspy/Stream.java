/*
 * (C) Copyright Richard Jones, 2003
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.vm.gcspy;

import org.mmtk.utility.gcspy.Color;

import org.vmmagic.pragma.*;

/**
 *
 * VM-neutral stub file to set up a GCspy Stream, by forwarding calls
 * to gcspy C library
 *
 * $Id$
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */

public class Stream implements  Uninterruptible {
  public Stream(ServerSpace driver,
          int id,       
          int dataType,
          String name,
          int minValue,         
          int maxValue,
          int zeroValue,
          int defaultValue,
          String stringPre,
          String stringPost,
          int presentation,
          int paintStyle,
          int maxStreamIndex,
          Color colour) {
  }
  public int getMinValue() { return 0; }
  public int getMaxValue() { return 0; }
}

