/*
 * (C) Copyright IBM Corp. 2001
 */
/*$Id$
 * This native code is shared between native implementation
 * for memory.java and register.java
 * It converts between register number and symbolic name used in JVM
 * @author Ton Ngo (1/22/98)
 */

#include <sys/reg.h>
#include <sys/ptrace.h>
#include <sys/ldr.h>
#include <sys/errno.h>  

extern int jvmFP;
extern int jvmSP;
extern int jvmTI;
extern int jvmJTOC;

extern char **GPR_names; 
extern int    GPR_count; 
extern char **FPR_names;
extern int    FPR_count; 
  

/* convert a decimal register number to a string in RVM convention */
char *jvm_regname(char *regname, int reg)
{
  strcpy(regname,""); 
  
  /* for the general purpose registers, in the range of 0-31 */
  if ((reg>=GPR0) && (reg<=GPR0+GPR_count))
    strcpy(regname,GPR_names[reg-GPR0]);
    
  /* for the floating point registers, in the range of 256-287 */
  if ((reg>=FPR0) && (reg<=FPR0+FPR_count))
    strcpy(regname,FPR_names[reg-FPR0]);
  
  /* for the special purpose RS6000 registers */
  switch (reg) {
  case IAR:      strcpy(regname,"IP"); break;
  case MSR:      strcpy(regname,"MSR"); break; 
  case CR:       strcpy(regname,"CR"); break;   
  case LR:       strcpy(regname,"LR"); break;   
  case CTR:      strcpy(regname,"CTR"); break;  
  case XER:      strcpy(regname,"XER"); break;  
  case MQ:       strcpy(regname,"MQ"); break;   
  case TID:      strcpy(regname,"TID"); break;  
  case FPSCR:    strcpy(regname,"FPSCR"); break;
  case FPINFO:   strcpy(regname,"FPINFO"); break;
  case FPSCRX:   strcpy(regname,"FPSCRX"); break;
  }
  
  /* for the special JVM register mapping */
  if (reg==jvmFP)
    strcpy(regname,"FP"); 
  else if (reg==jvmJTOC)
    strcpy(regname,"JTOC"); 
  else if (reg==jvmSP)
    strcpy(regname,"SP"); 

  return regname;
}

/* convert a decimal register number to string */
char *reg_itoa(char *regname, int reg)
{
  /* First try to give it a meaningful name in the RVM convention */
  jvm_regname(regname, reg);

  /* if nothing, make it a general purpose or floating point register */
  if (strcmp(regname,"")==0) {
    if ((reg>=FPR0) && (reg<=FPR31))
      sprintf(regname,"fr%d", (reg-FPR0));  
    else
      sprintf(regname,"r%d", reg);
  }
  return regname;
}

/* convert a register name as string to decimal number */
int reg_atoi(char *reg_string)
{
  int reg, i;

  reg = -1;

  /* first try the special purpose names */
  if (strcasecmp(reg_string,"ip")==0) {
    reg = IAR;
  } else if (strcasecmp(reg_string,"msr")==0) {
    reg = MSR;
  } else if (strcasecmp(reg_string,"cr")==0) {
    reg = CR;
  } else if (strcasecmp(reg_string,"ctr")==0) {
    reg = CTR;
  } else if (strcasecmp(reg_string,"xer")==0) {
    reg = XER;
  } else if (strcasecmp(reg_string,"mq")==0) {
    reg = MQ;
  } else if (strcasecmp(reg_string,"tid")==0) {
    reg = TID;
  } else if (strcasecmp(reg_string,"fpscr")==0) {
    reg = FPSCR;
  } else if (strcasecmp(reg_string,"fpinfo")==0) {
    reg = FPINFO;
  } else if (strcasecmp(reg_string,"fpscrx")==0) {
    reg = FPSCRX;
  } else if (strcasecmp(reg_string,"fp")==0) {
    reg = jvmFP;
  } else if (strcasecmp(reg_string,"sp")==0) {
    reg = jvmSP;
  } else if (strcasecmp(reg_string,"lr")==0) {
    reg = LR;
  } else if (strcasecmp(reg_string,"jtoc")==0 || 
	     strcasecmp(reg_string,"toc")==0) {
    reg = jvmJTOC;
  }  else if (strcmp(reg_string,"")==0 || strcmp(reg_string,"")==0) {
    reg = 0;
  } else {
    /* then check the general purpose names */
    for (i=0; i<GPR_count; i++) {
      if (strcasecmp(reg_string,GPR_names[i])==0) {
	reg = i;
	break;
      }
    }
    /* then check the floating point names */
    for (i=0; i<FPR_count; i++) {
      if (strcasecmp(reg_string,FPR_names[i])==0) {
	reg = i+FPR0;
	break;
      }
    }    
  }
  
  /* last resort */
  if (reg==-1) {
    reg = atoi(reg_string);
    if (!isdigit(reg_string[0]) || reg>287 ) {
      printf("invalid register name: %s\n", reg_string);
      return -1;
    }
  }


  /* printf(" register mapped: %d \n", reg); */
  return reg;
}
