/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


/**
 * Process command line options for accessing HPM trace files.
 *
 * @author Peter F. Sweeney
 * @date 2/22/2003
 */

class CommandLineOptions 
{
  
  static private int local_debug = 1;

  static public final int UNINITIALIZED = -1;
  /*
   * command line arguments
   */
  // name of application interested in
  public String  application = null;
  // run of application interested in
  public int  run = -1;
  // group index (POWER4)
  public int  group_index = UNINITIALIZED;
  // generate statistics: assume event and thread index are set, compute average, min and max.
  //  public boolean generate_statistics = false;
  // no yields: if true only consider time interrupted thread switches (ie tid > 0)
  public boolean no_yields = false;
  // print hpm aggregate values
  public boolean print_aggregate = false;
  // print hpm aggregate values by thread; i.e. not interested in processor
  public boolean print_aggregate_by_thread = false;
  // print hpm events
  public boolean print_events  = false;
  // print mids
  public boolean print_mids  = false;
  // print full name instead of mid
  public boolean print_fullname  = false;
  // print structure of trace file
  public boolean print_structure = false;
  // field mask: identify what fields to examine
  public int event_mask  = UNINITIALIZED;
  public int event_mask_array[] = {1, 2, 4, 8, 16, 32, 64, 128, 256};
  // print for each individual input trace file
  public boolean print_local   = false;
  // print active threads
  public boolean print_threads = false;
  // print hpm trace values
  public boolean print_trace = false;
  // thread index
  public int tid = UNINITIALIZED;
  // array of trace file names
  public String[] trace_filenames = new String[20];
  // index into array of trace file names
  public int trace_file_index = 0;
  // be verbose
  public int verbose = 0;
  // debug
  public int debug = 0;


