/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.jikesrvm;
/**
 * Constants exported by the assembler
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Dave Grove
 * @author Derek Lieber
 */
public interface VM_AssemblerConstants {

  public static final int LT = 0xC<<21 | 0<<16;
  public static final int GT = 0xC<<21 | 1<<16;
  public static final int EQ = 0xC<<21 | 2<<16;
  public static final int GE = 0x4<<21 | 0<<16;
  public static final int LE = 0x4<<21 | 1<<16;
  public static final int NE = 0x4<<21 | 2<<16;

}  
