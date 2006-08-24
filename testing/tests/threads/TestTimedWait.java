/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Test Timed Wait.
 *
 *  monitorenter
 *  monitorexit
 *
 *  wait
 *  wait(millis)
 *  notify
 *  notifyAll
 *
 * @author unascribed
 */
class Task extends Thread {
  Mailbox mailbox;
  Flag    flag;
  int     taskId;
   
  Task(Mailbox mailbox, Flag flag, int id) {
    this.mailbox = mailbox;
    this.flag    = flag;
    this.taskId  = id;
  }
   
  public String getTaskName() {
    return "Task "+taskId;
  }

  public void run() {
    mailbox.send(getTaskName());

    try { sleep(200); } catch (InterruptedException e) {}
      
    System.out.println(getTaskName() + " waiting");
    flag.await();
    System.out.println(getTaskName() + " ending");
  }
}

class Mailbox {
  String messages[];
  int    received;
   
  Mailbox(int max) {
    messages = new String[max];
  }
   
  // Add a message to this mailbox.
  //
  synchronized void send(String message) {
    messages[received++] = message;
    if (received == messages.length) {
      System.out.println("mailbox: notification sent to tell main that mailbox is full");
      notify();
    }
  }
   
  // Wait for this mailbox to fill up.
  //
  synchronized void await() {
    if (received != messages.length) {
      try { wait(); } catch (InterruptedException e) {}
    }
    Thread current = Thread.currentThread();
    String name = current instanceof Task ? ((Task)current).getTaskName() : "Main "; 
    System.out.println(name + ": mailbox: notification received");
  }
}

class Flag {
  boolean flag = false;
   
  // Set this flag.
  //
  synchronized void set() {
    Thread current = Thread.currentThread();
    String name = current instanceof Task ? ((Task)current).getTaskName() : "Main "; 
    System.out.println(name + ": flag: notification sent");
    flag = true;
    notifyAll();
  }
      
  // Wait for this flag to be set.
  //
  synchronized void await() {
    Thread current = Thread.currentThread();
    String name = current instanceof Task ? ((Task)current).getTaskName() : "Main "; 
    if (flag == true) {
      System.out.println(name + ": flag: already set");
    } else   {
      while (flag == false) {
        try { wait(1000000); } catch (InterruptedException e) {}
        if (flag == false)    
          System.out.println(name + ": flag: timed out");
      }
      System.out.println(name + ": flag: notification received");
    }
  }
}
   
class TestTimedWait {

  public static void main(String args[]) {
    System.out.println("TestTimedWait");

    int     cnt     = 20;
    Mailbox mailbox = new Mailbox(cnt);
    Flag    flag    = new Flag();
    Task    tasks[] = new Task[cnt];
    
    for (int i = 0; i < cnt; ++i)
      tasks[i] = new Task(mailbox, flag, i);
    
    TestTimedWait test = new TestTimedWait(tasks);
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
    for (int i = 0; i < cnt; ++i) {
      System.out.println("main: joining " + tasks[i].getTaskName());
      try {
        tasks[i].join();
      } catch (InterruptedException e) {
      }
    }

    System.out.println("main: bye");
  }
              
  Task tasks[];
   
  TestTimedWait(Task tasks[]) {
    this.tasks = tasks;
  }
      
  public void run() {
    for (int i = 0; i < tasks.length; ++i)
      tasks[i].start();
  }
}
