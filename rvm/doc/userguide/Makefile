config_file := $(shell . $(RVM_HOST_CONFIG) && echo $$USER_GUIDE_CONFIG)

HOST_JAVA := $(shell . $(RVM_HOST_CONFIG) && echo $$HOST_JAVA)
HOST_JAVAC := $(shell . $(RVM_HOST_CONFIG) && echo $$HOST_JAVAC)
HOST_REPOSITORIES := $(shell . $(RVM_HOST_CONFIG) && echo $$HOST_REPOSITORIES)

GENERATOR_DIR := $(RVM_ROOT)/rvm/src/tools/templateExpander
GENERATOR := GenerateFromTemplate

VM_OPTION_DIR        := $(RVM_ROOT)/rvm/src/vm/utility
VM_BOOLEAN_DAT_FILES := $(VM_OPTION_DIR)/BooleanOptions.dat
VM_VALUE_DAT_FILES   := $(VM_OPTION_DIR)/ValueOptions.dat
VM_DAT_FILES         := $(VM_BOOLEAN_DAT_FILES) $(JMTK_VALUE_DAT_FILES)

JMTK_OPTION_DIR        := $(RVM_ROOT)/rvm/src/vm/memoryManagers/JMTk/utility
JMTK_BOOLEAN_DAT_FILES := $(JMTK_OPTION_DIR)/BooleanOptions.dat
JMTK_VALUE_DAT_FILES   := $(JMTK_OPTION_DIR)/ValueOptions.dat
JMTK_DAT_FILES         := $(JMTK_BOOLEAN_DAT_FILES) $(JMTK_VALUE_DAT_FILES)

ADAPT_OPTION_DIR        := $(RVM_ROOT)/rvm/src/vm/adaptive/utility/
ADAPT_BOOLEAN_DAT_FILES := ${ADAPT_OPTION_DIR}/BooleanOptions.dat
ADAPT_VALUE_DAT_FILES   := ${ADAPT_OPTION_DIR}/ValueOptions.dat
ADAPT_DAT_FILES         := $(ADAPT_BOOLEAN_DAT_FILES) $(ADAPT_VALUE_DAT_FILES)

COMPILER_OPTION_DIR        := $(RVM_ROOT)/rvm/src/vm/compilers/utility
COMPILER_BOOLEAN_DAT_FILES := $(COMPILER_OPTION_DIR)/SharedBooleanOptions.dat
COMPILER_VALUE_DAT_FILES   := $(COMPILER_OPTION_DIR)/SharedValueOptions.dat
COMPILER_DAT_FILES         := $(COMPILER_BOOLEAN_DAT_FILES) $(COMPILER_VALUE_DAT_FILES)

BASE_OPTION_DIR        := $(RVM_ROOT)/rvm/src/vm/compilers/baseline
BASE_BOOLEAN_DAT_FILES := $(COMPILER_BOOLEAN_DAT_FILES) $(BASE_OPTION_DIR)/BooleanOptions.dat
BASE_VALUE_DAT_FILES   := $(COMPILER_VALUE_DAT_FILES) $(BASE_OPTION_DIR)/ValueOptions.dat
BASE_DAT_FILES         := $(BASE_BOOLEAN_DAT_FILES) $(BASE_VALUE_DAT_FILES)

OPT_OPTION_DIR := $(RVM_ROOT)/rvm/src/vm/compilers/optimizing/driver
OPT_BOOLEAN_DAT_FILES := $(COMPILER_BOOLEAN_DAT_FILES) \
			 ${OPT_OPTION_DIR}/BooleanOptions.dat
OPT_VALUE_DAT_FILES   := $(COMPILER_VALUE_DAT_FILES) \
			 ${OPT_OPTION_DIR}/ValueOptions.dat
OPT_DAT_FILES         := $(OPT_BOOLEAN_DAT_FILES) $(OPT_VALUE_DAT_FILES)

include $(config_file)
LATEX  ?= latex
DVIPS ?= dvips
BIBTEX ?= bibtex
MAKEINDEX ?= makeindex
HYPERLATEX ?= hyperlatex
PS2GIF ?= ps2gif

INPUT_FILES = 	userguide.tex \
		ack.tex \
		aosdetails.tex \
		basedetails.tex \
		cmdline.tex \
		codingstyle.tex \
		codingstyle-nonjava.tex \
		componentOverview.tex \
		debugging.tex \
		eclipse.tex \
		faq.tex \
		installation.tex \
		intro.tex \
		JMTk.tex \
		jni.tex \
		libraries.tex \
		optdetails.tex \
		performance.tex \
		preprocessor.tex \
		profiling.tex \
		regression.tex \
		running.tex \
		remark.tex \
		trademarks.tex \
		trademark-macros.tex \
		vmdetails.tex

