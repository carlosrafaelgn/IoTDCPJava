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
import java.util.HashMap;

final class IoTSentMessage {
	final static class Cache {
		private static final int MaxCacheSize = 32;
		private final IoTSentMessage[] cache = new IoTSentMessage[MaxCacheSize];
		private final HashMap<IoTSentMessage, IoTSentMessage> sentMessages = new HashMap<>(MaxCacheSize);
		private volatile boolean waitingForResponses;

		@IoTClient.MixedThreads
		private IoTSentMessage create_(SocketAddress socketAddress, IoTDevice device, int clientId, int sequenceNumber, byte[] password, int message, int payloadLength, int payload0, int payload1, IoTProperty.Buffer value) {
			IoTSentMessage sentMessage = null;
			synchronized (cache) {
				for (int i = MaxCacheSize - 1; i >= 0; i--) {
					// try to fetch the first available message, from the end
					if (cache[i] != null) {
						sentMessage = cache[i];
						cache[i] = null;
						break;
					}
				}
			}

			if (sentMessage == null)
				sentMessage = new IoTSentMessage();

			sentMessage.socketAddress = socketAddress;
			sentMessage.device = device;
			sentMessage.clientId = clientId;
			sentMessage.sequenceNumber = sequenceNumber;
			sentMessage.password = password;
			sentMessage.message = message;
			sentMessage.payloadLength = payloadLength;
			sentMessage.payload0 = payload0;
			sentMessage.payload1 = payload1;
			sentMessage.value = value;
			sentMessage.attempts = 0;
			switch (clientId) {
			case IoTMessage.ClientIdDescribeInterface:
				sentMessage.hash0 = payload0;
				break;
			case IoTMessage.ClientIdDescribeEnum:
				sentMessage.hash0 = payload0 | (payload1 << 1);
				break;
			default:
				sentMessage.hash0 = 0;
				break;
			}
			sentMessage.hash = socketAddress.hashCode() ^ (clientId << 24) ^ (sequenceNumber << 8) ^ sentMessage.hash0;
			return sentMessage;
		}

		@IoTClient.SecondaryThread
		private void doRelease_(IoTSentMessage sentMessage) {
			sentMessage.socketAddress = null;
			sentMessage.device = null;
			sentMessage.password = null;
			sentMessage.next = null;
			sentMessage.value = null;

			for (int i = MaxCacheSize - 1; i >= 0; i--) {
				// try to return this message at the first available spot
				if (cache[i] == null) {
					cache[i] = sentMessage;
					return;
				}
			}
		}

		boolean isWaitingForResponses() {
			return waitingForResponses;
		}

		@IoTClient.SecondaryThread
		void markAsSentMessage_(IoTSentMessage sentMessage) {
			synchronized (cache) {
				sentMessages.put(sentMessage, sentMessage);
				waitingForResponses = true;
			}
		}

		@IoTClient.SecondaryThread
		void unmarkAsSentMessageAndRelease_(IoTSentMessage sentMessage) {
			synchronized (cache) {
				sentMessages.remove(sentMessage);
				doRelease_(sentMessage);
				waitingForResponses = (sentMessages.size() != 0);
			}
		}

		@IoTClient.SecondaryThread
		void release_(IoTSentMessage sentMessage) {
			synchronized (cache) {
				doRelease_(sentMessage);
			}
		}

		@IoTClient.SecondaryThread
		IoTSentMessage getActualSentMessage_(IoTSentMessage placeholder) {
			synchronized (cache) {
				return sentMessages.get(placeholder);
			}
		}

		@IoTClient.SecondaryThread
		IoTSentMessage[] copySentMessages_(IoTSentMessage[] sentMessages) {
			synchronized (cache) {
				return this.sentMessages.values().toArray(sentMessages);
			}
		}

