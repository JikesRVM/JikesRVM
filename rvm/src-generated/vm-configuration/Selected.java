package com.ibm.jikesrvm.memorymanagers.mminterface;

import com.ibm.jikesrvm.VM_Processor;

import org.vmmagic.pragma.*;

public class Selected {
  @Uninterruptible
  public static final class Plan extends 
//-#value RVM_WITH_MMTK_PLAN
  {
    private static final Plan plan = new Plan(); 
    
    @Inline
    public static Plan get() { return plan; } 
  }

  @Uninterruptible
  public static final class Constraints extends
//-#value RVM_WITH_MMTK_PLANCONSTRAINTS
  {
    private static final Constraints constraints = new Constraints();
    
    @Inline
    public static Constraints get() { return constraints; } 
  }

  @Uninterruptible
  public static class Collector extends 
//-#value RVM_WITH_MMTK_COLLECTORCONTEXT
  {
    private VM_Processor processor;
    public Collector(VM_Processor parent) { processor = parent; }
    @Inline
    public final VM_Processor getProcessor() { return processor; } 
    @Inline
    public static Collector get() { return VM_Processor.getCurrentProcessor().collectorContext; } 
  }

  @Uninterruptible
  public static class Mutator extends 
//-#value RVM_WITH_MMTK_MUTATORCONTEXT
  {
    @Inline
    public final VM_Processor getProcessor() { return (VM_Processor) this; } 
    @Inline
    public static Mutator get() { return VM_Processor.getCurrentProcessor(); } 
  } 
}
