/*
 * (C) Copyright IBM Corp. 2001
 */
// t3GTGC
//$Id$

/** Part of test of jni method management -
 *  generates garbage
 *  @author Runtime group 
 *  @modified by Dick Attanasio
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
   
class t3GTGC extends Thread
   {
   
   t3GTGC()
      {
      }
      
   public static void
   runit(int reps, int length)
      {
      Buf buf;
			int i, j;
			for (j = 0; j < reps; j++) {
			buf = new Buf();
      for (i = 0;i < length;i++)
         buf.append('x');
			buf = null;
      }

      }
   }
