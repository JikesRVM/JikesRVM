/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/*
 * This is the implementation for memory that provides access from an external process
 * @author Ton Ngo  2/98
 */

import java.util.*;

class memoryExternal extends memory
{
  /**
   * Constructor
   * @param  process  the OsProcess that owns this memory interface
   * @return 
   * @exception
   * @see memory
   */
  public memoryExternal (OsProcess process) {
    super(process);
  }

  /**
   * Implement abstract method: Read an entry in the Table of Content (TOC)
   * @param offset the byte address offset into the TOC
   * @return the content of this TOC entry
   * @exception
   * @see VM_Statics
   */
  public int readTOC(int offset) {
    try {
      int address = offset + owner.reg.getJTOC();
      return read(address);
    } catch (Exception e) {
      System.out.println("Could not read JTOC register, check if the name JT have changed in VM_BaselineConstants.java, GPR_NAMES");    
      return 0;
    }
  }

  /**
   * Implement abstract method: Find the address of an entry in the Table of Content (TOC)
   * @param offset the byte address offset into the TOC
   * @return the address of this TOC entry 
   * @exception
   * @see VM_Statics 
   */
  public int addressTOC(int offset) {
    try {
      int address = offset + owner.reg.getJTOC();
      return address;
    } catch (Exception e) {
      System.out.println("Could not read JTOC register, check if the name JT have changed in VM_BaselineConstants.java, GPR_NAMES");      
      return 0;
    }
  }


  /**
   * Methods for accessing memory
   * Reading and writing memory go through the aixPlatform class
   */

  /**
   * True machine level memory read
   */
  public int read(int address) {
    return Platform.readmem(address);
  }

  public int[] readblock(int address, int count) {
    return Platform.readmemblock(address,  count);
  }

  public void write(int address, int value) {
    Platform.writemem(address, value);
  }

  //******************************************************************************
  // Methods for handling machine instruction:  disassembler, computing branch target
  //******************************************************************************
  public int branchTarget(int instruction, int address) {
    return Platform.branchTarget(instruction, address);
  }

  /**
   *  Check if the instruction is one of the 2 trap instructions
   *      t  T0,RA,RB      opcode 011111  bits 21:30 = 00000000100
   *      ti T0,RA,SI      opcode 000011  
   * @param instruction a powerPC instruction
   * @return true if the instruction is one of the 2 instructions above, false otherwise
   */
  public boolean isTrapInstruction(int instruction) {
    int opcode = (instruction & 0xFC000000) >>> 26;
    int bits21_30 = (instruction & 0x000007FE) >>> 1;    
    if (opcode==31 && bits21_30==4) 
      return true;
    else if (opcode==3)
      return true;
    else
      return false;

  }

  /**
   * Get the name of a native procedure from the text block at the end of the
   * instruction block
   * @param instructionAddress an arbitrary instruction address
   * @return the name of the native procedure
   *
   */
  public String getNativeProcedureName(int instructionAddress) {
    return Platform.getNativeProcedureName(instructionAddress);
  }

}
