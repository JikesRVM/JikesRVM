/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Test notification.
 *
 *  monitorenter
 *  monitorexit
 *
 *  wait
 *  notify
 *  notifyAll
 *
 * @author unascribed
 */

class Task extends Thread
   {
   Mailbox mailbox;
   Flag    flag;
   
   Task(Mailbox mailbox, Flag flag)
      {
      this.mailbox = mailbox;
      this.flag    = flag;
      }
   
   public void
   run()
      {
      mailbox.send(getName());

      try { sleep(200); } catch (InterruptedException e) {}
      
      System.out.println(getName() + " waiting");
      flag.await();
      System.out.println(getName() + " ending");
      }
   }

class Mailbox
   {
   String messages[];
   int    received;
   
   Mailbox(int max)
      {
      messages = new String[max];
      }
   
   // Add a message to this mailbox.
   //
   synchronized void
   send(String message)
      {
      messages[received++] = message;
      if (received == messages.length)
         {
         System.out.println("mailbox: notification sent to tell main that mailbox is full");
         notify();
         }
      }
   
   // Wait for this mailbox to fill up.
   //
   synchronized void
   await() {
       if (received != messages.length) {
	   try { wait(); } catch (InterruptedException e) {}
       }
       System.out.println(Thread.currentThread().getName() + ": mailbox: notification received");
   }
}

class Flag
   {
   boolean flag = false;
   
   // Set this flag.
   //
   synchronized void
   set()
      {
      System.out.println(Thread.currentThread().getName() + ": flag: notification sent");
      flag = true;
      notifyAll();
      }
      
   // Wait for this flag to be set.
   //
   synchronized void
   await()
      {
      if (flag == false)
         {
         try { wait(); } catch (InterruptedException e) {}
         System.out.println(Thread.currentThread().getName() + ": flag: notification received");
         }
      else
         System.out.println(Thread.currentThread().getName() + ": flag: already set");
      }
   }
   
class TestNotification
   {
   public static void 
   main(String args[])
      {
      System.out.println("TestNotification");

      int     cnt     = 20;
      Mailbox mailbox = new Mailbox(cnt);
      Flag    flag    = new Flag();
      Task    tasks[] = new Task[cnt];
      
      for (int i = 0; i < cnt; ++i)
         tasks[i] = new Task(mailbox, flag);
      
      TestNotification test = new TestNotification(tasks);
      test.run();
      
      // wait for mailbox to fill up
      mailbox.await();
      
      // display mail
      for (int i = 0; i < cnt; ++i)
         System.out.println("main: " + mailbox.messages[i] + " replied");

      // pause to allow tasks to queue up on flag
      System.out.println("main: sleeping");
      try { Thread.currentThread().sleep(1000); } catch (InterruptedException e) {}
      System.out.println("main: running");
   
      // release tasks waiting on flag, letting them terminate
      flag.set();
      
      // wait for them to terminate
      for (int i = 0; i < cnt; ++i)
         {
         System.out.println("main: joining " + tasks[i].getName());
         try {
             tasks[i].join();
             }
         catch (InterruptedException e)
             {
             }
         }

      System.out.println("main: bye");
      }
              
   Task tasks[];
   
   TestNotification(Task tasks[])
      {
      this.tasks = tasks;
      }
      
   public void 
   run()
      {
      for (int i = 0; i < tasks.length; ++i)
         tasks[i].start();
      }
   }
