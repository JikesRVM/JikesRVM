class t3GTWorker2 extends Thread {

  int        arg1;
  boolean    isReady;
  boolean    isFinished;
  
  t3GTWorker2(int arg1)
  {
    this.arg1 = arg1;
    this.isFinished = false;
    this.isReady    = false;
  }
  
  public void start() //- overrides Thread
  {
    super.start();
  }
  
  public void run()  //- overrides Thread
  {

		isReady = true;
		while (isReady) {
    try {
      Thread.currentThread().sleep(arg1);
    }
    catch (InterruptedException e) {
			System.out.println(" GC thread returning");
			isFinished = true;
    }
			if (isFinished) return;
    System.gc();
    }

  }
}

