/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Use this exception when the opt compiler attempts to 
 * compile/optimize a method containing a currently
 * unsupported (but expected) operation.
 * The main intended use is in prototype/incomplete optimizations
 * which may not handle every case, but which will eventually be 
 * extended to handle the excepting case. If the unsupported operation
 * really is a serious problem, then one should use 
 * an OptimzingCompilerException.
 *
 * We define this to be a non-fatal OPT_OptimizingCompilerException.
 *
 * @author Dave Grove
 */
class OPT_OperationNotImplementedException extends 
    OPT_OptimizingCompilerException {

  OPT_OperationNotImplementedException (String s) {
    super(s, false);
  }
}



