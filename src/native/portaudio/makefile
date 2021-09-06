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
PORTAUDIO_HEADERS = -I"portaudio/include/"

CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(PORTAUDIO_HEADERS)

OBJS = org_jitsi_impl_neomedia_portaudio_Pa.c WMME_DSound.c

LIBS = -Lportaudio/lib/.libs -lportaudio -lpthread

libjnportaudio.so: clean
	$(CC) $(CFLAGS) -fPIC -shared $(OBJS) -o libjnportaudio.so $(LIBS)

install32: libjnportaudio.so
	cp libjnportaudio.so "../../../lib/native/linux-x86/libjnportaudio.so"
	cp libjnportaudio.so "../../../../jitsi-videobridge/lib/native/linux/libjnportaudio.so"

install64: libjnportaudio.so
	cp libjnportaudio.so "../../../lib/native/linux-x86-64/libjnportaudio.so"
	cp libjnportaudio.so "../../../../jitsi-videobridge/lib/native/linux-64/libjnportaudio.so"

installppc64: libjnportaudio.so
	cp libjnportaudio.so "../../../lib/native/linux-ppc64le/libjnportaudio.so"
	cp libjnportaudio.so "../../../../jitsi-videobridge/lib/native/linux-ppc64le/libjnportaudio.so"

clean:
	rm -f *.so
