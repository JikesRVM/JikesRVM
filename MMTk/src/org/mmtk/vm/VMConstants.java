/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */

package org.mmtk.vm;


/**
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public interface VMConstants {
  public static final byte LOG_BYTES_IN_ADDRESS() { return 3; }
  public static final byte LOG_BYTES_IN_WORD() { return 3; }
  public static final byte LOG_BYTES_IN_PAGE() { return 12; }

  public static final byte LOG_BYTES_IN_PARTICLE() { return 1; }
  public static final byte MAXIMUM_ALIGNMENT_SHIFT() { return 1; }
  public static final int MAX_BYTES_PADDING() { return 1; }
}

