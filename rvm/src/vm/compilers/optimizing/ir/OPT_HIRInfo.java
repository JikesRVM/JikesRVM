/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Wrapper class around IR info that is valid on the HIR/LIR/MIR
 *
 * @author Dave Grove
 */
final class OPT_HIRInfo {
  
  OPT_HIRInfo(OPT_IR ir) { }

  /** Place to hang dominator tree. */
  public OPT_DominatorTree dominatorTree;

  /** Were dominators computed successfully ? */
  public boolean dominatorsAreComputed;

  /** Place to hang post-dominator tree. */
  public OPT_DominatorTree postDominatorTree;

  /** Were post-dominators computed successfully ? */
  public boolean postDominatorsAreComputed;

  /** Place to hang Heap SSA information. */
  public OPT_SSADictionary SSADictionary;

  /** Place to hang global value number information. */
  public OPT_GlobalValueNumberState valueNumbers;

  /** Place to hang uniformly generated global value number information. */
  public OPT_GlobalValueNumberState uniformlyGeneratedValueNumbers;

  /** Place to hang Loop Structure Tree (LST) */
  public OPT_LSTGraph LoopStructureTree;

  /** Place to hang results of index propagation analysis */
  public OPT_DF_Solution indexPropagationSolution;
}
