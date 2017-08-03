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

public final class IoTMessage {
	final static class Cache {
		private static final int MaxCacheSize = 32;
		private final IoTMessage[] cache = new IoTMessage[MaxCacheSize];
		private final byte[] emptyPayload = new byte[0];

		@IoTClient.SecondaryThread
		private IoTMessage create_(int clientId, int sequenceNumber, int responseCode, int payloadLength, byte[] payload) {
			IoTMessage message = null;
			synchronized (cache) {
				for (int i = MaxCacheSize - 1; i >= 0; i--) {
					// try to fetch the first available message, from the end
					if (cache[i] != null) {
						message = cache[i];
						cache[i] = null;
						break;
					}
				}
			}

			if (message == null)
				message = new IoTMessage();

			message.clientId = clientId;
			message.sequenceNumber = sequenceNumber;
			message.responseCode = responseCode;
			message.payloadLength = payloadLength;
			message.payload = payload;

			return message;
		}

		@IoTClient.MixedThreads
		void release_(IoTMessage message) {
			synchronized (cache) {
				message.payload = null;
				message.device = null;
				message.password = null;

				for (int i = MaxCacheSize - 1; i >= 0; i--) {
					// try to return this message at the first available spot
					if (cache[i] == null) {
						cache[i] = message;
						return;
					}
				}
			}
		}

		@IoTClient.SecondaryThread
		@SuppressWarnings("ConstantConditions")
		IoTMessage parseResponse_(byte[] srcBuffer, int length) {
			int state = 0;
			int clientId = 0, sequenceNumber = 0, responseCode = 0, payloadLength = 0, payloadReceivedLength = 0, payloadIndex = 0;
			byte[] payload = null;
			for (int i = 0; i < length; i++) {
				byte b = srcBuffer[i];

				if (b == StartOfPacket) {
					state = 1;
					continue;
				}

				switch ((state & StateMask)) {
				case 7:
					if ((state & FlagEscape) != 0) {
						b ^= 1;
						state &= ~FlagEscape;
					} else if (b == Escape) {
						payloadReceivedLength++;
						state |= FlagEscape;
						break;
					}

					if (payloadReceivedLength == payloadLength) {
						state = 0;
						if (b != EndOfPacket)
							continue;

						return create_(clientId, sequenceNumber, responseCode, payloadIndex, payload);
					}

					payloadReceivedLength++;

					payload[payloadIndex] = b;
					payloadIndex++;
					break;
				case 1:
					clientId = (b & 0xFF);
					state++;
					break;
				case 2:
					sequenceNumber = ((b & 0xFF) >>> 1);
					state++;
					break;
				case 3:
					sequenceNumber |= ((b & 0xFF) << 6);
					state++;
					break;
				case 4:
					responseCode = (b & 0xFF);
					state++;
					break;
				case 5:
					payloadLength = ((b & 0xFF) >>> 1);
					state++;
					break;
				case 6:
					payloadLength |= ((b & 0xFF) << 6);
					state++;
					if (payloadLength == 0)
						payload = emptyPayload;
					else if (payloadLength > MaxPayloadLengthEscaped)
						state = 0;
					else
						payload = new byte[payloadLength];
					break;
				}
			}
			return null;
		}

		@IoTClient.SecondaryThread
		IoTMessage timeout_(IoTDevice device, int interfaceIndex, int commandOrPropertyIndex) {
			final IoTMessage message = create_(0, 0, ResponseTimeout, 0, null);
			message.device = device;
			message.interfaceIndex = interfaceIndex;
			message.commandOrPropertyIndex = commandOrPropertyIndex;
			return message;
		}
	}

	static final int MaxPayloadLengthEscaped = 4096;
	static final int MaxPasswordLengthUnescaped = 32;
	private static final int MaxPasswordLengthEscaped = MaxPasswordLengthUnescaped * 2;

	static final int ClientIdQueryDevice = 0xFF;
	static final int ClientIdDescribeInterface = 0xFD;
	static final int ClientIdDescribeEnum = 0xFB;
	static final int ClientIdChangePassword = 0xF9;
	static final int ClientIdHandshake = 0xF7;

	static final int MaximumSequenceNumber = 0x3FFF; // Java only (becomes 0xFFFC when shifted by the devices)

	static final int ClientMessageException = -1; // Java only (does not come from devices)
	static final int ClientMessageMessageSent = -2; // Java only (does not come from devices)
	static final int MessageQueryDevice = 0x00;
	static final int MessageDescribeInterface = 0x02;
	static final int MessageDescribeEnum = 0x04;
	static final int MessageChangePassword = 0x06;
	static final int MessageHandshake = 0x08;
	static final int MessagePing = 0x0A;
	static final int MessageReset = 0x0C;
	static final int MessageGoodBye = 0x0E;
	static final int MessageExecute = 0x10;
	static final int MessageGetProperty = 0x12;
	static final int MessageSetProperty = 0x14;

