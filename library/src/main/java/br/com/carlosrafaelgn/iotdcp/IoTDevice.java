package br.com.carlosrafaelgn.iotdcp;

import java.net.SocketAddress;
import java.util.UUID;

public final class IoTDevice {
	private static final int FlagPasswordProtected = 0x01;
	private static final int FlagPasswordReadOnly = 0x02;
	private static final int FlagResetSupported = 0x04;
	private static final int FlagEncryptionRequired = 0x08;

	public final IoTClient client;
	public final UUID uuid;
	public final String name;

	private final int flags;
	private final IoTInterface[] ioTInterfaces;
	private final int hash;

	final SocketAddress socketAddress;
	int clientId, sequenceNumber;
	byte[] password;

	// used from a secondary thread, in a synchronized block
	private boolean hasPendingMessage;
	private IoTSentMessage firstPendingMessage, lastPendingMessage;

	public IoTDevice(IoTClient client, SocketAddress socketAddress, int flags, UUID uuid, String name, IoTInterface[] ioTInterfaces) {
		this.client = client;
		this.socketAddress = socketAddress;
		this.flags = flags;
		this.uuid = uuid;
		this.name = name;
		this.ioTInterfaces = ioTInterfaces;
		hash = socketAddress.hashCode() ^ name.hashCode();

		clientId = -1;
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
		return (o != null && (o == this || ((o instanceof IoTDevice) && ((IoTDevice)o).socketAddress.equals(socketAddress) && ((IoTDevice)o).name.equals(name))));
	}

	@IoTClient.SecondaryThread
	boolean isComplete_() {
		for (IoTInterface ioTInterface : ioTInterfaces) {
			if (ioTInterface == null || !ioTInterface.isComplete_())
				return false;
		}
		return true;
	}

	@IoTClient.SecondaryThread
	boolean canSendMessageNow_(IoTSentMessage.Cache cache, IoTSentMessage sentMessage) {
		synchronized (ioTInterfaces) {
			if (!hasPendingMessage) {
				hasPendingMessage = true;
				return true;
			}
			if (lastPendingMessage == null) {
				firstPendingMessage = sentMessage;
			} else {
				switch (sentMessage.message) {
				case IoTMessage.MessageGetProperty:
				case IoTMessage.MessageSetProperty:
					// replace repeated get/set property messages (identical interface index and
					// identical property index) with the most recent one
					IoTSentMessage previous = null, current = firstPendingMessage;
					while (current != null) {
						if (current.message == sentMessage.message &&
							current.payload0 == sentMessage.payload0 &&
							current.payload1 == sentMessage.payload1) {
							// return this message to the cache, as it will no longer be used
							cache.release_(current);
							// remove this message from the queue
							if (previous == null) {
								firstPendingMessage = current.next;
								if (firstPendingMessage == null)
									firstPendingMessage = sentMessage;
							} else {
								previous.next = current.next;
							}
							// it is not necessary to set lastPendingMessage.next to null
							// because lastPendingMessage.next will be set down below
							if (lastPendingMessage == current)
								lastPendingMessage = previous;
							break;
						}
						previous = current;
						current = current.next;
					}
					break;
				}
				if (lastPendingMessage != null)
					lastPendingMessage.next = sentMessage;
			}
			lastPendingMessage = sentMessage;
			return false;
		}
	}

	@IoTClient.SecondaryThread
	IoTSentMessage nextPendingMessage_() {
		synchronized (ioTInterfaces) {
			hasPendingMessage = false;
			if (firstPendingMessage == null)
				return null;
			final IoTSentMessage next = firstPendingMessage;
			firstPendingMessage = next.next;
			if (firstPendingMessage == null)
				lastPendingMessage = null;
			return next;
		}
	}

	@IoTClient.SecondaryThread
	void describeInterfaces_() {
		for (int i = ioTInterfaces.length - 1; i >= 0; i--)
			client.describeInterface_(this, i);
	}

	@IoTClient.SecondaryThread
	void ioTInterfaceDiscovered_(IoTInterface ioTInterface) {
		if (ioTInterfaces[ioTInterface.index] == null) {
			ioTInterfaces[ioTInterface.index] = ioTInterface;
			ioTInterface.describePropertiesEnum_();
		}
	}

	@IoTClient.SecondaryThread
	void handleDescribeEnum_(int responseCode, byte[] payload, int payloadLength) {
		if (payloadLength < 3)
			return;
		final int interfaceIndex = (payload[0] & 0xFF);
		final int propertyIndex = (payload[1] & 0xFF);
		if (interfaceIndex >= 0 && interfaceIndex < ioTInterfaces.length)
			ioTInterfaces[interfaceIndex].handleDescribeEnum_(responseCode, propertyIndex, payload, payloadLength);
	}

	void handleExecute(int responseCode, int interfaceIndex, int command, byte[] payload, int payloadLength) {
		if (interfaceIndex >= 0 && interfaceIndex < ioTInterfaces.length)
			ioTInterfaces[interfaceIndex].handleExecute(responseCode, command, payload, payloadLength);
	}

	void handleGetProperty(int responseCode, int interfaceIndex, int propertyIndex, byte[] payload, int payloadLength) {
		if (interfaceIndex >= 0 && interfaceIndex < ioTInterfaces.length)
			ioTInterfaces[interfaceIndex].handleGetProperty(responseCode, propertyIndex, payload, payloadLength);
	}

	void handleSetProperty(int responseCode, int interfaceIndex, int propertyIndex, byte[] payload, int payloadLength) {
		if (interfaceIndex >= 0 && interfaceIndex < ioTInterfaces.length)
			ioTInterfaces[interfaceIndex].handleSetProperty(responseCode, propertyIndex, payload, payloadLength);
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
		return (clientId < 0);
	}

	public void setLocalPassword(String localPassword) {
		this.password = ((!isPasswordProtected() || localPassword == null || localPassword.length() == 0) ? null : localPassword.getBytes());
	}

	public void changePassword(String password) {
		client.changePassword(this, password);
	}

	public void handshake() {
		client.handshake(this);
	}

	public void ping() {
		client.ping(this);
	}

	public void reset() {
		client.reset(this);
	}

	public void goodBye() {
		client.goodBye(this);
	}

	public void updateAllProperties() {
		for (IoTInterface ioTInterface : ioTInterfaces)
			ioTInterface.updateAllProperties();
	}
}