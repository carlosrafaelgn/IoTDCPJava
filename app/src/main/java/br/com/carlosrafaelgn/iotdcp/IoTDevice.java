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
public final class IoTDevice {
	private static final int FlagNameReadOnly = 0x01;
	private static final int FlagPasswordProtected = 0x02;
	private static final int FlagPasswordReadOnly = 0x04;
	private static final int FlagResetSupported = 0x08;
	private static final int FlagEncryptionRequired = 0x10;

	public final IoTClient client;
	public final UUID categoryUuid, uuid;
	public String name;

	public Object userTag;

	private final int flags;
	private final IoTInterface[] ioTInterfaces;
	private final int hash;

	final SocketAddress socketAddress;
	int clientId, sequenceNumber;
	byte[] password;

	// Used from a secondary thread, in a synchronized block
	private boolean isSentMessageWaitingToBeReceived;
	private IoTSentMessage firstEnqueuedMessage, lastEnqueuedMessage;

	public IoTDevice(IoTClient client, SocketAddress socketAddress, int flags, UUID categoryUuid, UUID uuid, String name, IoTInterface[] ioTInterfaces) {
		this.client = client;
		this.socketAddress = socketAddress;
		this.flags = flags;
		this.categoryUuid = categoryUuid;
		this.uuid = uuid;
		this.name = name;
		this.ioTInterfaces = ioTInterfaces;
		hash = socketAddress.hashCode();

		clientId = IoTMessage.InvalidClientId;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object o) {
		return (o != null && (o == this || ((o instanceof IoTDevice) && ((IoTDevice)o).socketAddress.equals(socketAddress))));
	}

	@SecondaryThread
	boolean isComplete_() {
		for (IoTInterface ioTInterface : ioTInterfaces) {
			if (ioTInterface == null || !ioTInterface.isComplete_())
				return false;
		}
		return true;
	}

	@SecondaryThread
	boolean canSendMessageNowAndIfNotEnqueue_(IoTSentMessage.Cache cache, IoTSentMessage sentMessage) {
		synchronized (ioTInterfaces) {
			if (!isSentMessageWaitingToBeReceived) {
				isSentMessageWaitingToBeReceived = true;
				return true;
			}
			if (firstEnqueuedMessage == null) {
				firstEnqueuedMessage = sentMessage;
			} else {
				switch (sentMessage.messageType) {
				case IoTMessage.MessageGetProperty:
				case IoTMessage.MessageSetProperty:
					// Replace repeated get/set property messages with the most recent one
					IoTSentMessage previous = null, current = firstEnqueuedMessage;
					while (current != null) {
						if (current.messageType == sentMessage.messageType &&
							current.isSimilarGetSetPropertyMessage(sentMessage)) {
							// Is this message already enqueued?
							if (current == sentMessage)
								return false;
							// Return this message to the cache, as it will no longer be used
							cache.release_(current);
							// Remove this message from the queue
							if (previous == null) {
								firstEnqueuedMessage = current.next;
								// If current was the only message in the queue, then sentMessage
								// is now the new first message in the queue
								if (firstEnqueuedMessage == null)
									firstEnqueuedMessage = sentMessage;
							} else {
								previous.next = current.next;
							}
							// It is not necessary to set lastEnqueuedMessage.next to null
							// because lastEnqueuedMessage.next will be set down below
							if (lastEnqueuedMessage == current)
								lastEnqueuedMessage = previous;
							break;
						}
						previous = current;
						current = current.next;
					}
					break;
				}
				if (lastEnqueuedMessage != null)
					lastEnqueuedMessage.next = sentMessage;
			}
			lastEnqueuedMessage = sentMessage;
			sentMessage.next = null;
			return false;
		}
	}

	@SecondaryThread
	IoTSentMessage markSentMessageAsReceivedAndGetNextMessageInQueue_() {
		synchronized (ioTInterfaces) {
			if (firstEnqueuedMessage == null) {
				isSentMessageWaitingToBeReceived = false;
				return null;
			}
			isSentMessageWaitingToBeReceived = true;
			final IoTSentMessage next = firstEnqueuedMessage;
			firstEnqueuedMessage = next.next;
			if (firstEnqueuedMessage == null)
				lastEnqueuedMessage = null;
			return next;
		}
	}

