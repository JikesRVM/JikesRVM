#!/bin/sh

DEST_DIR=$1

# make sure we have a repository to use
if [ x$RVM_ROOT = x ]; then
  echo Please set your RVM_ROOT variable
  exit -1
fi


# should tables of inherited stuff be stripped?
STRIP_FIELDS=0
STRIP_CLASSES=0
if [ x$2 = xboth -o x$2 = xfields ]; then
  STRIP_FIELDS=1
fi
if [ x$2 = xboth -o x$2 = xclasses ]; then
  STRIP_CLASSES=1
fi


# make build directory
export RVM_BUILD=$DEST_DIR/tmp_build
$RVM_ROOT/rvm/bin/jconfigure -D "BUILD_RVMRT_JAR=1" FullAdaptiveSemispace < /dev/null
cd $RVM_BUILD
./jbuild -nolink -nobooter


# pick up env
. $RVM_BUILD/jbuild.environment


# copy files to (1) get a subset of them and (2) strip out INSTRUCTION
SOURCES="RVM.classes RVM.scratch"
CODE_DIR=$DEST_DIR/sed_processed_java

mkdir -p $CODE_DIR
echo $CODE_DIR/* | xargs rm -rf

for d in $SOURCES; do
  # be in dir such that pathnames produced by find match package names
  cd $RVM_BUILD/$d
  for f in `find . -name \*.java`; do
    # copy only files compiled for this build
    if [ -e `dirname $f`/`basename $f java`class ]; then
      mkdir -p $CODE_DIR/`dirname $f`
      # strip INSTRUCTION typedef (someday, maybe we'll write in Java :)
      $SED s/\\\<INSTRUCTION\\\>/int/g $f > $CODE_DIR/$f
    fi
  done
done

echo -n "(sources processed) "

# run javadoc
cd $CODE_DIR

PACKAGES=
for _d in `find . -type d`; do
  if [ `find $_d -type f -maxdepth 1 -name '*.java' | wc -l` != 0 ]; then
    PACKAGES="$PACKAGES `echo $_d | $SED s@^\./\*@@g | $SED s@/@.@g`"
  fi
done

rm -f ../javadoc.out
find . -name '*.java' -maxdepth 1 -type f | xargs -t ${HOST_JAVADOC} -private -author -classpath $RVM_BUILD/RVM.classes/:$RVM_BUILD/RVM.classes/rvmrt.jar -d $DEST_DIR $PACKAGES >> ../javadoc.out 2>&1

echo -n "(javadoc complete) "

# no more need for code dir or build dir
# rm -rf $CODE_DIR $RVM_BUILD

# post-process if desired
cd $DEST_DIR

if [ $STRIP_FIELDS -eq 1 ]; then
    for f in `find . -name '*.html'`; do
      if [ `uname` = Linux ]; then
        TMP=`mktemp /tmp/strip.XXXXXX`
      else
        TMP=./xxx
      fi
      awk '
        BEGIN { discard_table = 0; find_table = 0; }
        /<A NAME="fields_inherited_from_class/ { find_table = 1; }
        find_table==1 && /<TABLE/ { discard_table = 1; }
        discard_table==0 { print $0; }
        discard_table==1 { }
        discard_table==1 && /<\/TABLE/ { find_table = 0; discard_table = 0; }
      ' $f > $TMP
      mv $TMP $f
    done
fi

if [ $STRIP_CLASSES -eq 1 ]; then
    for f in `find . -name '*.html'`; do
      if [ `uname` = Linux ]; then
        TMP=`mktemp /tmp/strip.XXXXXX`
      else
        TMP=./xxx
      fi
      awk '
        BEGIN { discard_table = 0; find_table = 0; }
        /<A NAME="inner_classes_inherited_from_class/ { find_table = 1; }
        find_table==1 && /<TABLE/ { discard_table = 1; }
        discard_table==0 { print $0; }
        discard_table==1 { }
        discard_table==1 && /<\/TABLE/ { find_table = 0; discard_table = 0; }
      ' $f > $TMP
      mv $TMP $f
    done
fi

if [ $STRIP_CLASSES -eq 1 -o $STRIP_FIELDS -eq 1 ]; then
  echo -n "(postprocessing done) "
fi

echo
