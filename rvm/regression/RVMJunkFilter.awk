#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#
BEGIN { 
    yes = "yes"
    no = "no"
    counts = no
    gc_mess = no
    report_crap = no 
    kill_next = no
    boot_image_junk = no
    verbose_trap = no
    memory_crap = no
}

#
# RVM startup messages
#
/^small heap = [0-9]*, large heap = [0-9]*$/ { next }
/^vm: booting$/ { next }
/^VM_RuntimeCompiler: boot.*$/ { next }
/NativeDaemonProcessor not created/ { next }
/VM_RuntimeCompiler \([a-zA-Z ]*\): ignoring command line argument/ { next }
/VM_RuntimeCompiler\(baseline\): Ignoring unrecognized argument/ { next }
/.*Illegal option specification/ { next }
/.*must be specified as a name-value pair in the form of option=value/ { next }
/IA32 linux build/ { next }


#
# JNI crap
#
/^method causing resize/ { next }
/^Growing native stack before entry/ { next }
/Number of threads found stuck in native code/ { next }

#
# GC system messages
#
/^polling caused gc - returning gc and retry$/ { next }
/^0x[0-9a-f]*:REF=0x[0-9a-f]* \(REF OUTSIDE OF HEAP\)/ { next }
/^getNextReferenceAddress: bridgeTarget/ { next }
/^GC Summary:/ { next }
/^GC Warning:/ { next }
/^GC Message:/ { next }
/^validRef: REF outside heap, ref = [0-9x]*$/ { kill_next = yes; next }

kill_next==yes { kill_next = no; next }

/Invalid ref reported while scanning stack/ { gc_mess=yes; next }
/GC stats: Mark Sweep Collector/ { gc_mess=yes; next }

/--- METHOD ---/ && gc_mess==yes { next }
/fp = [0-9a-fx]* ip = [0-9a-fx]*/ && gc_mess==yes { next }
/Heap Size [0-9]*/ && gc_mess==yes { next }
/<GC [0-9]*>/ && gc_mess==yes { next }
/[0-9]* Collections: avgTime [0-9]*/ && gc_mess==yes { next }
/GC Summary:/ && gc_mess==yes { next }

gc_mess==yes { gc_mess = no }

#
# non-fatal compiler errors
#
/Optimizing compiler \(via recompileWithOpt\): can't optimize/ { next }


#
# compilation timing report
#
/Compilation Subsystem Report/ { report_crap=yes; next }

/TOTAL COMPILATION TIME/ && report_crap==yes { report_crap=no; next }

report_crap==yes { next }

#
# memory usage report
#
/Object Demographics by type/ { memory_crap=yes; next }

/^[0-9() KMb]*TOTAL$/ { memory_crap=no; next }

memory_crap==yes { next }


#
# scheduler messages
#
/VM_Scheduler.boot: NativeDaemonProcessor NOT CREATED/ { next }


#
# adaptive system messages
#
/AOS: In non-adaptive mode; controller thread exiting./ { next }

/Method counts: A total of [0-9.]* times counted/ { counts=yes; next }
/[0-9.]* \([0-9.]*%\)/ && counts==yes { next }
/[:blank:]*BOOT/ && counts==yes { next }

#
# initial verbose boot image messages
#
/vm: loading from "/ { boot_image_junk = yes; next }
/   ...etc.../ && boot_image_junk==yes { boot_image_junk = no; next }
boot_image_junk==yes { next }

#
# verbose lintel trap messages
#
/vm: trap [0-9]* \(/ { verbose_trap = yes; next }
/fp7 0x[0-9a-f]*/ && verbose_trap==yes { verbose_trap = normal; next }
verbose_trap==yes { next }

/vm: normal trap/ && verbose_trap==normal { next }
/failing instruction : / && verbose_trap==normal { next }
/Trap code is 0x[0-9a-fA-F]*/ && verbose_trap==normal { next }
/Set vmr_fp to 0x[0-9a-f]*/ && verbose_trap==normal { 
  verbose_trap = no; next 
}

#
# GNU Classpath debugging verbosity
#
/java.lang.Double.initIDs/ { next }

#
# print everything else
#
/.*/
