/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

class Task extends Thread
   {
   int me;
   int total;
   int[] countArray;
   
     //---------------------------------

   Task(int me, int total, int[] countArray)
      {
        //      System.out.println("Task.constructor- " + Thread.currentThread().getName() + ": inTask constructor ");      
        this.me         = me;
        this.total      = total;
        this.countArray = countArray;
      }
   
     //------------------------

   public void
   run()
    {
      //     System.out.println("Task.run-" + Thread.currentThread().getName() + ": in run routine");            
      if ( me == 0)
        {  // code for the zero thread
          while (countArray[me] <= TestTimeSlicing.endCount){
            //test if last thread has caught up
            if (countArray[total-1] == countArray[me])
              {
                // other threads have caught up, start a new round
                countArray[me]++;
                System.out.println("Task.run-" + Thread.currentThread().getName() + ": incremented to count: " + countArray[me] + "\n");            
              }
            //  spin for a while
            int x = vspinner();  // virtual spin code
          }
          return;
        }
      else {
        // all other threads
        while (countArray[me] <= TestTimeSlicing.endCount){
          // is the previous thread ahead of me
          if (countArray[me - 1] == (countArray[me] + 1))
            {
              // yes - catch up
              countArray[me]++;
              System.out.println("Task.run-" + Thread.currentThread().getName() + ": incremented to count: " + countArray[me]+ "\n");            
            }
          else {
            // I'm caught up - spin a while
            int x = vspinner();  // virtual spin code
          }
        }
        return;
      }
    }// end run

     //-------------------------

     // routine to spin a while
     //
     int vspinner() {
  
       // some dummy calculation
       int sum = 0;
       for ( int i = 1; i < 100; i++ ){
         sum += i;
       }
       return sum;
     } // end vspinner
} // end class

//-------------------------------------------------------------
   
class TestTimeSlicing
   {

     static int    endCount     = 10; // threads count to ten in step

   public static void 
   main(String args[])
      {
        //      System.out.println("TestTimeSlicing- main entered for thread = " + Thread.currentThread().getName() );

      int     cnt         =  3;    // 3 threads
      Task    tasks[]     = new Task[cnt];
      int[]   countArray  = new int[cnt];


      // initialize countArray
      for (int i = 0; i < cnt; ++i) {
         countArray[i] = 0;
      }
      
      //      System.out.println("TestTimeSlicing-clearing out count array\n");
      for (int i = 0; i < cnt; ++i) {
         tasks[i] = new Task(i, cnt, countArray);
      }

      //start up tasks
      //      System.out.println("TestTimeSlicing-about to call test.run" +  Thread.currentThread().getName() + "\n");
      run(tasks);

      // spin and wait for countArray to fill
      //      
      while ( countArray[cnt-1] != endCount){
          int x =  spinner();
      }

      // count array at final values - test is over
      System.out.println("TestTimeSlicing-main:end of Test\n");

      }
              
     // static routine to spin a bit
     //
     static int spinner() {
       // dummy calculation
       int sum = 0;
       for ( int i = 1; i < 100; i++ ){
         sum += i;
       }
       return sum;
     }

      
   static public void 
   run(Task[] tasks)
      {

        //      System.out.println("TestTimeSlicing-run entered ");
      for (int i = 0; i < tasks.length; ++i)
        {
          //      System.out.println("TestTimeSlicing.run -about to start() a task -" +  Thread.currentThread().getName());
          tasks[i].start();
          //      System.out.println("TestTimeSlicing.run- new thread started -" +  Thread.currentThread().getName());
        }
      //          System.out.println("TestTimeSlicing-exiting run -" +  Thread.currentThread().getName());
      }
   }
