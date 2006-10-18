/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp., 2002.
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */

import java.io.File;

/**
 * Tests whether isAbsolute() handles zero-length names
 * correctly.  Used to throw a NullPointerException.
 *
 * @author Jeffrey Palm
 * @since  28 Jun 2002
 */
public class TestIsAbsolute {
  public static void main(String[] args) throws Exception {
    if (new File("").isAbsolute()) {
      throw new Error("empty filenames should be !isAbsolute");
    }      
  }
}
