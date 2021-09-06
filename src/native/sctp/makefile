#
# Copyright @ 2015 Atlassian Pty Ltd
# Copyright @ 2021 Raptor Engineering, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CC=gcc

JNI_HEADERS = -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/linux"
SCTP_HEADERS = -I"usrsctp/usrsctplib"

CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(SCTP_HEADERS) -DSCTP_DEBUG=1 -DINET6=1
#CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(SCTP_HEADERS) -DINET6=1

OBJS = org_jitsi_sctp4j_Sctp.c

LIBS = -L"usrsctp/usrsctplib/.libs" -lusrsctp -lpthread

libjnsctp.so: clean
	$(CC) $(CFLAGS) -fPIC -shared $(OBJS) -o libjnsctp.so $(LIBS)

install32: libjnsctp.so
	cp libjnsctp.so "../../../lib/native/linux-x86/libjnsctp.so"
	cp libjnsctp.so "../../../../jitsi-videobridge/lib/native/linux/libjnsctp.so"

install64: libjnsctp.so
	cp libjnsctp.so "../../../lib/native/linux-x86-64/libjnsctp.so"
	cp libjnsctp.so "../../../../jitsi-videobridge/lib/native/linux-64/libjnsctp.so"

installppc64: libjnsctp.so
	cp libjnsctp.so "../../../lib/native/linux-ppc64le/libjnsctp.so"
	cp libjnsctp.so "../../../../jitsi-videobridge/lib/native/linux-ppc64le/libjnsctp.so"

clean:
	rm -f *.so
