/*
 * (C) Copyright IBM Corp. 2001
 */

/**
 * OPT_Assembler throws this exception when the branch target of 
 * IG_PATCH_POINT is not in the range from -2^25 ~ 2^25-1.
 * Although it may not happen in real benchmarks, the opt compiler 
 * should catch this exception and turn IG_PATCH_POINT to other 
 * safe inline guards.
 *  
 * @see OPT_Assembler, 
 *
 * @author Feng Qian
 */
class OPT_PatchPointGuardedInliningException extends 
    OPT_OptimizingCompilerException {
  int outOfRangeOffset;
  
  /**
   * @param offset, the offset of patch point target, it must be
   *                out of range (-2^25 ~ 2^25-1)
   */
  OPT_PatchPointGuardedInliningException(int offset) {
    super("The target of patch point is out of range :" +offset, 
	  false);
    this.outOfRangeOffset = offset;
  }
}



