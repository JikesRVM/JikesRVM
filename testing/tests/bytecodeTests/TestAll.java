/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class TestAll
   {
   public static void main(String args[])
      throws Throwable
      {
   // if (VM.runningVM == false) VM.boot();
      runTest();
      }

   public static void runTest()
      throws Throwable
      {
      SystemOut.println("TestAll");

      // same order as in Makefile
      
      TestConstants.runTest();
      TestStackAccess.runTest();
      TestFieldAccess.runTest();
      TestArrayAccess.runTest();
      TestReturn.runTest();
      TestCompare.runTest();
      TestConversions_toHex.runTest();
      TestArithmetic_toHex.runTest();
      TestMath.runTest();
      TestMath_toHex.runTest();
      TestSwitch.runTest();
      TestStaticCall.runTest();
      TestVirtualCall.runTest();
      TestInterfaceCall.runTest();
      TestSpecialCall.runTest();
      TestMiranda.runTest();
      TestClassInitializer.runTest();
      TestThrow.runTest();
      TestFinally.runTest();
      TestInstanceOf.runTest();
      TestClone.runTest();
      TestMonitorUnwind.runTest();
      TestReflection.runTest();
      TestClassLoading.runTest();
      // TestStackTrace.runTest();      //!!TODO
      TestMetaclass.runTest();
      TestMiranda.runTest();
      TestGC.runTest();
      }
   }
