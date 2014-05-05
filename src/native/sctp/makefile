CC=gcc

JNI_HEADERS = -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/linux"
SCTP_HEADERS = -I"sctp-refimpl-read-only/KERN/usrsctp/usrsctplib"

#CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(SCTP_HEADERS) -DSCTP_DEBUG=1 -DINET6=1
CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(SCTP_HEADERS) -DINET6=1

OBJS = org_jitsi_sctp4j_Sctp.c

LIBS = -lusrsctp -lpthread

libjnsctp.so: clean
	$(CC) $(CFLAGS) -fPIC -shared $(OBJS) -o libjnsctp.so $(LIBS)

install32: libjnsctp.so
	cp libjnsctp.so "../../../lib/native/linux/libjnsctp.so"
	cp libjnsctp.so "../../../../jitsi-videobridge/lib/native/linux/libjnsctp.so"

install64: libjnsctp.so
	cp libjnsctp.so "../../../lib/native/linux-64/libjnsctp.so"
	cp libjnsctp.so "../../../../jitsi-videobridge/lib/native/linux-64/libjnsctp.so"

clean:
	rm -f *.so
