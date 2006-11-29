package com.ibm.jikesrvm.memorymanagers.mminterface;

import com.ibm.jikesrvm.VM_Processor;

import org.vmmagic.pragma.*;

public class Selected {
  @Uninterruptible
  public static final class Plan {
    private static final 
//-#value RVM_WITH_MMTK_PLAN
    plan = new 
//-#value RVM_WITH_MMTK_PLAN
    ();
    public static 
//-#value RVM_WITH_MMTK_PLAN
    get() throws InlinePragma { return plan; }
  }

  @Uninterruptible
  public static final class Constraints {
    private static final 
//-#value RVM_WITH_MMTK_PLANCONSTRAINTS
    constraints = new 
//-#value RVM_WITH_MMTK_PLANCONSTRAINTS
    ();
    public static 
//-#value RVM_WITH_MMTK_PLANCONSTRAINTS
    get() throws InlinePragma { return constraints; }
  }

  @Uninterruptible
  public static class Collector extends 
//-#value RVM_WITH_MMTK_COLLECTORCONTEXT
  {
    private VM_Processor processor;
    public Collector(VM_Processor parent) { processor = parent; }
    public final VM_Processor getProcessor() throws InlinePragma { return processor; }
    public static final Collector get() throws InlinePragma { return VM_Processor.getCurrentProcessor().collectorContext; }
  }

  @Uninterruptible
  public static class Mutator extends 
//-#value RVM_WITH_MMTK_MUTATORCONTEXT
  {
    public final VM_Processor getProcessor() throws InlinePragma { return (VM_Processor) this; }
    public static final Mutator get() throws InlinePragma { return VM_Processor.getCurrentProcessor(); }
  } 
}
