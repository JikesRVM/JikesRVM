/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Disassembler for the Intel instruction set
 *
 * Source code in C borrowed from C++ VisualAge, 
 * provided by Steve Turner (IBM Austin)
 * Minor change:  change output to lower case
 *
 * @author Ton Ngo 
 * @date 2/12/2001 
 */
public class IntelDisassembler {

  static {
    System.loadLibrary("IntelDisassembler");
  }

  /**
   * Disassemble up to the number of instruction, 
   * -if count is nonzero, disassemble up to count
   * -if count is zero, disassemble until there is no more valid 
   *  instructions in the buffer
   */
  public static native String disasm(byte[] instr, int count, int address);

  public static String disasm(byte[] instr) {
    return disasm(instr, 0, 0);
  }

  /**
   * Compute the instruction length for a block of instruction
   */
  public static native byte[] instructionLength(byte[] instr);

  /**
   * Test if this is a CALL instruction
   */
  public static boolean isCallInstruction(byte[] instrArray) {
    String instructionString = disasm(instrArray, 1, 0);
    if (instructionString.indexOf("CALL") == -1)
      return false;
    else 
      return true;
  }

  /**
   * Compute the target address for a call, jump, or jump conditional
   */
  public static native int getBranchTarget(byte instrArray[], int[] regs);
  // just a way to return more info from decoding the branch target
  public static native boolean isLastAddressIndirect();   

  /**
   * for stand alone testing 
   */
  static byte testInstructions[] = {
    (byte) 0x55, (byte) 0x89, (byte) 0xe5, (byte) 0x83, 
    (byte) 0xec, (byte) 0x08, (byte) 0xc7, (byte) 0x45, 
    (byte) 0xfc, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
    (byte) 0x00, (byte) 0xc7, (byte) 0x45, (byte) 0xf8, 
    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
    (byte) 0x83, (byte) 0x7d, (byte) 0xf8, (byte) 0x09, 
    (byte) 0x7e, (byte) 0x06, (byte) 0xeb, (byte) 0x14, 
    (byte) 0x8d, (byte) 0x74, (byte) 0x26, (byte) 0x00, 
    (byte) 0x8b, (byte) 0x45, (byte) 0xf8, (byte) 0x01, 
    (byte) 0x45, (byte) 0xfc, (byte) 0xff, (byte) 0x45, 
    (byte) 0xf8, (byte) 0xeb, (byte) 0xe9, (byte) 0x90, 
    (byte) 0x8d, (byte) 0x74, (byte) 0x26, (byte) 0x00, 
    (byte) 0x8b, (byte) 0x45, (byte) 0xfc, (byte) 0x50, 
    (byte) 0x68, (byte) 0x70, (byte) 0x84, (byte) 0x04, 
    (byte) 0x08, (byte) 0xe8, (byte) 0xfa, (byte) 0xfe, 
    (byte) 0xff, (byte) 0xff, (byte) 0x83, (byte) 0xc4, 
    (byte) 0x08, (byte) 0xc9, (byte) 0xc3, (byte) 0x90, 
    (byte) 0x90};

  // for testing 
  public static void main(String args[]) {
    System.out.println("Disassemble Intel: " + testInstructions.length +
                       " bytes");
    System.out.println("return mnemonic: " + disasm(testInstructions));
  }
}
