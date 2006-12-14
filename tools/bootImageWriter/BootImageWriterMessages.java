/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//BootImageWriterMessages.java
//$Id$

/**
 * Functionality to write messages during image generation.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImageWriterMessages {

  protected static void say(String...messages) {
    System.out.print("BootImageWriter: ");
    for (String message : messages)
      System.out.print(message);
    System.out.println();
}

  protected static void fail(String message) throws Error {
    throw new Error("\nBootImageWriter: " + message);
  }
}

