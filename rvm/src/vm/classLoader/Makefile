#
# (C) Copyright IBM Corp. 2001
#
#$Id$

# @author Bowen Alpern
# @author Derek Lieber

include			$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:		$(GEN_FILES_DIR)/VM_TypeDictionary.java \
			$(GEN_FILES_DIR)/VM_InterfaceMethodSignatureDictionary.java \
			$(GEN_FILES_DIR)/VM_IntLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_FloatLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_LongLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_DoubleLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_StringLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_TypeVector.java \
			$(GEN_FILES_DIR)/VM_FieldVector.java \
			$(GEN_FILES_DIR)/VM_FieldReferenceVector.java \
			$(GEN_FILES_DIR)/VM_MethodVector.java
			@print -n "(classloader templates expanded) "

$(GEN_FILES_DIR)/VM_TypeDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM.classloader VM_TypeDictionary VM_Atom VM_Type VM_Atom VM_Dictionary.template $(GEN_FILES_DIR)/VM_TypeDictionary.java

$(GEN_FILES_DIR)/VM_InterfaceMethodSignatureDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM.classloader VM_InterfaceMethodSignatureDictionary VM_InterfaceMethodSignature int VM_InterfaceMethodSignature VM_Dictionary.template $(GEN_FILES_DIR)/VM_InterfaceMethodSignatureDictionary.java

$(GEN_FILES_DIR)/VM_IntLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM VM_IntLiteralDictionary int int com.ibm.JikesRVM.VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_IntLiteralDictionary.java

$(GEN_FILES_DIR)/VM_FloatLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM VM_FloatLiteralDictionary int int com.ibm.JikesRVM.VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_FloatLiteralDictionary.java

$(GEN_FILES_DIR)/VM_LongLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM VM_LongLiteralDictionary long int com.ibm.JikesRVM.VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_LongLiteralDictionary.java

$(GEN_FILES_DIR)/VM_DoubleLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM VM_DoubleLiteralDictionary long int com.ibm.JikesRVM.VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_DoubleLiteralDictionary.java

$(GEN_FILES_DIR)/VM_StringLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate com.ibm.JikesRVM VM_StringLiteralDictionary com.ibm.JikesRVM.classloader.VM_Atom int com.ibm.JikesRVM.classloader.VM_Atom VM_Dictionary.template $(GEN_FILES_DIR)/VM_StringLiteralDictionary.java

$(GEN_FILES_DIR)/VM_TypeVector.java:	VM_Vector.template
			./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_TypeVector VM_Type VM_Vector.template $(GEN_FILES_DIR)/VM_TypeVector.java

$(GEN_FILES_DIR)/VM_FieldVector.java:	VM_Vector.template
			./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_FieldVector VM_Field VM_Vector.template $(GEN_FILES_DIR)/VM_FieldVector.java

$(GEN_FILES_DIR)/VM_FieldReferenceVector.java:	VM_Vector.template
			./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_FieldReferenceVector VM_FieldReference VM_Vector.template $(GEN_FILES_DIR)/VM_FieldReferenceVector.java

$(GEN_FILES_DIR)/VM_MethodVector.java:	VM_Vector.template
			./InstantiateVectorTemplate com.ibm.JikesRVM.classloader VM_MethodVector VM_Method VM_Vector.template $(GEN_FILES_DIR)/VM_MethodVector.java