GEN_INPUTS =	opt_options.tex adaptive_options.tex base_options.tex \
                jmtk_options.tex vm_options.tex

GENERATOR_FILES = ${GENERATOR}.class QuotedStringTokenizer.class \
		  UnterminatedStringException.class

TEX_FILES = $(INPUT_FILES) $(GEN_INPUTS) 

TEX_GEN_FILES = userguide.aux userguide.bbl userguide.blg userguide.dvi userguide.log userguide.lof userguide.toc userguide.idx userguide.ilg userguide.ind adaptive_options.aux opt_options.aux base_options.aux jmtk_options.tex

HTML_GRAPHICS = jtoc.gif stack.gif arch-AOSOverview.gif

# do not add more white spaces to the following line
NAV_GIFS=blank.gif next.gif previous.gif up.gif right.gif left.gif

HTML_DIR = HTML


# by default make the PostScript version
ps:		userguide.ps
userguide.ps:	userguide.dvi
		$(DVIPS) userguide -o

# make the PostScript and the HTML version
all:	userguide.ps html

# TeX the document
userguide.dvi:	$(TEX_FILES) main.bib
		$(LATEX) userguide
		$(BIBTEX) userguide
		$(MAKEINDEX) userguide
		$(LATEX) userguide
		$(LATEX) userguide

html:	userguide.tex $(TEX_FILES) $(HTML_GRAPHICS)
	rm -rf $(HTML_DIR)
	$(HYPERLATEX) userguide
	cp ${NAV_GIFS} ${HTML_GRAPHICS} ${HTML_DIR}
	cd ${HTML_DIR} && ln -sf userguide.html index.html
	chmod -Rf u+rwX ${HTML_DIR}
	chmod -Rf g+rwX ${HTML_DIR}

%.gif:	%.ps
	${PS2GIF} -res 50 $< $@

.INTERMEDIATE:	${GENERATOR}.java

clean:;	rm -f 	${TEX_GEN_FILES} $(GEN_INPUTS) ${GENERATOR_FILES} userguide.dvi

dist-clean:	clean
		rm -f userguide.ps
		rm -rf ${HTML_DIR}

distrib:;	mkdir -p $(distdir)/ps
		-rm -f $(distdir)/ps/userguide.ps
		-rm -rf $(distdir)/HTML
		-cp userguide.ps $(distdir)/ps
		-cp -r ${HTML_DIR} $(distdir)
		-chmod -Rf g+rwX ${distdir}
		-chmod -Rf o+rX ${distdir}



${GENERATOR}.java:	${GENERATOR_DIR}/${GENERATOR}.java
			cp $< $@

${GENERATOR_FILES}:	${GENERATOR}.java
			$(HOST_JAVAC) -g -d . $<

vm_options.tex:		${GENERATOR_FILES} options.template $(VM_DAT_FILES)
			$(HOST_JAVA) ${GENERATOR} options.template \
			  vm_options.tex \
			  BOOLEAN_DAT_FILES="$(VM_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(VM_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:vm" \
			  USE_OPT_LEVELS="NO" \
			  OPTION_TYPE="VM"

jmtk_options.tex:	${GENERATOR_FILES} options.template $(JMTK_DAT_FILES)
			$(HOST_JAVA) ${GENERATOR} options.template \
			  jmtk_options.tex \
			  BOOLEAN_DAT_FILES="$(JMTK_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(JMTK_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:gc" \
			  USE_OPT_LEVELS="NO" \
			  OPTION_TYPE="JMTK"

adaptive_options.tex:	${GENERATOR_FILES} options.template $(ADAPT_DAT_FILES)
			$(HOST_JAVA) ${GENERATOR} options.template \
			  adaptive_options.tex \
			  BOOLEAN_DAT_FILES="$(ADAPT_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(ADAPT_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:aos" \
			  USE_OPT_LEVELS="NO" \
			  OPTION_TYPE="ADAPTIVE"

opt_options.tex:	${GENERATOR_FILES} options.template ${OPT_DAT_FILES}
			$(HOST_JAVA) ${GENERATOR} options.template \
			  opt_options.tex \
			  BOOLEAN_DAT_FILES="$(OPT_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(OPT_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:opt" \
			  USE_OPT_LEVELS="YES" \
			  OPTION_TYPE="OPTIMIZE"

base_options.tex:	${GENERATOR_FILES} options.template $(BASE_DAT_FILES)
			$(HOST_JAVA) ${GENERATOR} options.template \
			  base_options.tex \
			  BOOLEAN_DAT_FILES="$(BASE_BOOLEAN_DAT_FILES)" \
			  VALUE_DAT_FILES="$(BASE_VALUE_DAT_FILES)" \
			  OPTPREFIX="-X:base" \
			  USE_OPT_LEVELS="NO" \
			  OPTION_TYPE="BASELINE"

