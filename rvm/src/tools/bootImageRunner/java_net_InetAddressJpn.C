#include <jni.h>

#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <netdb.h>

#ifdef __linux__
#include <netinet/in.h>
#include <linux/net.h>
#include <rpc/types.h>
#else
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/param.h>
#include <netinet/tcp.h>
#endif

#include "java_net_InetAddressJpn.h"

#ifdef __i386__
#define MANGLE32(i) ({ unsigned int r = 0; r |= (i&0xFF)<<24; r |= (i&0xFF00)<<8; r |= (i&0xFF0000)>>8; r |= (i&0xFF000000)>>24; r; })
#define MANGLE16(i) ({ unsigned short r = 0; r |= (i&0xFF)<<8; r |= (i&0xFF00)>>8; r; })
#else
#define MANGLE32(x) x
#define MANGLE16(x) x
#endif

/*
 * Class:     java_net_InetAddressJpn
 * Method:    getAliasesByNameImpl
 * Signature: (Ljava/lang/String;)[Ljava/net/InetAddress;
 */
JNIEXPORT jobjectArray JNICALL 
Java_java_net_InetAddressJpn_getAliasesByNameImpl(
		    JNIEnv *env, 
		    jclass type, 
		    jstring hostname) 
{
  int i;
  jboolean no = 0;
  jboolean yes = 1;

  int len = env->GetStringLength( hostname );
  const jchar *chars = env->GetStringChars(hostname, &yes);
  char *bytes = new char[ len+1 ];
  bytes[len] = '\0';
  for(int c = 0; c < len; c++) 
    bytes[c] = (char) chars[c];

#ifdef __linux__
  hostent *results = gethostbyname( bytes );

  if (! results) {
#else  
  hostent      _results; memset(&_results, 0, sizeof(_results));
  hostent_data data;    memset(&data, 0, sizeof(data));
  hostent *results = &_results;

  int rc = gethostbyname_r(bytes, results, &data);
  if (rc != 0) {
#endif
    jclass ex = env->FindClass("Ljava/net/UnknownHostException;");
    env->ThrowNew(ex, strerror( errno ));
  }

  // verify 4-byte-address assumption
  //
  if (results->h_addrtype != AF_INET || results->h_length != 4) {
    jclass ex = env->FindClass("Ljava/net/UnknownHostException;");
    env->ThrowNew(ex, "Cannot understand result of gethostbyname");
  }
  
  // get addresses (which is all we care about)
  unsigned int **addresses = (unsigned int **)results->h_addr_list;

  // find out how many we need
  for(i = 0; addresses[i] != 0; i++);

  // allocate an object array to hold the byte arrays
  jclass inetArr = env->FindClass("java.net.InetAddress");
  jobjectArray addrs = env->NewObjectArray(i, inetArr, NULL);

  // allocate objects for addresses
  jclass jinet = env->FindClass("java.net.InetAddress");
  jmethodID ctor = env->GetMethodID(jinet, "<init>", "(ILjava/lang/String;)V");
  for(i = 0; addresses[i] != 0; i++) {
    env->SetObjectArrayElement(addrs, i, 
     env->NewObject(jinet, ctor, MANGLE32(*(addresses[i])), hostname));
  }

  // return array of addresses
  return addrs;
}

/*
 * Class:     java_net_InetAddressJpn
 * Method:    getHostByAddrImpl
 * Signature: (I)Ljava/net/InetAddress;
 */
JNIEXPORT jobject JNICALL 
Java_java_net_InetAddressJpn_getHostByAddrImpl(
	       JNIEnv *env, 
	       jclass type, 
	       jint inetAddress)
{
  // get address based on internet address
#ifdef __linux__
  hostent *resultAddress = gethostbyaddr((char *)&inetAddress,
				sizeof(inetAddress),
				AF_INET);

  if (! resultAddress) {
#else  
  hostent results; memset(&results, 0, sizeof(results));
  hostent_data data; memset(&data, 0, sizeof(data));
  hostent *resultAddress = &results;

  int rc = gethostbyaddr_r((char *)&inetAddress,
			   sizeof(inetAddress), AF_INET, 
			   resultAddress, &data);

  if (rc != 0) {
#endif

    // could not find host for given address
    jclass ex = env->FindClass("java/net/UnknownHostException");
    env->ThrowNew(ex, strerror( errno ));
  }

  // create java string of hostname
  char *name = resultAddress->h_name;
  jchar *jchars = new jchar[ strlen(name) + 1 ];
  jchars[ strlen(name) ] = 0;
  for(int i = 0; i < strlen(name); i++) jchars[i] = name[i];
  jstring jname = env->NewString( jchars, strlen(name) );

  // return new InetAddress object
  jclass jaddrt = env->FindClass("java/net/InetAddress");
  jmethodID ctor = env->GetMethodID(jaddrt,"<init>","(ILjava/lang/String;)V");
  return env->NewObject(jaddrt, ctor, inetAddress, jname);
}


/*
 * Class:     java_net_InetAddressJpn
 * Method:    getHostNameImpl
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL 
Java_java_net_InetAddressJpn_getHostNameImpl(JNIEnv *env, jclass type) {
  char buf[ MAXHOSTNAMELEN ];
  int rc = gethostname(buf, MAXHOSTNAMELEN);

  // could not find local hostname!
  if ( rc != 0 ) {
    jclass ex = env->FindClass("java/net/UnknownHostException");
    env->ThrowNew(ex, strerror( errno ));
  }

  // make a string
  jchar *jchars = new jchar[ strlen(buf) + 1 ];
  jchars[ strlen(buf) ] = 0;
  for(int i = 0; i < strlen(buf); i++) jchars[i] = buf[i];
  jstring jname = env->NewString( jchars, strlen(buf) );

  // return java string of hostname
  return jname;
}
