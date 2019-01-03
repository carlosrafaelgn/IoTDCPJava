//
// IoTDCPJava is distributed under the FreeBSD License
//
// Copyright (c) 2017, Carlos Rafael Gimenes das Neves
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// * Redistributions of source code must retain the above copyright notice, this
//   list of conditions and the following disclaimer.
//
// * Redistributions in binary form must reproduce the above copyright notice,
//   this list of conditions and the following disclaimer in the documentation
//   and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// https://github.com/carlosrafaelgn/IoTDCPJava
//
package br.com.carlosrafaelgn.iotdcp;

import java.net.SocketAddress;
import java.util.UUID;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class IoTMessage {
	static final class Cache {
		private static final int MaxCacheSize = 32;
		private final IoTMessage[] cache = new IoTMessage[MaxCacheSize];
		private final byte[] emptyPayload = new byte[0];

		@SecondaryThread
		private IoTMessage create_(int messageType, int clientId, int sequenceNumber, int responseCode, int payloadLength, byte[] payload) {
			IoTMessage message = null;
			synchronized (cache) {
				for (int i = MaxCacheSize - 1; i >= 0; i--) {
					// Try to fetch the first available message, from the end
					if (cache[i] != null) {
						message = cache[i];
						cache[i] = null;
						break;
					}
				}
			}

			if (message == null)
				message = new IoTMessage();

			message.messageType = messageType;
			message.clientId = clientId;
			message.sequenceNumber = sequenceNumber;
			message.responseCode = responseCode;
			message.payloadLength = payloadLength;
			message.payload = payload;

			return message;
		}

		@MixedThreads
		void release_(IoTMessage message) {
			synchronized (cache) {
				message.payload = null;
				message.device = null;
				message.password = null;

				for (int i = MaxCacheSize - 1; i >= 0; i--) {
					// Try to return this message at the first available spot
					if (cache[i] == null) {
						cache[i] = message;
						return;
					}
				}
			}
		}

		@SecondaryThread
		@SuppressWarnings("ConstantConditions")
		IoTMessage parseResponse_(byte[] srcBuffer, int length) {
			if (srcBuffer == null ||
				length < (ResponseHeaderLength + EndOfPacketLength) ||
				srcBuffer[0] != StartOfPacket ||
				srcBuffer[length - 1] != EndOfPacket)
				return null;

			final int message = (srcBuffer[1] & 0xFF),
				clientId = (srcBuffer[2] & 0xFF),
				sequenceNumber = (srcBuffer[3] & 0xFF) | ((srcBuffer[4] & 0xFF) << 8),
				responseCode = (srcBuffer[5] & 0xFF),
				payloadLength = (srcBuffer[6] & 0xFF) | ((srcBuffer[7] & 0xFF) << 8),
				actualPayloadLength = length - (ResponseHeaderLength + EndOfPacketLength);

			if (payloadLength > MaxPayloadLength ||
				actualPayloadLength != payloadLength)
				return null;

			final byte[] payload;

			if (payloadLength == 0) {
				payload = emptyPayload;
			} else {
				payload = new byte[payloadLength];
				System.arraycopy(srcBuffer, ResponseHeaderLength, payload, 0, payloadLength);
			}

			return create_(message, clientId, sequenceNumber, responseCode, payloadLength, payload);
		}
	}

	static final int MaxPayloadLength = 32768;
	static final int MaxPasswordLength = 64;

	static final int InvalidClientId = 255;

	static final int MaximumSequenceNumber = 0xFFFF; // Java only

	static final int MessageTimeout = -1; // Java only (does not come from devices)
	static final int MessageException = -2; // Java only (does not come from devices)
	static final int MessageSent = -3; // Java only (does not come from devices)
	public static final int MessageQueryDevice = 0x00;
	public static final int MessageDescribeInterface = 0x01;
	public static final int MessageDescribeEnum = 0x02;
	public static final int MessageChangeName = 0x03;
	public static final int MessageChangePassword = 0x04;
	public static final int MessageHandshake = 0x05;
	public static final int MessagePing = 0x06;
	public static final int MessageReset = 0x07;
	public static final int MessageGoodBye = 0x08;
	public static final int MessageExecute = 0x09;
	public static final int MessageGetProperty = 0x0A;
	public static final int MessageSetProperty = 0x0B;

	static final int ServerMessagePropertyChange = 0x80;

	public static final int ResponseOK = 0x00;
	public static final int ResponseDeviceError = 0x01;
	public static final int ResponseUnknownClient = 0x02;
	public static final int ResponseUnsupportedMessage = 0x03;
	public static final int ResponsePayloadTooLarge = 0x04;
	public static final int ResponseInvalidPayload = 0x05;
	public static final int ResponseEndOfPacketNotFound = 0x06;
	public static final int ResponseWrongPassword = 0x07;
	public static final int ResponseNameReadOnly = 0x08;
	public static final int ResponsePasswordReadOnly = 0x09;
	public static final int ResponseCannotChangeNameNow = 0x0A;
	public static final int ResponseCannotChangePasswordNow = 0x0B;
	public static final int ResponseInvalidInterface = 0x0C;
	public static final int ResponseInvalidInterfaceCommand = 0x0D;
	public static final int ResponseInvalidInterfaceProperty = 0x0E;
	public static final int ResponseInterfacePropertyReadOnly = 0x0F;
	public static final int ResponseInterfacePropertyWriteOnly = 0x10;
	public static final int ResponseInvalidInterfacePropertyValue = 0x11;
	public static final int ResponseTryAgainLater = 0x12;

	static final byte StartOfPacket = 0x55;
	static final byte EndOfPacket = 0x33;
	private static final int ResponseHeaderLength = 8;
	private static final int RequestHeaderLength = 8;
	private static final int EndOfPacketLength = 1;

	// This class used to be immutable, but after running several tests,
	// caching and reusing proved to save A LOT of GC work, during periods
	// when tens of messages are being sent per second
	int messageType, clientId, sequenceNumber, responseCode, payloadLength;
	byte[] payload;
	IoTDevice device;
	byte[] password;

	@SecondaryThread
	private IoTMessage() {
	}

	@SecondaryThread
	static byte[] allocateMaximumResponseBuffer_() {
		return new byte[ResponseHeaderLength + MaxPayloadLength + EndOfPacketLength];
	}

	@SecondaryThread
	static byte[] allocateMaximumRequestBuffer_() {
		return new byte[RequestHeaderLength + MaxPasswordLength + MaxPayloadLength + EndOfPacketLength];
	}

	private static long deserializeLong(byte[] payload, int srcOffset) {
		return ((long)((payload[srcOffset] & 0xFF) | ((payload[srcOffset + 1] & 0xFF) << 8) | ((payload[srcOffset + 2] & 0xFF) << 16) | (payload[srcOffset + 3] << 24)) & 0xFFFFFFFFL) |
			((long)((payload[srcOffset + 4] & 0xFF) | ((payload[srcOffset + 5] & 0xFF) << 8) | ((payload[srcOffset + 6] & 0xFF) << 16) | (payload[srcOffset + 7] << 24)) << 32);
	}

	@SecondaryThread
	IoTDevice parseQueryDevice_(IoTClient client, SocketAddress socketAddress) {
		if (clientId != IoTMessage.InvalidClientId ||
			responseCode != IoTMessage.ResponseOK ||
			payloadLength < (1 + 16 + 16 + 1 + 1))
			return null;

		int srcOffset = 0;

		final int flags = (payload[srcOffset++] & 0xFF);

		final long leastSigBitsCategory = deserializeLong(payload, srcOffset);
		final long mostSigBitsCategory = deserializeLong(payload, srcOffset + 8);
		final long leastSigBits = deserializeLong(payload, srcOffset + 16);
		final long mostSigBits = deserializeLong(payload, srcOffset + 24);
		srcOffset += 32;

		final int interfaceCount = (payload[srcOffset++] & 0xFF);
		if (interfaceCount <= 0 || interfaceCount > 128)
			return null;
		srcOffset += interfaceCount; // Skip all interfaces for now

		final int nameLen = (payload[srcOffset++] & 0xFF);
		if ((nameLen + srcOffset) > payloadLength)
			return null;
		final String name = ((nameLen == 0) ? "IoT" : new String(payload, srcOffset, nameLen));

		return new IoTDevice(client, socketAddress, flags, new UUID(mostSigBitsCategory, leastSigBitsCategory), new UUID(mostSigBits, leastSigBits), name, new IoTInterface[interfaceCount]);
	}

	@SecondaryThread
	IoTInterface parseDescribeInterface_(IoTDevice device) {
		if (clientId != IoTMessage.InvalidClientId ||
			responseCode != IoTMessage.ResponseOK)
			return null;

		try {
			int srcOffset = 0;

			final int interfaceIndex = (payload[srcOffset++] & 0xFF);

			final int nameLen = (payload[srcOffset++] & 0xFF);
			if ((nameLen + srcOffset) > payloadLength)
				return null;
			final String name = new String(payload, srcOffset, nameLen);
			srcOffset += nameLen;

			final int type = (payload[srcOffset++] & 0xFF);

			final int propertyCount = (payload[srcOffset++] & 0xFF);
			final IoTProperty[] properties = new IoTProperty[propertyCount];

			for (int i = 0; i < propertyCount; i++) {
				final int propertyNameLen = (payload[srcOffset++] & 0xFF);
				if ((propertyNameLen + srcOffset) > payloadLength)
					return null;
				final String propertyName = new String(payload, srcOffset, propertyNameLen);
				srcOffset += propertyNameLen;

				properties[i] = new IoTProperty(i,
					propertyName,
					payload[srcOffset++] & 0xFF,
					payload[srcOffset++] & 0xFF,
					payload[srcOffset++] & 0xFF,
					payload[srcOffset++] & 0xFF,
					payload[srcOffset++] & 0xFF,
					payload[srcOffset++] & 0xFF);
			}

			switch (type) {
			case IoTInterface.TypeSensor:
				return IoTInterfaceSensor.create_(device, interfaceIndex, name, properties);
			case IoTInterface.TypeOnOff:
				return IoTInterfaceOnOff.create_(device, interfaceIndex, name, properties);
			case IoTInterface.TypeOnOffSimple:
				return IoTInterfaceOnOffSimple.create_(device, interfaceIndex, name, properties);
			case IoTInterface.TypeOpenClose:
				return IoTInterfaceOpenClose.create_(device, interfaceIndex, name, properties);
			case IoTInterface.TypeOpenCloseStop:
				return IoTInterfaceOpenCloseStop.create_(device, interfaceIndex, name, properties);
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}

		return null;
	}

	@SecondaryThread
	int parseHandshake_() {
		if (clientId != IoTMessage.InvalidClientId ||
			responseCode != IoTMessage.ResponseOK ||
			payloadLength != 1)
			return IoTMessage.InvalidClientId;

		return payload[0] & 0xFF;
	}
}
