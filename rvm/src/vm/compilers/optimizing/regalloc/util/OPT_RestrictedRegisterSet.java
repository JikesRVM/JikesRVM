/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * An instance of this class represents restrictions on physical register 
 * assignment.
 * 
 * @author Stephen Fink
 */
final class OPT_RestrictedRegisterSet {
  /**
   * The set of registers to which assignment is forbidden.
   */
  private OPT_BitSet bitset;

  /**
   * Default constructor
   */
  OPT_RestrictedRegisterSet(OPT_PhysicalRegisterSet phys) {
    bitset = new OPT_BitSet(phys);
  }

  /**
   * Add a particular physical register to the set.
   */
  void add(OPT_Register r) {
    bitset.add(r);
  }

  /**
   * Add a set of physical registers to this set.
   */
  void addAll(OPT_BitSet set) {
    bitset.addAll(set);
  }

  /**
   * Does this set contain a particular register?
   */
  boolean contains(OPT_Register r) {
    return bitset.contains(r);
  }
}