  /** 
   * Constructor
   */
  public CommandLineOptions() {
    allocateStatistics();
    resetStatistics();
  }
  /*
   * Process command line arguments
   *
   * @param args       array command line arguments
   * @param singleton  process only one trace file allowed
   */
  public void processArgs(String args[], boolean singleton) 
  {
    for (int i=0; i<args.length; i++) {
      String arg = args[i];
      if (arg.compareTo("-aggregate") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -aggregate");
        print_aggregate = true;
      } else if (arg.compareTo("-aggregate_by_thread") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -aggregate_by_thread");
        print_aggregate_by_thread = true;
      } else if (arg.compareTo("-application") == 0) {
        application = args[++i];
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -application "+application);
      } else if (arg.compareTo("-events") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -events");
        print_events = true;
      } else if (arg.compareTo("-mids") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -mids");
        print_mids = true;
      } else if (arg.compareTo("-fullname") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -mids");
        print_fullname = true;
      } else if (arg.compareTo("-structure") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -structure");
        print_structure = true;
      } else if (arg.compareTo("-group_index") == 0) {
        String Index = args[++i];
        try {
          group_index = Integer.parseInt(Index);
        } catch (NumberFormatException e) {
          System.out.println("\n***CommandLineOptions.processArgs() \"-group_index "+Index+"\" throws NumberFormatException!***");
          usage();
        }
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -group_index "+group_index);
      } else if (arg.compareTo("-event_mask") == 0) {
        String Mask = args[++i];
        try {
          event_mask = Integer.parseInt(Mask);
        } catch (NumberFormatException e) {
          System.out.println("\n***CommandLineOptions.processArgs() \"-event_mask "+Mask+"\" throws NumberFormatException!***");
          usage();
        }
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -event_mask "+event_mask);
        //      } else if (arg.compareTo("-generate_statistics") == 0) {
        //      if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -generate_statistics");
        //      generate_statistics = true;
      } else if (arg.compareTo("-local") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -local");
        print_local = true;
      } else if (arg.compareTo("-no_yields") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -no_yields");
        no_yields = true;
      } else if (arg.compareTo("-threads") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -threads");
        print_threads = true;
      } else if (arg.compareTo("-run") == 0) {
        String Run = args[++i];
        try {
          run = Integer.parseInt(Run);
        } catch (NumberFormatException e) {
          System.out.println("\n***CommandLineOptions.processArgs() \"-run "+Run+"\" throws NumberFormatException!***");
          usage();
        }
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -run "+run);
      } else if (arg.compareTo("-tid") == 0) {
        String Index = args[++i];
        try {
          tid = Integer.parseInt(Index);
        } catch (NumberFormatException e) {
          System.out.println("\n***CommandLineOptions.processArgs() \"-tid "+Index+"\" throws NumberFormatException!***");
          usage();
        }
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -tid "+tid);
      } else if (arg.compareTo("-trace") == 0) {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -trace");
        print_trace = true;
      } else if (arg.compareTo("-verbose") == 0) {
        String Value = args[++i];
        try {
          verbose = Integer.parseInt(Value);
        } catch (NumberFormatException e) {
          System.out.println("\n***CommandLineOptions.processArgs() \"-verbose "+Value+"\" throws NumberFormatException!***");
          usage();
        }
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -verbose "+verbose);
      } else if (arg.compareTo("-debug") == 0) {
        String Value = args[++i];
        try {
          debug = Integer.parseInt(Value);
        } catch (NumberFormatException e) {
          System.out.println("\n***CommandLineOptions.processArgs() \"-debug "+Value+"\" throws NumberFormatException!***");
          usage();
        }
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() -debug "+debug);
      } else if (arg.charAt(0) != '-') {
        if(local_debug>=2)System.out.println("CommandLineOptions.processArgs() trace_filenames["+trace_file_index+"] = \""+arg+"\"");
        trace_filenames[trace_file_index++] = arg;
        if(singleton && trace_file_index > 1) {
          System.out.println("***CommandLineOptions.processArgs() multiple trace files specified! Only one allowed!***\n");
          usage();
        }
      } else {
        System.out.println("\n***Unknown command line options: \""+arg+"\"!***");
        usage();
      }
    }
    //    if(generate_statistics && event_mask == UNINITIALIZED) {
    //      System.out.println("\n***CommandLineOptions.processArgs() event_mask "+event_mask+" must be specified when generate_statistics is specified!***");
    //      usage();
    //    }
    if (run != -1 && application == null) {
      System.out.println("\n***CommandLineOptions.processArgs() -application must be specified when -run "+run+" is specified!***");
      usage();
    }
  }
  /**
   * How to use this program!
   */
  public void usage() {
    System.out.println("\nreadTraceFile usage\n"+
                       " Boolean options: option\n"+
                       "  Option               description\n"+
                       "  -aggregate           aggregate values\n"+
                       "  -aggregate_by_thread aggregate values where threads values broken out by VP ID\n"+
                       "  -events              tell hpm events collected\n"+
                       //                      "  -generate_statistics generate average, min, max, var, and std dev.\n"+
                       //                      "                       thread specified in -tid and events specified in -event_mask\n"+
                       "  -mids                tell method ids collected\n"+
                       "  -fullname            print MID's full name\n"+
                       "  -local               apply options to input trace files individually\n"+
                       "  -no_yields           consider only a thread switch that times out\n"+
                       "  -structure           of user-defined trace records\n"+
                       "  -threads             tell what threads were active\n"+
                       "  -trace               print trace.  Project on -tid and -event_mask\n"+
                       "\n"+
                       " Value options: option value\n"+
                       "  Option               Value  description\n"+
                       "  -application         String name of application\n"+
                       "  -debug               int    debug what is going on\n"+
                       "  -group_index         int    group number to watch (POWER4 architecture)\n"+
                       "  -event_mask          int    fields to focus on?  Only look at a field if its bit is on.  511=all fields\n"+
                       "  -run                 int    run to focus on. Constraint: -application must be specified!\n"+
                       "  -tid                 int    thread id to focus on. Constraint only one \n"+
                       "  -verbose             int    verbose about what is going on\n"+
                       "\n"+
                       " Value options: option\n"+
                       "  trace_filename       name of trace file.  If more than one trace file name is\n"+
                       "                       specified, combine all the records.\n"+
                       "\n");
    System.exit(-1);
  }

