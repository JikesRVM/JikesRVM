/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Anthony Cocchi
 */
//-#if RVM_WITH_QUICK_COMPILER
public 
  //-#endif
  final class VM_BasicBlock {

 // structure to describe the basic blocks of the byte code
 // Used in calculating stack map and local variable map for
 // the garabage collector.

 // NOTE: Block number 1 is the epilog block, the only block
 // that exits from the method. Blocks that end in a return will
 // have the exit block as their only successor.
 // NOTE: Block number 0 is NOT used;

  // ------------------- Static Class Fields -----------------

  public static final int NOTBLOCK = 0;
  public static final int EXITBLOCK = 1;
  public static final int STARTPREDSIZE = 4;
  public static final int STARTBBNUMBER = 2;

  static final byte JSRENTRY = 1;
  static final byte JSREXIT  = 2;
  static final byte METHODENTRY = 4;
  static final byte TRYSTART = 8;
  static final byte TRYBLOCK = 16;
  static final byte INJSR = 32;
  static final byte TRYHANDLERSTART = 64;

  // --------------------- Instance Fields ---------------------

  public int     blockNumber;// ID number (index into block array)
  int     start;             // starting byte in byte code array
  int     end;               // ending byte in byte code array
  int     predcount = 0;     // number of preceeding basic blocks
                             // First 2 are listed individually.
  short   pred1;     
  short   pred2;
  short[] restPredecessors;  // list of rest preceeding basic blocks 
                             // may be bigger then predcount;
  byte state  = 0;           // additional state info for jsr handling, 
                             // and other flags

  // --------------- Constructor --------------------------------

 // This should be called only from the factory.
 VM_BasicBlock (int startval, int bn) {
   blockNumber = bn;
   start = startval;
 }

 // This should be used when building the EXIT block
 // EXIT is likely to have several predecessors.
 VM_BasicBlock(int startval, int endval, int blockNumber) {
   start = startval;
   end = endval;
   this.blockNumber = blockNumber;
   restPredecessors = new short[STARTPREDSIZE];
 }

  // ------------------ Static Methods -------------------------

 void setStart(int startval) {
   start = startval;
 }

 // transfer predecessor blocks from one block to another
 public static void
 transferPredecessors(VM_BasicBlock fromBB, VM_BasicBlock toBB) {
   toBB.predcount = fromBB.predcount;
   toBB.pred1 = fromBB.pred1;
   toBB.pred2 = fromBB.pred2;
   toBB.restPredecessors = fromBB.restPredecessors;
   
   fromBB.predcount = 0;
   fromBB.restPredecessors = null;
 }

  // -------------------------- Instance Methods ----------------

 void setEnd(int endval) {
   end = endval;
 }

 void setState(byte stateval) {
   state |= stateval;
 }

 public int getStart(){
   return start;
 }

 public int getEnd() {
   return end;
 }

 public int getBlockNumber() {
   return blockNumber;
 }

 public byte getState() {
    return  state;
 }

 public boolean isJSRExit() {
     return ((state&JSREXIT)==JSREXIT);
 }

 public boolean isJSREntry() {
     return ((state&JSRENTRY)==JSRENTRY);
 }

 public boolean isInJSR() {
   return ((state&INJSR)==INJSR);
 }

 public boolean isMethodEntry() {
   return ((state&METHODENTRY)==METHODENTRY);
 }

 public boolean isTryStart() {
   return ((state&TRYSTART)==TRYSTART);
 }

 public boolean isTryBlock() {
   return ((state&TRYBLOCK)==TRYBLOCK);
 }

 public boolean isTryHandlerStart() {
   return ((state&TRYHANDLERSTART)==TRYHANDLERSTART);
 }

 public void 
 addPredecessor(VM_BasicBlock predbb) {
   predcount++;
   if (predcount == 1)
     pred1 = (short)predbb.getBlockNumber();
   else if (predcount == 2)
     pred2 = (short)predbb.getBlockNumber();
   else if (restPredecessors == null) {
     restPredecessors = new short[STARTPREDSIZE];
     restPredecessors[predcount-3] = (short)predbb.getBlockNumber();
   }
   else {
     if (restPredecessors.length <= predcount-3) {
       short[] newpreds = new short[predcount<<1];
       int restLength = restPredecessors.length;
       for (int i=0; i<restLength; i++) 
          newpreds[i] = restPredecessors[i];
       restPredecessors = newpreds;
       newpreds = null;
     }
     restPredecessors[predcount-3] = (short)predbb.getBlockNumber();
     // -3 to get it zero-based
   }
 }

 // This method first checks if a block is already on the predecessor
 // list. Used with try blocks being added to their catch block as
 // predecessors. 
 public void 
 addUniquePredecessor(VM_BasicBlock predbb) {
   boolean dupFound = false, checkMade = false;
   short predbbNum = (short)predbb.getBlockNumber();

   if (predcount >= 1) {
     if (pred1 == predbbNum)
        return;
     
     if (predcount > 1) {
        if (pred2 == predbbNum)
          return;

        if (predcount > 2) {
         if (restPredecessors.length <= predcount-2) {
            short[] newpreds = new short[predcount<<1];
            int restLength = restPredecessors.length;
            for (int i=0; i<restLength; i++) {
              if (restPredecessors[i] == predbbNum)
                dupFound = true;            // finish up the copy anyway.
              newpreds[i] = restPredecessors[i];
            }
            restPredecessors = newpreds;
            newpreds = null;

            if (dupFound) return;
            checkMade = true;
         }

         if (!checkMade) {
          for (int i=0; i<predcount-2; i++) 
             if (restPredecessors[i] == predbbNum)
               return;
         }

         predcount++;
         restPredecessors[predcount-3] = predbbNum;
        }
        else {  // predcount must be 2
          restPredecessors = new short[STARTPREDSIZE];
          predcount++;
          restPredecessors[predcount-3] = predbbNum;
        }
     }  // predcount must be 1
     else { 
        predcount++;
        pred2 = predbbNum;
     }
   }
   else { // predcount must be 0
     predcount++;
     pred1 = predbbNum;
   }
 }

 public int[] getPredecessors() {

   int [] preds;
   preds = new int[predcount];
   if (predcount >= 1)
     preds[0] = pred1;
   if (predcount > 1)
     preds[1] = pred2;
   for (int i = 0; i < predcount-2 ; i++)
     preds[i+2] = restPredecessors[i];
   return preds;

 }

}
