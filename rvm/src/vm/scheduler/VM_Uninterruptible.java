/*
 * (C) Copyright IBM Corp. 2001
 */
// Methods of a class that implements this interface
// are treated specially by the machine code compiler:
// (1) the normal thread switch test that would be
//     emitted in the method prologue is omitted.
// (2) the stack overflow test that would be emitted
//     in the method prologue is omitted.
interface VM_Uninterruptible
   {
   }
