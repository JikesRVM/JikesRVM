/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class splits live ranges for certain special cases before register
 * allocation.
 *
 * On PPC, this phase is currently a No-op.
 *
 * @author Stephen Fink
 */
class OPT_SplitLiveRanges extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  final boolean shouldPerform (OPT_Options options) {
    return false;
  }

  /**
   * Return the name of this phase
   * @return "Live Range Splitting"
   */
  final String getName () {
    return "Live Range Splitting"; 
  }

  /**
   * The main method.
   * 
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
  }
}

