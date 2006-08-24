/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestDeadVPQueueWorker extends Thread
   {

   static final int starting      = 1;
   static final int inConstructor = 2;
   static final int running       = 3;
   static final int ending        = 4;

   int    state = starting;
   String myname;

   static int count = 0;

   TestDeadVPQueueWorker(String name)
      {
      myname = name;
      state = inConstructor;
      }

 public static native int nativeFoo(int count);

   public void
   run() //- overrides Thread
      {
        state = running;
        count++;

        System.out.println(myname + ": running count = " + count);

        int returnValue = nativeFoo(17);
        System.out.println("First nativeFoo return " + returnValue);
    
        returnValue = nativeFoo(30);
        System.out.println("Second nativeFoo return " + returnValue);
        
        
        state = ending;
      }
   }
