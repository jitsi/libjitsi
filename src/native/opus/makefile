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
OPUS_HEADERS = -I"/usr/include/opus"

CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(OPUS_HEADERS)

OBJS = org_jitsi_impl_neomedia_codec_audio_opus_Opus.c

LIBS = -lopus -lpthread

libjnopus.so: clean
	$(CC) $(CFLAGS) -fPIC -shared $(OBJS) -o libjnopus.so $(LIBS)

install32: libjnopus.so
	cp libjnopus.so "../../../lib/native/linux-x86/libjnopus.so"
	cp libjnopus.so "../../../../jitsi-videobridge/lib/native/linux/libjnopus.so"

install64: libjnopus.so
	cp libjnopus.so "../../../lib/native/linux-x86-64/libjnopus.so"
	cp libjnopus.so "../../../../jitsi-videobridge/lib/native/linux-64/libjnopus.so"

installppc64: libjnopus.so
	cp libjnopus.so "../../../lib/native/linux-ppc64le/libjnopus.so"
	cp libjnopus.so "../../../../jitsi-videobridge/lib/native/linux-ppc64le/libjnopus.so"

clean:
	rm -f *.so
