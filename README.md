## Jikes Research Virtual Machine

Jikes RVM (Research Virtual Machine) provides a flexible open testbed to prototype virtual machine technologies and experiment with a large variety of design alternatives. The system is licensed under the [EPL](http://www.eclipse.org/legal/epl-v10.html), an [OSI](http://www.opensource.org/) approved license. Jikes RVM runs on IA32 32 bit (64 bit support is work in progress) and PowerPC (big endian only).

A distinguishing characteristic of Jikes RVM is that it is implemented in the Java™ programming language and is self-hosted i.e., its Java code runs on itself without requiring a second virtual machine. Most other virtual machines for the Java platform are written in native code (typically, C or C++). A Java implementation provides ease of portability, and a seamless integration of virtual machine and application resources such as objects, threads, and operating-system interfaces.

More information is available at our [website](http://www.jikesrvm.org).

## Building

You'll need

* a JDK (>= 6 and < 9). Building with JDK 9 or later is currently [not supported](https://xtenlang.atlassian.net/projects/RVM/issues/RVM-1136).
* Ant (>= 1.7) with optional tasks (e.g. for JUnit and JavaDoc)
* GCC with multilibs
* Bison
* an internet connection during the first build to download [GNU Classpath](http://www.gnu.org/software/classpath/) and other components
* (tests only) the Perl library for Proc::ProcessTable (e.g. the package libproc-processtable-perl on Debian-based distributions or perl-Proc-ProcessTable on CentOS)

Please see the [user guide](http://www.jikesrvm.org/UserGuide/) for more details.

## Need support?

Please ask on the researchers [mailing list](http://www.jikesrvm.org/MailingLists/).

## Bug reports

If you want to report a bug, please see [this page on our website](http://www.jikesrvm.org/ReportingBugs/).

## Contributions

See the [contributions page](http://www.jikesrvm.org/Contributions/) for details. 

The short version:

* Contributions are licensed under EPL and require a Contributor License Agreement. You keep your copyright.
* You can send us patches or use pull requests. Send patches to the [core mailing list](mailto:jikesrvm-core@lists.sourceforge.net).
* It is ok to test on one platform only (e.g. only on IA32).

## Work based on Jikes RVM

For work based on Jikes RVM, see our [publications page](http://www.jikesrvm.org/Resources/Publications/). Bear in mind that this page is almost always incomplete. You can help rectify this by submitting pull requests or patches against the [website repository](https://github.com/JikesRVM/jikesrvm.github.io/). Additionally, some authors have decided to publish the code for their papers in the [research archive at Sourceforge](http://sourceforge.net/p/jikesrvm/research-archive/?limit=250).

## Other GitHub repositories that are based on Jikes RVM

There are a lot of projects on GitHub that are based on Jikes RVM but aren't forks of this repository. If you want your project listed (or not listed) here, please send us a patch or pull request.

The list is divided into repositories with code changes and additional information (description, README, paper, thesis, ...), repositories that have code changes but no (known) support material and repositories where it's unclear if there are actually any code changes against the base Jikes RVM.

Projects with code changes and additional information
* [HeraJVM](https://github.com/rmcilroy/HeraJVM), a JVM implementation for the Cell processor. See the thesis [Using program behaviour to exploit heterogeneous multi-core processors ](http://theses.gla.ac.uk/1755/) by Ross McIlroy or the paper "Hera-JVM: A Runtime System for Heterogeneous Multi-Core Architectures".
* [Metacircular Research Platform](https://github.com/codehaus/mrp) by Ian Rogers. We're trying to merge most of the changes from MRP, with the notable exception of Apache Harmony support (because it's dead upstream) and Windows support (because that is implemented with Apache Harmony in MRP). If you want to help, see the [MRP merge status page](http://www.jikesrvm.org/MergeStatusOfMRPChangesets/) and ask on the core mailing list first.
* [A mark-compact related GC implementation](https://github.com/ampasowa/jikesrvm)
* [Cost-aware Parallel GC](https://github.com/junjieqian/CAP-GC) by Junjie Qian
* [Deutsch-Bobrow-GC](https://github.com/NikolausDemmel/pgc-jikesrvm)
* [GPU garbage collection](https://github.com/preames/gpu-garbage-collection) code from the paper "GPUs as an Opportunity for Offloading Garbage Collection" by Maas et al. from ISMM 2012.
* [Reference-counting Immix](https://github.com/rifatshahriyar/rcimmix) by Rifat Shahriyar et al. See the paper "Taking Off the Gloves with Reference Counting Immix" (OOPSLA'13). There's also a conservative version of the collector and an associated paper in OOPSLA'14. Patches to produce both collector implementations from a base Jikes RVM can be found in Rifat's [patch repository](https://github.com/rifatshahriyar/rcimmixpatch). If you want to help to get the code into mainline Jikes RVM, contact us on the core mailing list. The relevant issues are [RVM-1061](https://xtenlang.atlassian.net/browse/RVM-1061) for RCImmix and [RVM-1085](https://xtenlang.atlassian.net/browse/RVM-1085) for conservative RC Immix.
* [Sapphire garbage collector](https://github.com/perlfu/sapphire). See that repository's readme for more information. If you want to help get the collector into mainline, contact us on the core mailing list. The associated issue is [RVM-893](https://xtenlang.atlassian.net/browse/RVM-893).
* [The MMTk tutorial collector implemented](https://github.com/Elizaveta239/MMTk-gc)
* [A tool related to Pacer](https://github.com/jaggerlink/cs356).
* [ByteSTM](https://github.com/mohamedin/bytestm). See the paper "ByteSTM: Virtual Machine-level Java Software Transactional Memory" by Mohamedin et al.
* [Laminar](https://github.com/ut-osa/laminar). See the paper "Laminar: Practical Fine-Grained Decentralized Information Flow Control" by Roy et al. in PLDI'09. The changes can be found in the form of patches in the Jikes RVM [research archive entry](http://sourceforge.net/p/jikesrvm/research-archive/26/).
* [A minimal implementation of causal profiling for Java applications running on Jikes RVM](https://github.com/alanweide/coff)
* [Velodrome: a sound and complete dynamic atomicity checker for multithreaded programs](https://github.com/palmskog/velodrome)
* [DoubleChecker: Efficient Sound and Precise Atomicity Checking](https://github.com/palmskog/doublechecker-single-run)
* [Java Object Environment](https://github.com/joekoolade/JOE)

Projects with code changes but without any additional information
* [https://github.com/vilay/check](https://github.com/vilay/check)
* [https://github.com/gdeOo/ditto](https://github.com/gdeOo/ditto)
* [https://github.com/Scharrels/e-strobe](https://github.com/Scharrels/e-strobe)
* [https://github.com/leizhao833/jikes-bmm](https://github.com/leizhao833/jikes-bmm)
* [https://github.com/josemsimao/jikesrvm](https://github.com/josemsimao/jikesrvm)

Projects that may or may not have any changes compared to a released version of Jikes RVM
* [https://github.com/hkrish4/ConcurrentGC](https://github.com/hkrish4/ConcurrentGC)
* [https://github.com/zitterbewegung/cs398](https://github.com/zitterbewegung/cs398)
* [https://github.com/danyaberezun/JBPractise](https://github.com/danyaberezun/JBPractise)
* [https://github.com/sirinath/jikesrvm](https://github.com/sirinath/jikesrvm)
* [https://github.com/yanxiaoliang/jikesrvm](https://github.com/yanxiaoliang/jikesrvm)
* [https://github.com/Betula-L/jikesrvm-3.1.3-hg_DrFinder](https://github.com/Betula-L/jikesrvm-3.1.3-hg_DrFinder)
* [https://github.com/CodeOffloading/JikesRVM-CCO](https://github.com/CodeOffloading/JikesRVM-CCO)
* [https://github.com/ravifreek63/RVMJikes](https://github.com/ravifreek63/RVMJikes)
* [https://github.com/perpetualcoder/SMPGC](https://github.com/perpetualcoder/SMPGC)

## History

The project migrated from Subversion to Mercurial and from Mercurial to Git. Certain older changes are not contained in this repository. If you need access to these changes, you can browse the old repositories at [SourceForge](http://sourceforge.net/p/jikesrvm). The relevant parts of the old repositories are also mirrored on GitHub (see below).

The last commit in the Mercurial repository is commit #11358 (hg commit id d4ced37a7a0d) from Tue, 08 Sep 2015 13:55:48 +0200. The matching commit in this Git repository has the commit id 871ee0e826c161c8cb99bba7280dced6da850779.

The last interesting commit in the Subversion repository is commit #16061 (Move assertion on heavy lock state to within lock mutex to avoid possible race with inflation code). The matching commit in this Git repository has the commit id 164e4f465640364da0b135b78307e8cf1de8a070. The very last commit in the Subversion repository is commit #16068 (disable runs on piccolo until we get hg working on AIX.).

Mirrors of the old repositories on GitHub:

* The main code is mirrored at https://github.com/JikesRVM/mirror-historical-svn-jikesrvm . Please note that the mirror includes old branches which aren't present in this repository.
* Tuningfork is mirrored at https://github.com/JikesRVM/mirror-historical-svn-tuningfork
* Cattrack, an application for tracking test results that the project used in the past, is mirrored at https://github.com/JikesRVM/mirror-historical-svn-cattrack
