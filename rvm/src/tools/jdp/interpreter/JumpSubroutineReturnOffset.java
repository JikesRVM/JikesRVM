/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author John Barton
 */
  public class JumpSubroutineReturnOffset
      {
      public JumpSubroutineReturnOffset(int offset_in)
	 {
	 offset = offset_in;
	 }
      public int getBytecodeOffset()
	 {
	 return offset;
	 }
      int offset = 0;
      public String toString()
	 {
	 return "JumpSubroutineReturnOffset="+offset;
	 }
     }

