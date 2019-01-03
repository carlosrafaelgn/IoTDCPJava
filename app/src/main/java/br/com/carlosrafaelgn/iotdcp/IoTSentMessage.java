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
	static final class Cache {
		private static final int MaxCacheSize = 32;
		private final IoTSentMessage[] cache = new IoTSentMessage[MaxCacheSize];
		private final HashMap<IoTSentMessage, IoTSentMessage> sentMessages = new HashMap<>(MaxCacheSize);
		private volatile boolean waitingForResponses;

		@MixedThreads
		private IoTSentMessage create_(SocketAddress socketAddress, IoTDevice device, int messageType, int clientId, int sequenceNumber, byte[] password, int payloadLength, int payload0, int payload1, IoTProperty.Buffer value, int userArg) {
			IoTSentMessage sentMessage = null;
			synchronized (cache) {
				for (int i = MaxCacheSize - 1; i >= 0; i--) {
					// Try to fetch the first available message, from the end
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
			sentMessage.messageType = messageType;
			sentMessage.clientId = clientId;
			sentMessage.sequenceNumber = sequenceNumber;
			sentMessage.password = password;
			sentMessage.payloadLength = payloadLength;
			sentMessage.payload0 = payload0;
			sentMessage.payload1 = payload1;
			sentMessage.value = value;
			sentMessage.attempts = 0;
			sentMessage.userArg = userArg;
			switch (messageType) {
			case IoTMessage.MessageDescribeInterface:
				sentMessage.hash0 = payload0;
				break;
			case IoTMessage.MessageDescribeEnum:
				sentMessage.hash0 = payload0 ^ (payload1 << 1);
				break;
			default:
				sentMessage.hash0 = 0;
				break;
			}
			sentMessage.hash = socketAddress.hashCode() ^ (messageType << 24) ^ (sequenceNumber << 8) ^ sentMessage.hash0;
			return sentMessage;
		}

		@SecondaryThread
		private void doRelease_(IoTSentMessage sentMessage) {
			sentMessage.socketAddress = null;
			sentMessage.device = null;
			sentMessage.password = null;
			sentMessage.next = null;
			sentMessage.value = null;

			for (int i = MaxCacheSize - 1; i >= 0; i--) {
				// Try to return this message at the first available spot
				if (cache[i] == null) {
					cache[i] = sentMessage;
					return;
				}
			}
		}

		boolean isWaitingForResponses() {
			return waitingForResponses;
		}

		@SecondaryThread
		void markAsSentMessage_(IoTSentMessage sentMessage) {
			synchronized (cache) {
				sentMessages.put(sentMessage, sentMessage);
				waitingForResponses = true;
			}
		}

		@SecondaryThread
		void unmarkAsSentMessageAndRelease_(IoTSentMessage sentMessage) {
			synchronized (cache) {
				sentMessages.remove(sentMessage);
				doRelease_(sentMessage);
				waitingForResponses = (sentMessages.size() != 0);
			}
		}

		@SecondaryThread
		void release_(IoTSentMessage sentMessage) {
			synchronized (cache) {
				doRelease_(sentMessage);
			}
		}

		@SecondaryThread
		IoTSentMessage getActualSentMessage_(IoTSentMessage placeholder) {
			synchronized (cache) {
				return sentMessages.get(placeholder);
			}
		}

		@SecondaryThread
		IoTSentMessage[] copySentMessages_(IoTSentMessage[] sentMessages) {
			synchronized (cache) {
				return this.sentMessages.values().toArray(sentMessages);
			}
		}

		@SecondaryThread
		IoTSentMessage placeholder_(SocketAddress socketAddress) {
			return create_(socketAddress,
				null,
				0,
				IoTMessage.InvalidClientId,
				0,
				null,
				0,
				0,
				0,
				null,
				0);
		}

		IoTSentMessage queryDevice(SocketAddress socketAddress) {
			return create_(socketAddress,
				null,
				IoTMessage.MessageQueryDevice,
				IoTMessage.InvalidClientId,
				IoTMessage.MaximumSequenceNumber,
				null,
				0,
				0,
				0,
				null,
				0);
		}

		@SecondaryThread
		IoTSentMessage describeInterface_(IoTDevice device, int interfaceIndex) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageDescribeInterface,
				IoTMessage.InvalidClientId,
				IoTMessage.MaximumSequenceNumber,
				null,
				1,
				interfaceIndex,
				0,
				null,
				0);
		}

		@SecondaryThread
		IoTSentMessage describeEnum_(IoTInterface ioTInterface, int propertyIndex) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageDescribeEnum,
				IoTMessage.InvalidClientId,
				IoTMessage.MaximumSequenceNumber,
				null,
				2,
				ioTInterface.index,
				propertyIndex,
				null,
				0);
		}

		IoTSentMessage changeName(IoTDevice device, String name, int userArg) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageChangeName,
				IoTMessage.InvalidClientId,
				IoTMessage.MaximumSequenceNumber,
				(name == null || name.length() == 0) ? null : name.getBytes(),
				0,
				0,
				0,
				null,
				userArg);
		}

		IoTSentMessage changePassword(IoTDevice device, String password, int userArg) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageChangePassword,
				IoTMessage.InvalidClientId,
				IoTMessage.MaximumSequenceNumber,
				(password == null || password.length() == 0) ? null : password.getBytes(),
				0,
				0,
				0,
				null,
				userArg);
		}

		IoTSentMessage handshake(IoTDevice device, int userArg) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageHandshake,
				IoTMessage.InvalidClientId,
				device.nextSequenceNumber(),
				device.password,
				0,
				0,
				0,
				null,
				userArg);
		}

		IoTSentMessage ping(IoTDevice device, int userArg) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessagePing,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				0,
				0,
				0,
				null,
				userArg);
		}

		IoTSentMessage reset(IoTDevice device, int userArg) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageReset,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				0,
				0,
				0,
				null,
				userArg);
		}

		IoTSentMessage goodBye(IoTDevice device, int userArg) {
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageGoodBye,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				0,
				0,
				0,
				null,
				userArg);
		}

		IoTSentMessage execute(IoTInterface ioTInterface, int command, int userArg) {
			final IoTDevice device = ioTInterface.device;
			final IoTSentMessage sentMessage = create_(device.socketAddress,
				device,
				IoTMessage.MessageExecute,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				2,
				ioTInterface.index,
				command,
				null,
				userArg);
			sentMessage.executedInterfaceIndex = ioTInterface.index;
			sentMessage.executedCommand = command;
			return sentMessage;
		}

		IoTSentMessage getProperty(IoTInterface ioTInterface, int propertyIndex, int userArg) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageGetProperty,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				2,
				ioTInterface.index,
				propertyIndex,
				null,
				userArg);
		}

		IoTSentMessage setProperty(IoTInterface ioTInterface, int propertyIndex, IoTProperty.Buffer value, int userArg) {
			final IoTDevice device = ioTInterface.device;
			return create_(device.socketAddress,
				device,
				IoTMessage.MessageSetProperty,
				device.clientId,
				device.nextSequenceNumber(),
				device.password,
				0,
				ioTInterface.index,
				propertyIndex,
				value,
				userArg);
		}
	}

	// This class used to be immutable, but after running several tests,
	// caching and reusing proved to save A LOT of GC work, during periods
	// when tens of messages are being sent per second
	SocketAddress socketAddress;
	IoTDevice device;
	int messageType, userArg;
	int executedInterfaceIndex, executedCommand; // Only used with MessageExecute
	byte[] password;
	private IoTProperty.Buffer value;
	private int clientId, sequenceNumber, hash, hash0, payloadLength, payload0, payload1;

	IoTSentMessage next;
	int attempts, timestamp;

	@SecondaryThread
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
			(messageType == message.messageType) &&
			(sequenceNumber == message.sequenceNumber) &&
			(hash0 == message.hash0));
	}

	@SecondaryThread
	void fillPlaceholder_(SocketAddress socketAddress, int messageType, int sequenceNumber, byte[] payload) {
		this.socketAddress = socketAddress;
		this.messageType = messageType;
		this.sequenceNumber = sequenceNumber;
		switch (messageType) {
		case IoTMessage.MessageDescribeInterface:
			hash0 = ((payload != null && payload.length >= 1) ? (payload[0] & 0xFF) : 0);
			break;
		case IoTMessage.MessageDescribeEnum:
			hash0 = ((payload != null && payload.length >= 2) ? ((payload[0] & 0xFF) ^ ((payload[1] & 0xFF) << 1)) : 0);
			break;
		default:
			hash0 = 0;
			break;
		}
		hash = socketAddress.hashCode() ^ (messageType << 24) ^ (sequenceNumber << 8) ^ hash0;
	}

	@SecondaryThread
	boolean isSimilarGetSetPropertyMessage(IoTSentMessage sentMessage) {
		// Currently, it is not possible to send a message requesting to get/set more than one
		// property at the same time, so we just need to check payload0 and payload1 (when this
		// scenario changes, this method will have to be rewritten)
		//
		// There is no need for checking messageType because this method assumes the caller has
		// already done so
		return (payload0 == sentMessage.payload0 &&
			payload1 == sentMessage.payload1);
	}

	@SecondaryThread
	@SuppressWarnings("SynchronizeOnNonFinalField")
	int build_(byte[] dstBuffer) {
		if (password != null && password.length > IoTMessage.MaxPasswordLength)
			throw new IllegalArgumentException("0 <= password.length <= MaxPasswordLength");
		if (payloadLength < 0 || payloadLength > IoTMessage.MaxPayloadLength)
			throw new IllegalArgumentException("0 <= payloadLength <= MaxPayloadLength");
		dstBuffer[0] = IoTMessage.StartOfPacket;
		dstBuffer[1] = (byte)messageType;
		dstBuffer[2] = (byte)clientId;
		dstBuffer[3] = (byte)sequenceNumber;
		dstBuffer[4] = (byte)(sequenceNumber >>> 8);
		int dstOffset;
		if (password == null || password.length == 0) {
			dstBuffer[5] = 0;
			dstOffset = 6;
		} else {
			dstBuffer[5] = (byte)password.length;
			System.arraycopy(password, 0, dstBuffer, 6, password.length);
			dstOffset = 6 + password.length;
		}

		if (value != null) {
			// It is safe to synchronize on value because we know it will not
			// change throughout the execution of this method (it is changed
			// only when a message is created or released)
			synchronized (value) {
				final int valueLength = value.length;
				payloadLength = 4 + valueLength;
				dstBuffer[dstOffset++] = (byte)payloadLength;
				dstBuffer[dstOffset++] = (byte)(payloadLength >>> 8);
				dstBuffer[dstOffset++] = (byte)payload0;
				dstBuffer[dstOffset++] = (byte)payload1;
				dstBuffer[dstOffset++] = (byte)valueLength;
				dstBuffer[dstOffset++] = (byte)(valueLength >>> 8);
				if (valueLength != 0) {
					System.arraycopy(value.buffer, 0, dstBuffer, dstOffset, valueLength);
					dstOffset += valueLength;
				}
			}
		} else {
			switch (payloadLength) {
			case 1:
				dstBuffer[dstOffset++] = 1;
				dstBuffer[dstOffset++] = 0;
				dstBuffer[dstOffset++] = (byte)payload0;
				break;
			case 2:
				dstBuffer[dstOffset++] = 2;
				dstBuffer[dstOffset++] = 0;
				dstBuffer[dstOffset++] = (byte)payload0;
				dstBuffer[dstOffset++] = (byte)payload1;
				break;
			default:
				dstBuffer[dstOffset++] = 0;
				dstBuffer[dstOffset++] = 0;
				break;
			}
		}

		dstBuffer[dstOffset++] = IoTMessage.EndOfPacket;
		return dstOffset;
	}
}