  /*
   * Used with generate statistics for a particular event.
   */
  private double[] average;             // running average
  private long[]   minimum;             // minimum value seen so far
  private double[] minimum_d;           // minimum value seen so far
  private int[]    minimum_x;           // index of minimum value seen so far
  private long[]   maximum;             // maximum value seen so far
  private double[] maximum_d;           // minimum value seen so far
  private int[]    maximum_x;           // index of maximum value seen so far
  private int      n_values;            // number of values seen so far
  private double[] sumOfX;              // running sum of values
  private double[] sumOfSquareOfX;      // running square of values
  private double[] variance;            // running variance 
  
  /**
   * reset statistics
   */
  static private final int NUM_VALUES = 9;
  static private final int NUM_EXTENDED_VALUES = NUM_VALUES+2;

  static public  final int P4_SLICE0              =  0; // ** group completed
  static public  final int P4_BASIC               =  2; // L1 dcache misses
  static public  final int P4_IFU                 =  3; // ** IFU events 
  static public  final int P4_ISU                 =  4; // stalls
  static public  final int P4_LSOURCE             =  5; // use 25 instead (suggested by Alex)
  static public  final int P4_ISOURCE             =  6; // use 27 instead
  static public  final int P4_LSU                 =  7; // 
  static public  final int P4_XLATE1              =  8; // ** I & D TLB miss rate
  static public  final int P4_XLATE2              =  9; // ** I & D SLB miss rate
  static public  final int P4_FPU1                = 14; // FPU
  static public  final int P4_FPU2                = 15; // FPU2
  static public  final int P4_IDU1                = 17; // 
  static public  final int P4_IDU2                = 17; // 
  static public  final int P4_ISU_RENAME          = 18; // ** I disp comp, Full resources
  static public  final int P4_ISU_QUEUES1         = 19; // ** queues full
  static public  final int P4_ISU_FLOW            = 20; // ** grp dispatch, FXU finish
  static public  final int P4_SERIALIZE           = 22; // 
  static public  final int P4_LSU_BUSY            = 23; // ** estimated load latency
  static public  final int P4_LSOURCE3            = 25; // ** Where Data comes from?
  static public  final int P4_ISOURCE3            = 27; // ** Where Instructions come from?
  static public  final int P4_FPU3                = 28; //
  static public  final int P4_FPU4                = 29; //
  static public  final int P4_FPU5                = 30; //
  static public  final int P4_FPU6                = 31; //
  static public  final int P4_FPU7                = 32; //
  static public  final int P4_FXU                 = 33; // FXU
  static public  final int P4_LSU_LMQ             = 34; // LMQ and tablewalk
  static public  final int P4_LSU_LOAD1           = 36; // L1 cache banks
  static public  final int P4_LSU_STORE1          = 37; //
  static public  final int P4_LSU7                = 39; //
  static public  final int P4_MISC                = 41; //
  static public  final int P4_BRANCH_ANALYSIS     = 55; // branch mispredict rate
  static public  final int P4_L1_AND_TLB_ANALYSIS = 56; // ** I and DTLB miss rates
  static public  final int P4_L2_ANALYSIS         = 57;
  static public  final int P4_L3_ANALYSIS         = 58;

  static public  final int P4_LSU_BUSY_INDEX = 9;
  static public  final int P4_LSU_LMQ_INDEX = 10;

