/*
 * ===========================================================================
 * IBM Confidential
 * Software Group 
 *
 * Eclipse/Jikes
 *
 * (C) Copyright IBM Corp., 2002.
 *
 * The source code for this program is not published or otherwise divested of
 * its trade secrets, irrespective of what has been deposited with the U.S.
 * Copyright office.
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
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
