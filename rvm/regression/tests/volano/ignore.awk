BEGIN { 
    yes = "yes"
    no = "no"
    counts = no
    gc_mess = no
    report_crap = no 
    kill_next = no
}

#
# RVM startup messages
#
/^small heap = [0-9]*, large heap = [0-9]*$/ { next }
/^vm: booting$/ { next }
/^VM_RuntimeCompiler: boot.*$/ { next }
/NativeDaemonProcessor not created/ { next }
/VM_RuntimeCompiler \([a-zA-Z ]*\): ignoring command line argument/ { next }
#
# JNI crap
#
/^method causing resize/ { next }
/^Growing native stack before entry/ { next }
/^NewString: created/ { next }

#
# GC system messages
#
/^validRef: REF outside heap, ref = [0-9x]*$/ { kill_next = yes; next }
/^0x[0-9a-f]*:REF=0x[0-9a-f]* \(REF OUTSIDE OF HEAP\)/ { next }
/^getNextReferenceAddress: bridgeTarget/ { next }

kill_next==yes { kill_next = no; next }

/Invalid ref reported while scanning stack/ { gc_mess=yes; next }
/GC stats: Mark Sweep Collector/ { gc_mess=yes; next }

/--- METHOD ---/ && gc_mess==yes { next }
/fp = [0-9a-fx]* ip = [0-9a-fx]*/ && gc_mess==yes { next }
/Heap Size [0-9]*  Large Object Heap Size [0-9]*/ && gc_mess==yes { next }
/[0-9]* Collections: avgTime [0-9]*/ && gc_mess==yes { next }

gc_mess==yes { gc_mess = no }

#
# compiler junk
#
/^Use without def for [a-z0-9]*\(GUARD\) in/ { next }

#
# compilation timing report
#
/Compilation Subsystem Report/ { report_crap=yes; next }

/TOTAL COMPILATION TIME/ && report_crap==yes { report_crap=no; next }

report_crap==yes { next }

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
# elapsed time can vary, so remove it
#
/^Elapsed time       =/ { next }

#
# hostname and port can vary, so normalize (where the .expected was taken)
#
/^Loading server properties from .\/properties.txt[.0-9]*/ {
 print "Loading server properties from ./properties.txt."; next
}

/^[a-z0-9.]*.watson.ibm.com \(.*\) VolanoChatPro - unlimited connections.$/ {
 print "rios2.watson.ibm.com (9.2.251.90) VolanoChatPro - unlimited connections."
 next
}

#
# people produce all kinds of blanks lines 
#
/^$/ { next }

#
# print everything else
#
/.*/