		@IoTClient.SecondaryThread
		IoTSentMessage placeholder_(SocketAddress socketAddress) {
			return create_(socketAddress,
				null,
				0,
				0,
				null,
				0,
				0,
				0, 0, null);
		}

		IoTSentMessage queryDevice(SocketAddress socketAddress) {
			return create_(socketAddress,
				null,
				IoTMessage.ClientIdQueryDevice,
				IoTMessage.MaximumSequenceNumber,
				null,
				IoTMessage.MessageQueryDevice,
				0,
				0, 0, null);
		}

		@IoTClient.SecondaryThread
		IoTSentMessage describeInterface_(IoTDevice device, int interfaceIndex) {
			return create_(device.socketAddress,
				device,
				IoTMessage.ClientIdDescribeInterface,
				IoTMessage.MaximumSequenceNumber,
				null,
				IoTMessage.MessageDescribeInterface,
				1,
				interfaceIndex, 0, null);
		}

		@IoTClient.SecondaryThread
		IoTSentMessage describeEnum_(IoTInterface ioTInterface, int propertyIndex) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				IoTMessage.ClientIdDescribeEnum,
				IoTMessage.MaximumSequenceNumber,
				null,
				IoTMessage.MessageDescribeEnum,
				2,
				ioTInterface.index, propertyIndex, null);
		}

		IoTSentMessage changePassword(IoTDevice device, String password) {
			return create_(device.socketAddress,
				device,
				IoTMessage.ClientIdChangePassword,
				IoTMessage.MaximumSequenceNumber,
				(password == null || password.length() == 0) ? null : password.getBytes(),
				IoTMessage.MessageChangePassword,
				0,
				0, 0, null);
		}

		IoTSentMessage handshake(IoTDevice device) {
			return create_(device.socketAddress,
				device,
				IoTMessage.ClientIdHandshake,
				IoTMessage.MaximumSequenceNumber,
				device.password,
				IoTMessage.MessageHandshake,
				0,
				0, 0, null);
		}

		IoTSentMessage ping(IoTDevice device) {
			return create_(device.socketAddress,
				device,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				IoTMessage.MessagePing,
				0,
				0, 0, null);
		}

		IoTSentMessage reset(IoTDevice device) {
			return create_(device.socketAddress,
				device,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				IoTMessage.MessageReset,
				0,
				0, 0, null);
		}

		IoTSentMessage goodBye(IoTDevice device) {
			return create_(device.socketAddress,
				device,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				IoTMessage.MessageGoodBye,
				0,
				0, 0, null);
		}

		IoTSentMessage execute(IoTInterface ioTInterface, int command) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				IoTMessage.MessageExecute,
				2,
				ioTInterface.index, command, null);
		}

		IoTSentMessage getProperty(IoTInterface ioTInterface, int propertyIndex) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				IoTMessage.MessageGetProperty,
				2,
				ioTInterface.index, propertyIndex, null);
		}

		IoTSentMessage setProperty(IoTInterface ioTInterface, int propertyIndex, IoTProperty.Buffer value) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				IoTMessage.MessageSetProperty,
				0,
				ioTInterface.index, propertyIndex, value);
		}
	}

	// this class used to be immutable, but after running several tests,
	// caching and reusing proved to save A LOT of GC work, during periods
	// when tens of messages are being sent per second
	SocketAddress socketAddress;
	IoTDevice device;
	int message, payloadLength, payload0, payload1;
	byte[] password;
	private IoTProperty.Buffer value;
	private int clientId, sequenceNumber, hash, hash0;

	IoTSentMessage next;
	int attempts, timestamp;

	@IoTClient.SecondaryThread
	private IoTSentMessage() {
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof IoTSentMessage))
			return false;
		final IoTSentMessage message = (IoTSentMessage)o;
		return (socketAddress.equals(message.socketAddress) &&
			(clientId == message.clientId) &&
			(sequenceNumber == message.sequenceNumber) &&
			(hash0 == message.hash0));
	}

	@IoTClient.SecondaryThread
	private static int escapeBuffer_(byte[] dstBuffer, int dstOffset, byte[] srcBuffer, int length) {
		for (int i = 0; i < length; i++) {
			final byte v = srcBuffer[i];
			if (v == IoTMessage.StartOfPacket || v == IoTMessage.Escape) {
				dstBuffer[dstOffset++] = IoTMessage.Escape;
				dstBuffer[dstOffset++] = (byte)(v ^ 1);
			} else {
				dstBuffer[dstOffset++] = v;
			}
		}
		return dstOffset;
	}

	@IoTClient.SecondaryThread
	private static int buildHeader_(byte[] dstBuffer, int clientId, int sequenceNumber, byte[] password, int message) {
		if (password != null && password.length > IoTMessage.MaxPasswordLengthUnescaped)
			throw new IllegalArgumentException("0 <= password.length <= MaxPasswordLengthUnescaped");
		int dstOffset = 0;
		dstBuffer[dstOffset++] = IoTMessage.StartOfPacket;
		dstBuffer[dstOffset++] = (byte)clientId;
		dstBuffer[dstOffset++] = (byte)(sequenceNumber << 1);
		dstBuffer[dstOffset++] = (byte)((sequenceNumber >>> 7) << 1);
		if (password == null || password.length == 0) {
			dstBuffer[dstOffset++] = 0;
		} else {
			final int passwordLengthOffset = dstOffset;
			dstOffset = escapeBuffer_(dstBuffer, dstOffset + 1, password, password.length);
			dstBuffer[passwordLengthOffset] = (byte)((dstOffset - passwordLengthOffset - 1) << 1);
		}
		dstBuffer[dstOffset++] = (byte)message;
		dstBuffer[dstOffset++] = 0;
		dstBuffer[dstOffset++] = 0;
		return dstOffset;
	}

	@IoTClient.SecondaryThread
	private static void fillPayloadLength_(byte[] dstBuffer, int payloadLengthOffset, int payloadLength) {
		if (payloadLength < 0 || payloadLength > IoTMessage.MaxPayloadLengthEscaped)
			throw new IllegalArgumentException("0 <= payloadLength <= MaxPayloadLengthEscaped");
		dstBuffer[payloadLengthOffset] = (byte)(payloadLength << 1);
		dstBuffer[payloadLengthOffset + 1] = (byte)((payloadLength >>> 7) << 1);
	}

	@IoTClient.SecondaryThread
	private static int build_(byte[] dstBuffer, int clientId, int sequenceNumber, byte[] password, int message) {
		int dstOffset = buildHeader_(dstBuffer, clientId, sequenceNumber, password, message);
		dstBuffer[dstOffset++] = IoTMessage.EndOfPacket;
		return dstOffset;
	}

	@IoTClient.SecondaryThread
	private static int build_(byte[] dstBuffer, int clientId, int sequenceNumber, byte[] password, int message, int byteValue0) {
		int dstOffset = buildHeader_(dstBuffer, clientId, sequenceNumber, password, message);
		final int payloadOffset = dstOffset;
		if (byteValue0 == IoTMessage.StartOfPacket || byteValue0 == IoTMessage.Escape) {
			dstBuffer[dstOffset++] = IoTMessage.Escape;
			dstBuffer[dstOffset++] = (byte)(byteValue0 ^ 1);
		} else {
			dstBuffer[dstOffset++] = (byte)byteValue0;
		}
		fillPayloadLength_(dstBuffer, payloadOffset - 2, dstOffset - payloadOffset);
		dstBuffer[dstOffset++] = IoTMessage.EndOfPacket;
		return dstOffset;
	}

	@IoTClient.SecondaryThread
	private static int build_(byte[] dstBuffer, int clientId, int sequenceNumber, byte[] password, int message, byte[] byteValues, int byteValuesLength) {
		int dstOffset = buildHeader_(dstBuffer, clientId, sequenceNumber, password, message);
		final int payloadOffset = dstOffset;
		dstOffset = escapeBuffer_(dstBuffer, dstOffset, byteValues, byteValuesLength);
		fillPayloadLength_(dstBuffer, payloadOffset - 2, dstOffset - payloadOffset);
		dstBuffer[dstOffset++] = IoTMessage.EndOfPacket;
		return dstOffset;
	}

	@IoTClient.SecondaryThread
	private static int build_(byte[] dstBuffer, int clientId, int sequenceNumber, byte[] password, int message, int byteValue0, int byteValue1, byte[] byteValues2, int byteValues2Length) {
		int dstOffset = buildHeader_(dstBuffer, clientId, sequenceNumber, password, message);
		final int payloadOffset = dstOffset;
		if (byteValue0 == IoTMessage.StartOfPacket || byteValue0 == IoTMessage.Escape) {
			dstBuffer[dstOffset++] = IoTMessage.Escape;
			dstBuffer[dstOffset++] = (byte)(byteValue0 ^ 1);
		} else {
			dstBuffer[dstOffset++] = (byte)byteValue0;
		}
		if (byteValue1 == IoTMessage.StartOfPacket || byteValue1 == IoTMessage.Escape) {
			dstBuffer[dstOffset++] = IoTMessage.Escape;
			dstBuffer[dstOffset++] = (byte)(byteValue1 ^ 1);
		} else {
			dstBuffer[dstOffset++] = (byte)byteValue1;
		}
		dstOffset = escapeBuffer_(dstBuffer, dstOffset, byteValues2, byteValues2Length);
		fillPayloadLength_(dstBuffer, payloadOffset - 2, dstOffset - payloadOffset);
		dstBuffer[dstOffset++] = IoTMessage.EndOfPacket;
		return dstOffset;
	}

	@IoTClient.SecondaryThread
	void fillPlaceholder_(SocketAddress socketAddress, int clientId, int sequenceNumber, byte[] payload) {
		this.socketAddress = socketAddress;
		this.clientId = clientId;
		this.sequenceNumber = sequenceNumber;
		if (payload == null) {
			payload0 = 0;
			payload1 = 0;
		} else if (payload.length >= 2) {
			payload0 = (payload[0] & 0xFF);
			payload1 = (payload[1] & 0xFF);
		} else if (payload.length == 1) {
			payload0 = (payload[0] & 0xFF);
			payload1 = 0;
		}
		switch (clientId) {
		case IoTMessage.ClientIdDescribeInterface:
			hash0 = payload0;
			break;
		case IoTMessage.ClientIdDescribeEnum:
			hash0 = payload0 | (payload1 << 1);
			break;
		default:
			hash0 = 0;
			break;
		}
		hash = socketAddress.hashCode() ^ (clientId << 24) ^ (sequenceNumber << 8) ^ hash0;
	}

	@IoTClient.SecondaryThread
	@SuppressWarnings("SynchronizeOnNonFinalField")
	int build_(byte[] dstBuffer) {
		if (value != null) {
			// it is safe to synchronized on value because we now it will not
			// change throughout the execution of this method (it is changed
			// only when a message is created or released)
			synchronized (value) {
				return build_(dstBuffer, clientId, sequenceNumber, password, message, payload0, payload1, value.buffer, value.length);
			}
		}

		if (message == IoTMessage.MessageChangePassword)
			return build_(dstBuffer, clientId, sequenceNumber, null, message, password, password == null ? 0 : password.length);

		switch (payloadLength) {
		case 1:
			return build_(dstBuffer, clientId, sequenceNumber, password, message, payload0);
		case 2:
			return build_(dstBuffer, clientId, sequenceNumber, password, message, payload0, payload1, null, 0);
		default:
			return build_(dstBuffer, clientId, sequenceNumber, password, message);
		}
	}
}
