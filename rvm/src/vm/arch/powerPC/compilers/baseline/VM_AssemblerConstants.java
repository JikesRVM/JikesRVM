/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
/**
 * Constants exported by the assembler
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Dave Grove
 * @author Derek Lieber
 */
interface VM_AssemblerConstants {

  public static final int LT = 0xC<<21 | 0<<16;
  public static final int GT = 0xC<<21 | 1<<16;
  public static final int EQ = 0xC<<21 | 2<<16;
  public static final int GE = 0x4<<21 | 0<<16;
  public static final int LE = 0x4<<21 | 1<<16;
  public static final int NE = 0x4<<21 | 2<<16;

}  
