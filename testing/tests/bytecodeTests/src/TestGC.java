/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */

class Buf
   {
   char [] buf;
   
   Buf()
      {
      buf = new char[0];
      }
      
   synchronized
   void append(char c)
      {
      char [] newbuf = new char[buf.length + 1];
      System.arraycopy(buf, 0, newbuf, 0, buf.length);
      newbuf[buf.length] = c;
      buf = newbuf;
      }
   
   }
   
class TestGC extends Thread
   {
   Buf buf;
   
   TestGC(Buf buf)
      {
      this.buf = buf;
      }
      
   public void
   run()
      {
      for (;;)
         buf.append('x');
      }

   public static void main(String[] args)
      {
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestGC");
      
      Buf buf = new Buf();

      new TestGC(buf).start();
      new TestGC(buf).start();
      new TestGC(buf).start();
      new TestGC(buf).start();
      new TestGC(buf).start();
      new TestGC(buf).start();

      // run for 10 seconds
      //
      try { sleep(10000); } catch(Exception e) {}

      SystemOut.println("bye");
      System.exit(0);
      }
   }
