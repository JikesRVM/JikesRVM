/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
// Temporary class until "System.out" is available.
//
class SystemOut
   {
   static void writeOut(String s) { System.out.print(s); }
// static void writeOut(String s) { VM.sysWrite(s); }
   
   static void print(boolean x)   { writeOut(x + ""); }
   static void print(byte    x)   { writeOut(x + ""); }
   static void print(short   x)   { writeOut(x + ""); }
   static void print(char    x)   { writeOut(x + ""); }
   static void print(int     x)   { writeOut(x + ""); }
   static void print(long    x)   { writeOut(x + ""); }
   static void print(float   x)   { writeOut(x + ""); }
   static void print(double  x)   { writeOut(x + ""); }
   static void print(String  x)   { writeOut(x + ""); }
   static void print(Object  x)   { writeOut(x + ""); }

   static void println()          { writeOut(    "\n"); }
   static void println(boolean x) { writeOut(x + "\n"); }
   static void println(byte    x) { writeOut(x + "\n"); }
   static void println(char    x) { writeOut(x + "\n"); }
   static void println(short   x) { writeOut(x + "\n"); }
   static void println(int     x) { writeOut(x + "\n"); }
   static void println(long    x) { writeOut(x + "\n"); }
   static void println(float   x) { writeOut(x + "\n"); }
   static void println(double  x) { writeOut(x + "\n"); }
   static void println(String  x) { writeOut(x + "\n"); }
   static void println(Object  x) { writeOut(x + "\n"); }
   
   }