	public static final int ResponseTimeout = -1; // Java only (does not come from devices)
	public static final int ResponseOK = 0x00;
	public static final int ResponseDeviceError = 0x02;
	public static final int ResponseUnknownClient = 0x04;
	public static final int ResponseUnsupportedMessage = 0x06;
	public static final int ResponsePayloadTooLarge = 0x08;
	public static final int ResponseInvalidPayload = 0x0A;
	public static final int ResponseEndOfPacketNotFound = 0x0C;
	public static final int ResponseWrongPassword = 0x0E;
	public static final int ResponsePasswordReadOnly = 0x10;
	public static final int ResponseCannotChangePasswordNow = 0x12;
	public static final int ResponseInvalidInterface = 0x14;
	public static final int ResponseInvalidInterfaceCommand = 0x16;
	public static final int ResponseInvalidInterfaceProperty = 0x18;
	public static final int ResponseInterfacePropertyReadOnly = 0x1A;
	public static final int ResponseInterfacePropertyWriteOnly = 0x1C;
	public static final int ResponseInvalidInterfacePropertyValue = 0x1E;
	public static final int ResponseTryAgainLater = 0x20;

	static final byte StartOfPacket = 0x55;
	static final byte Escape = 0x1B;
	static final byte EndOfPacket = 0x33;
	private static final int ResponseHeaderLength = 7;
	private static final int RequestHeaderLength = 8;
	private static final int EndOfPacketLength = 1;
	private static final int StateMask = 0x0F;
	private static final int FlagEscape = 0x10;

	// this class used to be immutable, but after running several tests,
	// caching and reusing proved to save A LOT of GC work, during periods
	// when tens of messages are being sent per second
	int clientId, sequenceNumber, responseCode, payloadLength;
	byte[] payload;
	IoTDevice device;
	int interfaceIndex, commandOrPropertyIndex;
	byte[] password;

	@IoTClient.SecondaryThread
	private IoTMessage() {
	}

	@IoTClient.SecondaryThread
	static byte[] allocateMaximumResponseBuffer_() {
		return new byte[ResponseHeaderLength + MaxPayloadLengthEscaped + EndOfPacketLength];
	}

	@IoTClient.SecondaryThread
	static byte[] allocateMaximumRequestBuffer_() {
		return new byte[RequestHeaderLength + MaxPasswordLengthEscaped + MaxPayloadLengthEscaped + EndOfPacketLength];
	}

	@IoTClient.SecondaryThread
	IoTDevice parseQueryDevice_(IoTClient client, SocketAddress socketAddress) {
		if (clientId != IoTMessage.ClientIdQueryDevice ||
			responseCode != IoTMessage.ResponseOK)
			return null;

		int srcOffset = 0;

		final int flags = (payload[srcOffset++] & 0xFF);

		final long leastSigBits = ((long)((payload[srcOffset++] & 0xFF) | ((payload[srcOffset++] & 0xFF) << 8) | ((payload[srcOffset++] & 0xFF) << 16) | (payload[srcOffset++] << 24)) & 0xFFFFFFFFL) |
			((long)((payload[srcOffset++] & 0xFF) | ((payload[srcOffset++] & 0xFF) << 8) | ((payload[srcOffset++] & 0xFF) << 16) | (payload[srcOffset++] << 24)) << 32);

		final long mostSigBits = ((long)((payload[srcOffset++] & 0xFF) | ((payload[srcOffset++] & 0xFF) << 8) | ((payload[srcOffset++] & 0xFF) << 16) | (payload[srcOffset++] << 24)) & 0xFFFFFFFFL) |
			((long)((payload[srcOffset++] & 0xFF) | ((payload[srcOffset++] & 0xFF) << 8) | ((payload[srcOffset++] & 0xFF) << 16) | (payload[srcOffset++] << 24)) << 32);

		final int interfaceCount = (payload[srcOffset++] & 0xFF);
		if (interfaceCount <= 0 || interfaceCount > 128)
			return null;
		srcOffset += interfaceCount; // skip all interfaces for now

		final int nameLen = (payload[srcOffset++] & 0xFF);
		if ((nameLen + srcOffset) > payloadLength)
			return null;
		final String name = new String(payload, srcOffset, nameLen);

		return new IoTDevice(client, socketAddress, flags, new UUID(mostSigBits, leastSigBits), name, new IoTInterface[interfaceCount]);
	}

	@IoTClient.SecondaryThread
	IoTInterface parseDescribeInterface_(IoTDevice device) {
		if (clientId != IoTMessage.ClientIdDescribeInterface ||
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

	@IoTClient.SecondaryThread
	int parseHandshake_() {
		if (clientId != IoTMessage.ClientIdHandshake ||
			responseCode != IoTMessage.ResponseOK ||
			payloadLength != 1)
			return -1;

		final int clientId = (payload[0] & 0xFF);

		return (((clientId & 1) != 0) ? -1 : clientId);
	}
}
