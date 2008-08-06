/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.harmony.lang;

/**
 * @author Evgueni Brevnov, Roman S. Bushmanov
 * @version $Revision: 1.1.6.4 $
 */
public interface RuntimePermissionCollection {

    RuntimePermission ACCESS_DECLARED_MEMBERS_PERMISSION = new RuntimePermission(
        "accessDeclaredMembers");

    RuntimePermission CRAETE_SECURITY_MANAGER_PERMISSION = new RuntimePermission(
        "createSecurityManager");

    RuntimePermission CREATE_CLASS_LOADER_PERMISSION = new RuntimePermission(
        "createClassLoader");

    RuntimePermission EXIT_PERMISSION = new RuntimePermission("exitVM");

    RuntimePermission GET_CLASS_LOADER_PERMISSION = new RuntimePermission(
        "getClassLoader");

    RuntimePermission GET_PROTECTION_DOMAIN_PERMISSION = new RuntimePermission(
        "getProtectionDomain");

    RuntimePermission GET_STACK_TRACE_PERMISSION = new RuntimePermission(
        "getStackTrace");

    RuntimePermission GETENV_PERMISSION = new RuntimePermission("getenv.*");

    RuntimePermission MODIFY_THREAD_GROUP_PERMISSION = new RuntimePermission(
        "modifyThreadGroup");

    RuntimePermission MODIFY_THREAD_PERMISSION = new RuntimePermission(
        "modifyThread");

    RuntimePermission QUEUE_PRINT_JOB_PERMISSION = new RuntimePermission(
        "queuePrintJob");

    RuntimePermission READ_FILE_DESCRIPTOR_PERMISSION = new RuntimePermission(
        "readFileDescriptor");

    RuntimePermission SET_CONTEXT_CLASS_LOADER_PERMISSION = new RuntimePermission(
        "setContextClassLoader");

    RuntimePermission SET_DEFAULT_UNCAUGHT_EXCEPTION_HANDLER_PERMISSION = new RuntimePermission(
        "setDefaultUncaughtExceptionHandler");

    RuntimePermission SET_FACTORY_PERMISSION = new RuntimePermission(
        "setFactory");

    RuntimePermission SET_IO_PERMISSION = new RuntimePermission("setIO");

    RuntimePermission SET_SECURITY_MANAGER_PERMISSION = new RuntimePermission(
        "setSecurityManager");

    RuntimePermission SHUTDOWN_HOOKS_PERMISSION = new RuntimePermission(
        "shutdownHooks");

    RuntimePermission STOP_THREAD_PERMISSION = new RuntimePermission(
        "stopThread");

    RuntimePermission WRITE_FILE_DESCRIPTOR_PERMISSION = new RuntimePermission(
        "writeFileDescriptor");    
}
