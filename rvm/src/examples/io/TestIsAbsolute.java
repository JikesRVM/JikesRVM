/*
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