  public void allocateStatistics() 
  {
    average        = new double[NUM_EXTENDED_VALUES];
    minimum        = new long[NUM_EXTENDED_VALUES];
    minimum_d      = new double[NUM_EXTENDED_VALUES];
    minimum_x      = new int[NUM_EXTENDED_VALUES];
    maximum        = new long[NUM_EXTENDED_VALUES];
    maximum_d      = new double[NUM_EXTENDED_VALUES];
    maximum_x      = new int[NUM_EXTENDED_VALUES];
    sumOfX         = new double[NUM_EXTENDED_VALUES];
    sumOfSquareOfX = new double[NUM_EXTENDED_VALUES];
    variance       = new double[NUM_EXTENDED_VALUES];
  }
  /**
   * reset statistics
   */
  public void resetStatistics() 
  {
    n_values  = 0;
    for (int i=0; i<NUM_EXTENDED_VALUES; i++) {
      minimum[  i]      = Long.MAX_VALUE;       
      minimum_d[i]      = Double.MAX_VALUE;     
      minimum_x[i]      = Integer.MAX_VALUE;
      maximum[  i]      = Long.MIN_VALUE;
      maximum_d[i]      = Double.MIN_VALUE;
      maximum_x[i]      = Integer.MIN_VALUE;
      average[  i]      = 0;                    
      sumOfX[i]         = 0;
      sumOfSquareOfX[i] = 0;
      variance[i]       = 0;
    }
  }
  /**
   * Generate statistics
   * TODO: compute standard deviation
   *
   * @param tr       trace counter record
   * @param index    record's index
   */
  public void generateStatistics(TraceCounterRecord tr, int index)
  {
    if (tr.tid == tid || (tr.tid == -tid && !no_yields)) {
      n_values++;
      if (group_index ==      P4_LSU_BUSY) loadLatency(tr, index);
      if (group_index == P4_LSU_LMQ) loadMissLatency(tr, index);
      for (int i=0; i<NUM_VALUES; i++) {
        int mask_index = event_mask_array[i];
        if ((event_mask & mask_index) == mask_index) {
          long value = tr.values[i];
          if (value > maximum[i]) { maximum[i] = value; maximum_x[i] = index; }
          if (value < minimum[i]) { minimum[i] = value; minimum_x[i] = index; }
          sumOfX[i]           += value;
          sumOfSquareOfX[i]   += value * value;
          average[i]           = sumOfX[i] / n_values;
          variance[i]          = (sumOfSquareOfX[i] / n_values) - (average[i] * average[i]);
          if(verbose>=2) System.out.println("generateStatistics("+index+") X "+value+" sumOfX "+sumOfX[i]+" sumOfX2 "+sumOfSquareOfX[i]+" avg "+average[i]+" var "+variance[i]);
        }
      }
    }
  }
  /*
   * Generate statistics for load latency.
   * Group 23 on Power4
   * 
   * @param tcr      trace counter record
   * @param index    record's index
   */
  private void loadLatency(TraceCounterRecord tcr, int index) 
  {
    // LRQ slot 0 valid / LRQ slot 0 allocated
    double value = tcr.values[5] / tcr.values[6];
    if (value > maximum_d[P4_LSU_BUSY_INDEX]) { 
      maximum_d[P4_LSU_BUSY_INDEX] = value; maximum_x[P4_LSU_BUSY_INDEX] = index; 
    }
    if (value < minimum_d[P4_LSU_BUSY_INDEX]) { 
      minimum_d[P4_LSU_BUSY_INDEX] = value; minimum_x[P4_LSU_BUSY_INDEX] = index; 
    }
    sumOfX[P4_LSU_BUSY_INDEX]           += value;
    sumOfSquareOfX[P4_LSU_BUSY_INDEX]   += value * value;
    average[P4_LSU_BUSY_INDEX]           = sumOfX[P4_LSU_BUSY_INDEX] / n_values;
    variance[P4_LSU_BUSY_INDEX]          = (sumOfSquareOfX[P4_LSU_BUSY_INDEX] / n_values) - 
      (average[P4_LSU_BUSY_INDEX] * average[P4_LSU_BUSY_INDEX]);
  }
  /*
   * Generate statistics for load miss latency.
   * Group 34 on Power4
   * 
   * @param tcr      trace counter record
   * @param index    record's index
   */
  private void loadMissLatency(TraceCounterRecord tcr, int index) 
  {
    // LMQ slot 0 valid / LMQ slot 0 allocated
    double value = tcr.values[3] / tcr.values[4];
    if (value > maximum_d[P4_LSU_LMQ_INDEX]) { maximum_d[P4_LSU_LMQ_INDEX] = value; maximum_x[P4_LSU_LMQ_INDEX] = index; }
    if (value < minimum_d[P4_LSU_LMQ_INDEX]) { minimum_d[P4_LSU_LMQ_INDEX] = value; minimum_x[P4_LSU_LMQ_INDEX] = index; }
    sumOfX[P4_LSU_LMQ_INDEX]           += value;
    sumOfSquareOfX[P4_LSU_LMQ_INDEX]   += value * value;
    average[P4_LSU_LMQ_INDEX]           = sumOfX[P4_LSU_LMQ_INDEX] / n_values;
    variance[P4_LSU_LMQ_INDEX]          = (sumOfSquareOfX[P4_LSU_LMQ_INDEX] / n_values) - 
      (average[P4_LSU_LMQ_INDEX] * average[P4_LSU_LMQ_INDEX]);
  }
  /**
   * Report generated statistics
   *
   * @param header trace header
   */
  public void reportGeneratedStatistics(TraceHeader header)
  {
    int tid_abs = (tid < 0?-tid:tid);
    System.out.println("statistics: thread "+tid_abs+": "+
                     header.threadName(tid_abs)+", n_values "+n_values);
    for (int i=0; i<NUM_VALUES; i++) {
      int index = event_mask_array[i];
      if ((event_mask & index) == index) {
        System.out.print(i+": "+header.short_event_names[i]);
        printStatistics(i);
      }
    }
    if        (group_index == P4_LSU_BUSY) {
      System.out.print(P4_LSU_BUSY_INDEX     +": est. load      latency  ");
      printStatisticsDouble(P4_LSU_BUSY_INDEX);
    } else if (group_index == P4_LSU_LMQ) {
      System.out.print(P4_LSU_LMQ_INDEX+": est. load miss latency  ");
      printStatisticsDouble(P4_LSU_LMQ_INDEX);
    }
    System.out.println();
  }
  /*
   * Code factoring
   */
  private void printStatistics(int i)
  {
    if (minimum[i] == Long.MAX_VALUE) {
      System.out.println(" undefined");
      return;
    }
    System.out.print(" average "+Utilities.threeDigitDouble(average[i]));
    //    System.out.print("  average "+average[i]);
    System.out.print(" ["+Utilities.format_long(minimum[i]));
    if (verbose>=1)System.out.print(" ("+minimum_x[i]+")");
    System.out.print(", "+Utilities.format_long(maximum[i]));
    if (verbose>=1)System.out.print(" ("+maximum_x[i]+")");
    double var = variance[i];
    System.out.print("] var "+Utilities.threeDigitDouble(var));
    //    System.out.print("] var "+Utilities.threeDigitDouble(var));
    System.out.print(" std dev "+Utilities.threeDigitDouble(Math.sqrt(var)));
    System.out.println();
  }
  /*
   * Code factoring
   */
  private void printStatisticsDouble(int i)
  {
    if (minimum_d[i] == Double.MAX_VALUE) {
      System.out.println(" undefined");
      return;
    }
    System.out.print(" average "+Utilities.threeDigitDouble(average[i]));
    //    System.out.print("  average "+average[i]);
    System.out.print(" ["+minimum_d[i]);
    if (verbose>=1)System.out.print(" ("+minimum_x[i]+")");
    System.out.print(", "+maximum_d[i]);
    if (verbose>=1)System.out.print(" ("+maximum_x[i]+")");
    double var = variance[i];
    System.out.print("] var "+Utilities.threeDigitDouble(var));
    //    System.out.print("] var "+Utilities.threeDigitDouble(var));
    System.out.print(" std dev "+Utilities.threeDigitDouble(Math.sqrt(var)));
    System.out.println();
  }
}
