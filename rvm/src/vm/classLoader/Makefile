#
# (C) Copyright IBM Corp. 2001
#
#$Id$

# author Bowen Alpern
# author Derek Lieber

include			$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:		$(GEN_FILES_DIR)/VM_AtomDictionary.java \
			$(GEN_FILES_DIR)/VM_TypeDictionary.java \
			$(GEN_FILES_DIR)/VM_FieldDictionary.java \
			$(GEN_FILES_DIR)/VM_MethodDictionary.java \
			$(GEN_FILES_DIR)/VM_InterfaceMethodSignatureDictionary.java \
			$(GEN_FILES_DIR)/VM_IntLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_FloatLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_LongLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_DoubleLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_StringLiteralDictionary.java \
			$(GEN_FILES_DIR)/VM_StringVector.java \
			$(GEN_FILES_DIR)/VM_TypeVector.java \
			$(GEN_FILES_DIR)/VM_FieldVector.java \
			$(GEN_FILES_DIR)/VM_MethodVector.java
			@print -n "(classloader templates expanded) "

$(GEN_FILES_DIR)/VM_AtomDictionary.java:	VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_AtomDictionary VM_Atom VM_Atom VM_Atom VM_Dictionary.template $(GEN_FILES_DIR)/VM_AtomDictionary.java

$(GEN_FILES_DIR)/VM_TypeDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_TypeDictionary VM_Atom VM_Type VM_Atom VM_Dictionary.template $(GEN_FILES_DIR)/VM_TypeDictionary.java

$(GEN_FILES_DIR)/VM_FieldDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_FieldDictionary VM_Triplet VM_Field VM_Triplet VM_Dictionary.template $(GEN_FILES_DIR)/VM_FieldDictionary.java

$(GEN_FILES_DIR)/VM_MethodDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_MethodDictionary VM_Triplet VM_Method VM_Triplet VM_Dictionary.template $(GEN_FILES_DIR)/VM_MethodDictionary.java

$(GEN_FILES_DIR)/VM_InterfaceMethodSignatureDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_InterfaceMethodSignatureDictionary VM_InterfaceMethodSignature int VM_InterfaceMethodSignature VM_Dictionary.template $(GEN_FILES_DIR)/VM_InterfaceMethodSignatureDictionary.java

$(GEN_FILES_DIR)/VM_IntLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_IntLiteralDictionary int int VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_IntLiteralDictionary.java

$(GEN_FILES_DIR)/VM_FloatLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_FloatLiteralDictionary int int VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_FloatLiteralDictionary.java

$(GEN_FILES_DIR)/VM_LongLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_LongLiteralDictionary long int VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_LongLiteralDictionary.java

$(GEN_FILES_DIR)/VM_DoubleLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_DoubleLiteralDictionary long int VM_Statics VM_Dictionary.template $(GEN_FILES_DIR)/VM_DoubleLiteralDictionary.java

$(GEN_FILES_DIR)/VM_StringLiteralDictionary.java: VM_Dictionary.template
			./InstantiateDictionaryTemplate VM_StringLiteralDictionary VM_Atom int VM_Atom VM_Dictionary.template $(GEN_FILES_DIR)/VM_StringLiteralDictionary.java

$(GEN_FILES_DIR)/VM_StringVector.java:	VM_Vector.template
			./InstantiateVectorTemplate VM_StringVector String VM_Vector.template $(GEN_FILES_DIR)/VM_StringVector.java

$(GEN_FILES_DIR)/VM_TypeVector.java:	VM_Vector.template
			./InstantiateVectorTemplate VM_TypeVector VM_Type VM_Vector.template $(GEN_FILES_DIR)/VM_TypeVector.java

$(GEN_FILES_DIR)/VM_FieldVector.java:	VM_Vector.template
			./InstantiateVectorTemplate VM_FieldVector VM_Field VM_Vector.template $(GEN_FILES_DIR)/VM_FieldVector.java

$(GEN_FILES_DIR)/VM_MethodVector.java:	VM_Vector.template
			./InstantiateVectorTemplate VM_MethodVector VM_Method VM_Vector.template $(GEN_FILES_DIR)/VM_MethodVector.java