	@SecondaryThread
	void describeInterfaces_() {
		for (int i = ioTInterfaces.length - 1; i >= 0; i--)
			client.describeInterface_(this, i);
	}

	@SecondaryThread
	void ioTInterfaceDiscovered_(IoTInterface ioTInterface) {
		if (ioTInterfaces[ioTInterface.index] == null) {
			ioTInterfaces[ioTInterface.index] = ioTInterface;
			ioTInterface.describePropertiesEnum_();
		}
	}

	@SecondaryThread
	void handleDescribeEnum_(int responseCode, byte[] payload, int payloadLength) {
		if (payloadLength < 3)
			return;
		final int interfaceIndex = (payload[0] & 0xFF);
		final int propertyIndex = (payload[1] & 0xFF);
		if (interfaceIndex < ioTInterfaces.length)
			ioTInterfaces[interfaceIndex].handleDescribeEnum_(responseCode, propertyIndex, payload, payloadLength);
	}

	void handleExecute(int responseCode, int interfaceIndex, int command, byte[] payload, int payloadLength, int userArg) {
		if (interfaceIndex < ioTInterfaces.length)
			ioTInterfaces[interfaceIndex].handleExecute(responseCode, command, payload, payloadLength, userArg);
	}

	void handleProperty(byte[] payload, int payloadLength, int userArg) {
		if (payload == null)
			return;
		int payloadOffset = 0;
		while ((payloadLength - payloadOffset) >= 4) {
			final int interfaceIndex = (payload[payloadOffset++] & 0xFF);
			final int propertyIndex = (payload[payloadOffset++] & 0xFF);
			final int propertyPayloadLength = (payload[payloadOffset++] & 0xFF) | ((payload[payloadOffset++] & 0xFF) << 8);
			if ((payloadOffset + propertyPayloadLength) > payloadLength)
				break;
			if (interfaceIndex < ioTInterfaces.length)
				ioTInterfaces[interfaceIndex].handleProperty(propertyIndex, payload, payloadOffset, propertyPayloadLength, userArg);
			payloadOffset += propertyPayloadLength;
		}
	}

	int nextSequenceNumber() {
		return (this.sequenceNumber = ((this.sequenceNumber + 1) & IoTMessage.MaximumSequenceNumber));
	}

	public int ioTInterfaceCount() {
		return ioTInterfaces.length;
	}

	public IoTInterface ioTInterface(int interfaceIndex) {
		return ioTInterfaces[interfaceIndex];
	}

	public boolean isNameReadOnly() {
		return ((flags & FlagNameReadOnly) != 0);
	}

	public boolean isPasswordProtected() {
		return ((flags & FlagPasswordProtected) != 0);
	}

	public boolean isPasswordReadOnly() {
		return ((flags & FlagPasswordReadOnly) != 0);
	}

	public boolean isResetSupported() {
		return ((flags & FlagResetSupported) != 0);
	}

	public boolean needsHandshake() {
		return (clientId == IoTMessage.InvalidClientId);
	}

	public void setLocalPassword(String localPassword) {
		this.password = ((!isPasswordProtected() || localPassword == null || localPassword.length() == 0) ? null : localPassword.getBytes());
	}

	public boolean changePassword(String password) {
		return client.changePassword(this, password, 0);
	}

	public boolean changePassword(String password, int userArg) {
		return client.changePassword(this, password, userArg);
	}

	public boolean handshake() {
		return client.handshake(this, 0);
	}

	public boolean handshake(int userArg) {
		return client.handshake(this, userArg);
	}

	public boolean ping() {
		return client.ping(this, 0);
	}

	public boolean ping(int userArg) {
		return client.ping(this, userArg);
	}

	public boolean reset() {
		return client.reset(this, 0);
	}

	public boolean reset(int userArg) {
		return client.reset(this, userArg);
	}

	public boolean goodBye() {
		return client.goodBye(this, 0);
	}

	public boolean goodBye(int userArg) {
		return client.goodBye(this, userArg);
	}

	public boolean updateAllProperties() {
		return updateAllProperties(0);
	}

	public boolean updateAllProperties(int userArg) {
		boolean ok = true;
		for (IoTInterface ioTInterface : ioTInterfaces)
			ok &= ioTInterface.updateAllProperties(userArg);
		return ok;
	}
}
