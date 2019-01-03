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

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class IoTClient {
	// 2570 = 0x0A0A (at the present date it is not assigned to any services)
	private static final int IoTPort = 2570;
	public static final int DefaultMaximumAttempts = 5;
	public static final int DefaultTimeoutBeforeNextAttempt = 500;
	public static final int DefaultReceiveBufferSize = 16 * IoTMessage.MaxPayloadLength;

	public interface Observer {
		void onTimeout(IoTClient client, IoTDevice device, int messageType, int userArg);
		void onException(IoTClient client, Throwable ex, int messageType, int userArg);
		void onMessageSent(IoTClient client, IoTDevice device, int messageType, int userArg);
		void onQueryDevice(IoTClient client, IoTDevice device);
		void onChangeName(IoTClient client, IoTDevice device, int responseCode, String name, int userArg);
		void onChangePassword(IoTClient client, IoTDevice device, int responseCode, String password, int userArg);
		void onHandshake(IoTClient client, IoTDevice device, int responseCode, int userArg);
		void onPing(IoTClient client, IoTDevice device, int responseCode, int userArg);
		void onReset(IoTClient client, IoTDevice device, int responseCode, int userArg);
		void onGoodBye(IoTClient client, IoTDevice device, int responseCode, int userArg);
		void onExecute(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int command, int userArg);
		void onGetProperty(IoTClient client, IoTDevice device, int responseCode, int userArg);
		void onSetProperty(IoTClient client, IoTDevice device, int responseCode, int userArg);
	}

	private final int maximumAttempts, timeoutBeforeNextAttempt;
	private Context context;
	private volatile boolean alive;
	private boolean scanningDevices;
	private DatagramSocket socket;
	private Thread clientThread, senderThread;
	private Looper senderThreadLooper;
	private Handler mainThreadHandler, senderThreadHandler;
	private final IoTMessage.Cache messageCache;
	private final IoTSentMessage.Cache sentMessageCache;
	private Observer observer;

	public IoTClient(Context context) throws IOException {
		this(context, DefaultMaximumAttempts, DefaultTimeoutBeforeNextAttempt, DefaultReceiveBufferSize);
	}

	public IoTClient(Context context, int maximumAttempts, int timeoutBeforeNextAttempt, int receiveBufferSize) throws IOException {
		if (maximumAttempts < 1 || maximumAttempts > 100)
			throw new IllegalArgumentException("1 <= maximumAttempts <= 100");
		if (timeoutBeforeNextAttempt < 500 || timeoutBeforeNextAttempt > 1200000)
			throw new IllegalArgumentException("500 <= timeoutBeforeNextAttempt <= 1200000");

		final Object senderThreadSync = new Object();
		this.context = context;
		this.maximumAttempts = maximumAttempts;
		this.timeoutBeforeNextAttempt = timeoutBeforeNextAttempt;
		alive = true;
		socket = new DatagramSocket();
		socket.setBroadcast(true);
		socket.setSoTimeout(500);
		socket.setReceiveBufferSize(receiveBufferSize);
		messageCache = new IoTMessage.Cache();
		sentMessageCache = new IoTSentMessage.Cache();
		mainThreadHandler = new Handler(new Handler.Callback() {
			@Override
			public boolean handleMessage(Message msg) {
				return handleMessageInMainThread(msg);
			}
		});
		clientThread = new Thread(new Runnable() {
			@Override
			public void run() {
				runClientThread_();
			}
		}, "IoTClient Receive Thread");
		clientThread.start();
		senderThread = new Thread(new Runnable() {
			@Override
			public void run() {
				runSenderThread_(senderThreadSync);
			}
		}, "IoTClient Send Thread");
		senderThread.start();
		synchronized (senderThreadSync) {
			while (senderThreadHandler == null) {
				try {
					senderThreadSync.wait();
				} catch (Throwable ex) {
					// Just ignore
				}
			}
		}
	}

	private InetSocketAddress getBroadcastAddress() {
		try {
			final WifiManager wifi = (WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			if (wifi == null)
				return null;
			final DhcpInfo dhcp = wifi.getDhcpInfo();
			final int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
			final byte[] quads = new byte[4];
			for (int i = 0; i < 4; i++)
				quads[i] = (byte)(broadcast >> (i << 3));
			return new InetSocketAddress(InetAddress.getByAddress(quads), IoTPort);
		} catch (Throwable ex) {
			return null;
		}
	}

	public void setObserver(Observer observer) {
		this.observer = observer;
	}

	public boolean isWaitingForResponses() {
		return sentMessageCache.isWaitingForResponses();
	}

	public void destroy() {
		alive = false;
		if (socket != null) {
			try {
				socket.close();
			} catch (Throwable ex) {
				// just ignore
			}
			socket = null;
		}
		if (senderThreadLooper != null) {
			senderThreadLooper.quit();
			senderThreadLooper = null;
		}
		if (clientThread != null) {
			clientThread.interrupt();
			try {
				clientThread.join();
			} catch (Throwable ex) {
				// Just ignore
			}
			clientThread = null;
		}
		if (senderThread != null) {
			senderThread.interrupt();
			try {
				senderThread.join();
			} catch (Throwable ex) {
				// Just ignore
			}
			senderThread = null;
		}
		mainThreadHandler = null;
		senderThreadHandler = null;
		context = null;
		observer = null;
	}

	private boolean handleMessageInMainThread(Message msg) {
		if (!alive)
			return true;
		switch (msg.what) {
		case IoTMessage.MessageTimeout:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onTimeout(this, (IoTDevice)msg.obj, msg.arg1, msg.arg2);
			break;
		case IoTMessage.MessageException:
			if (observer != null && (msg.obj instanceof Throwable))
				observer.onException(this, (Throwable)msg.obj, msg.arg1, msg.arg2);
			break;
		case IoTMessage.MessageSent:
			if (observer != null && ((msg.obj instanceof IoTDevice) || msg.obj == null))
				observer.onMessageSent(this, (IoTDevice)msg.obj, msg.arg1, msg.arg2);
			break;
		case IoTMessage.MessageQueryDevice:
			if (msg.obj == null)
				scanningDevices = false;
			if (observer != null && ((msg.obj instanceof IoTDevice) || msg.obj == null))
				observer.onQueryDevice(this, (IoTDevice)msg.obj);
			break;
		case IoTMessage.MessageChangeName:
			if (observer != null) {
				if (msg.obj instanceof IoTMessage) {
					final IoTMessage message = (IoTMessage)msg.obj;
					final IoTDevice device = message.device;
					final int responseCode = message.responseCode;
					final byte[] name = message.password;
					messageCache.release_(message);
					device.name = ((name == null || name.length == 0) ? "IoT" : new String(name));
					observer.onChangeName(this, device, responseCode, device.name, msg.arg2);
				} else if (msg.obj instanceof IoTDevice) {
					observer.onChangeName(this, (IoTDevice)msg.obj, msg.arg1, null, msg.arg2);
				}
			} else if (msg.obj instanceof IoTMessage) {
				messageCache.release_((IoTMessage)msg.obj);
			}
			break;
		case IoTMessage.MessageChangePassword:
			if (observer != null) {
				if (msg.obj instanceof IoTMessage) {
					final IoTMessage message = (IoTMessage)msg.obj;
					final IoTDevice device = message.device;
					final int responseCode = message.responseCode;
					final byte[] password = message.password;
					messageCache.release_(message);
					observer.onChangePassword(this, device, responseCode, (password == null) ? null : new String(password), msg.arg2);
				} else if (msg.obj instanceof IoTDevice) {
					observer.onChangePassword(this, (IoTDevice)msg.obj, msg.arg1, null, msg.arg2);
				}
			} else if (msg.obj instanceof IoTMessage) {
				messageCache.release_((IoTMessage)msg.obj);
			}
			break;
		case IoTMessage.MessageHandshake:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onHandshake(this, (IoTDevice)msg.obj, msg.arg1, msg.arg2);
			break;
		case IoTMessage.MessagePing:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onPing(this, (IoTDevice)msg.obj, msg.arg1, msg.arg2);
			break;
		case IoTMessage.MessageReset:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onReset(this, (IoTDevice)msg.obj, msg.arg1, msg.arg2);
			break;
		case IoTMessage.MessageGoodBye:
			if (observer != null && (msg.obj instanceof IoTDevice)) {
				final IoTDevice device = (IoTDevice)msg.obj;
				device.clientId = IoTMessage.InvalidClientId;
				observer.onGoodBye(this, device, msg.arg1, msg.arg2);
			}
			break;
		case IoTMessage.MessageExecute:
			if (msg.obj instanceof IoTMessage) {
				final IoTMessage message = (IoTMessage)msg.obj;
				final IoTDevice device = message.device;
				final int responseCode = message.responseCode;
				final int interfaceIndex = (msg.arg1 & 0xFF);
				final int command = (msg.arg1 >>> 8);
				final byte[] payload = message.payload;
				final int payloadLength = message.payloadLength;
				messageCache.release_(message);
				device.handleExecute(responseCode, interfaceIndex, command, payload, payloadLength, msg.arg2);
				if (observer != null)
					observer.onExecute(this, device, responseCode, interfaceIndex, command, msg.arg2);
			}
			break;
		case IoTMessage.MessageGetProperty:
			if (msg.obj instanceof IoTMessage) {
				final IoTMessage message = (IoTMessage)msg.obj;
				final IoTDevice device = message.device;
				final int responseCode = message.responseCode;
				final byte[] payload = message.payload;
				final int payloadLength = message.payloadLength;
				messageCache.release_(message);
				if (responseCode == IoTMessage.ResponseOK)
					device.handleProperty(payload, payloadLength, msg.arg2);
				if (observer != null)
					observer.onGetProperty(this, device, responseCode, msg.arg2);
			}
			break;
		case IoTMessage.MessageSetProperty:
			if (msg.obj instanceof IoTMessage) {
				final IoTMessage message = (IoTMessage)msg.obj;
				final IoTDevice device = message.device;
				final int responseCode = message.responseCode;
				final byte[] payload = message.payload;
				final int payloadLength = message.payloadLength;
				messageCache.release_(message);
				if (responseCode == IoTMessage.ResponseOK)
					device.handleProperty(payload, payloadLength, msg.arg2);
				if (observer != null)
					observer.onSetProperty(this, device, responseCode, msg.arg2);
			}
			break;
		}
		return true;
	}

	@SecondaryThread
	private IoTSentMessage[] checkPendingAttempts_(HashMap<SocketAddress, IoTDevice> pendingDevices, IoTSentMessage[] sentMessagesLocalCopy) {
		// Check if there are pending messages that should be resent, or just discarded
		sentMessagesLocalCopy = sentMessageCache.copySentMessages_(sentMessagesLocalCopy);

		final int now = (int)SystemClock.elapsedRealtime();
		IoTSentMessage sentMessage;
		int i;
		for (i = 0; i < sentMessagesLocalCopy.length && (sentMessage = sentMessagesLocalCopy[i]) != null && alive; i++) {
			if ((now - sentMessage.timestamp) >= timeoutBeforeNextAttempt) {
				final int maximumAttempts;

				switch (sentMessage.messageType) {
				case IoTMessage.MessageExecute:
				case IoTMessage.MessageGetProperty:
				case IoTMessage.MessageSetProperty:
					maximumAttempts = this.maximumAttempts;
					break;
				default:
					// Special messages must be retried at least a few times
					maximumAttempts = (this.maximumAttempts <= DefaultMaximumAttempts ? DefaultMaximumAttempts : this.maximumAttempts);
					break;
				}

				if (sentMessage.attempts >= maximumAttempts) {
					// Copy whatever could be useful, and release the message before proceeding
					final SocketAddress socketAddress = sentMessage.socketAddress;
					final IoTDevice device = sentMessage.device;
					final int messageType = sentMessage.messageType;
					final int userArg = sentMessage.userArg;
					sentMessageCache.unmarkAsSentMessageAndRelease_(sentMessage);

					// Now that we are giving up on this message, try to send the next one
					if (device != null)
						sendNextMessageInDeviceQueue_(device);

					switch (messageType) {
					case IoTMessage.MessageQueryDevice:
						mainThreadHandler.sendEmptyMessage(IoTMessage.MessageQueryDevice);
						break;

					case IoTMessage.MessageDescribeInterface:
						pendingDevices.remove(socketAddress);
						break;

					case IoTMessage.MessageChangeName:
					case IoTMessage.MessageChangePassword:
					case IoTMessage.MessageHandshake:
					case IoTMessage.MessagePing:
					case IoTMessage.MessageReset:
					case IoTMessage.MessageGoodBye:
					case IoTMessage.MessageExecute:
					case IoTMessage.MessageGetProperty:
					case IoTMessage.MessageSetProperty:
						mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, IoTMessage.MessageTimeout, messageType, userArg, device));
						break;
					}
				} else {
					// Try again...
					sendMessage_(sentMessage);
				}
			}
		}

		Arrays.fill(sentMessagesLocalCopy, 0, i, null);

		return sentMessagesLocalCopy;
	}

	@SecondaryThread
	@SuppressWarnings("ConstantConditions")
	private void runClientThread_() {
		final byte[] buffer = IoTMessage.allocateMaximumResponseBuffer_();
		final DatagramPacket recvPacket = new DatagramPacket(buffer, buffer.length);
		final SocketAddress broadcastAddress = getBroadcastAddress();
		final HashMap<SocketAddress, IoTDevice> devices = new HashMap<>(16);
		final HashMap<SocketAddress, IoTDevice> pendingDevices = new HashMap<>(16);
		final IoTSentMessage placeholder = sentMessageCache.placeholder_(broadcastAddress);
		IoTSentMessage[] sentMessagesLocalCopy = new IoTSentMessage[16];

		while (alive) {
			IoTMessage message = null;

			int messageType = IoTMessage.MessageException, userArg = 0;

			try {
				sentMessagesLocalCopy = checkPendingAttempts_(pendingDevices, sentMessagesLocalCopy);

				recvPacket.setData(buffer);
				socket.receive(recvPacket);

				if (((InetSocketAddress)recvPacket.getSocketAddress()).getAddress().getHostAddress().equals("192.168.1.4"))
					continue;

				message = messageCache.parseResponse_(recvPacket.getData(), recvPacket.getLength());

				if (!alive || message == null)
					continue;

				final SocketAddress socketAddress = recvPacket.getSocketAddress();
				final IoTSentMessage sentMessage;
				placeholder.fillPlaceholder_((message.messageType == IoTMessage.MessageQueryDevice) ? broadcastAddress : socketAddress, message.messageType, message.sequenceNumber, message.payload);

				sentMessage = sentMessageCache.getActualSentMessage_(placeholder);

				if (sentMessage == null)
					continue;

				IoTDevice device = null;
				Message messageToSendToMainThread = null;

				try {
					userArg = sentMessage.userArg;

					switch (messageType = sentMessage.messageType) {
					case IoTMessage.MessageQueryDevice:
						// A new device has arrived
						if (devices.containsKey(socketAddress) || pendingDevices.containsKey(socketAddress))
							break;
						// We must not use device here, because that would give a different meaning
						// to device in the finally block at the end of this try block
						final IoTDevice newDevice = message.parseQueryDevice_(this, socketAddress);
						if (newDevice == null)
							break;
						pendingDevices.put(socketAddress, newDevice);
						newDevice.describeInterfaces_();
						break;

					default:
						device = sentMessage.device;
						if (device == null)
							break;

						switch (sentMessage.messageType) {
						case IoTMessage.MessageDescribeInterface:
							final IoTInterface ioTInterface = message.parseDescribeInterface_(device);
							if (ioTInterface == null ||
								ioTInterface.index < 0 ||
								ioTInterface.index >= device.ioTInterfaceCount())
								break;
							device.ioTInterfaceDiscovered_(ioTInterface);
							if (device.isComplete_()) {
								// This device is ready to be used!
								pendingDevices.remove(socketAddress);
								devices.put(socketAddress, device);
								messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageQueryDevice, device);
							}
							break;

						case IoTMessage.MessageDescribeEnum:
							device.handleDescribeEnum_(message.responseCode, message.payload, message.payloadLength);
							if (device.isComplete_()) {
								// This device is ready to be used!
								pendingDevices.remove(socketAddress);
								devices.put(socketAddress, device);
								messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageQueryDevice, device);
							}
							break;

						case IoTMessage.MessageChangeName:
							message.device = device;
							message.password = sentMessage.password;
							messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageChangeName, 0, sentMessage.userArg, message);
							message = null; // Do not release this message here
							break;

						case IoTMessage.MessageChangePassword:
							message.device = device;
							message.password = sentMessage.password;
							messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageChangePassword, 0, sentMessage.userArg, message);
							message = null; // Do not release this message here
							break;

						case IoTMessage.MessageHandshake:
							device.sequenceNumber = 0;
							device.clientId = message.parseHandshake_();
							messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageHandshake, message.responseCode, sentMessage.userArg, device);
							break;

						case IoTMessage.MessagePing:
						case IoTMessage.MessageReset:
						case IoTMessage.MessageGoodBye:
							messageToSendToMainThread = Message.obtain(mainThreadHandler, sentMessage.messageType, message.responseCode, sentMessage.userArg, device);
							break;

						case IoTMessage.MessageExecute:
							message.device = device;
							messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageExecute, sentMessage.executedInterfaceIndex | (sentMessage.executedCommand << 8), sentMessage.userArg, message);
							message = null; // Do not release this message here
							break;

						case IoTMessage.MessageGetProperty:
						case IoTMessage.MessageSetProperty:
							message.device = device;
							messageToSendToMainThread = Message.obtain(mainThreadHandler, sentMessage.messageType, 0, sentMessage.userArg, message);
							message = null; // Do not release this message here
							break;
						}
					}
				} finally {
					// Do not remove query device messages (let them timeout).
					// Also, we must first release the old message/send the new one,
					// before sending the message to the main thread, to guarantee
					// that isWaitingForResponses() reflects the correct scenario!
					if (messageType != IoTMessage.MessageQueryDevice)
						sentMessageCache.unmarkAsSentMessageAndRelease_(sentMessage);
					if (device != null)
						sendNextMessageInDeviceQueue_(device);
					if (messageToSendToMainThread != null)
						mainThreadHandler.sendMessage(messageToSendToMainThread);
				}
			} catch (SocketTimeoutException ex) {
				// Just ignore, we will try again later
			} catch (Throwable ex) {
				mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, IoTMessage.MessageException, messageType, userArg, ex));
			} finally {
				if (message != null)
					messageCache.release_(message);
			}
		}
	}

	@SecondaryThread
	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
	private void runSenderThread_(Object senderThreadSync) {
		final byte[] buffer = IoTMessage.allocateMaximumRequestBuffer_();
		final DatagramPacket sentPacket = new DatagramPacket(buffer, buffer.length);
		Looper.prepare();
		senderThreadLooper = Looper.myLooper();
		senderThreadHandler = new Handler(new Handler.Callback() {
			@Override
			public boolean handleMessage(Message msg) {
				if (!alive || !(msg.obj instanceof IoTSentMessage))
					return true;
				final IoTSentMessage sentMessage = (IoTSentMessage)msg.obj;
				int messageType = IoTMessage.MessageException, userArg = 0;
				try {
					final IoTDevice device = sentMessage.device;
					messageType = sentMessage.messageType;
					userArg = sentMessage.userArg;
					sentPacket.setSocketAddress(sentMessage.socketAddress);
					sentPacket.setData(buffer, 0, sentMessage.build_(buffer));
					sentMessageCache.markAsSentMessage_(sentMessage);
					socket.send(sentPacket);
					if (msg.what != 0)
						mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, IoTMessage.MessageSent, messageType, userArg, device));
				} catch (Throwable ex) {
					sentMessageCache.unmarkAsSentMessageAndRelease_(sentMessage);
					final Handler h = mainThreadHandler;
					if (h != null)
						h.sendMessage(Message.obtain(h, IoTMessage.MessageException, messageType, userArg, ex));
				}
				return true;
			}
		});
		synchronized (senderThreadSync) {
			senderThreadSync.notify();
		}
		Looper.loop();
	}

	@SecondaryThread
	private void doSendMessage_(IoTSentMessage sentMessage, boolean notifyObserver, boolean skipQueue) {
		if (skipQueue ||
			sentMessage.device == null ||
			sentMessage.device.canSendMessageNowAndIfNotEnqueue_(sentMessageCache, sentMessage)) {
			sentMessage.attempts++;
			sentMessage.timestamp = (int)SystemClock.elapsedRealtime();
			senderThreadHandler.sendMessage(Message.obtain(senderThreadHandler, notifyObserver ? 1 : 0, sentMessage));
		}
	}

	@SecondaryThread
	private void sendMessage_(IoTSentMessage sentMessage) {
		doSendMessage_(sentMessage,
			sentMessage.attempts == 0, // Notify the observer only when a message is sent for the first time
			sentMessage.attempts > 0); // Messages being retried must not be enqueued again
	}

	@SecondaryThread
	private void sendNextMessageInDeviceQueue_(IoTDevice device) {
		final IoTSentMessage sentMessage;
		if ((sentMessage = device.markSentMessageAsReceivedAndGetNextMessageInQueue_()) != null)
			doSendMessage_(sentMessage, true, true);
	}

	private boolean sendMessage(IoTSentMessage sentMessage) {
		if (!alive || senderThreadHandler == null)
			return false;
		doSendMessage_(sentMessage, true, false);
		return true;
	}

	public boolean isScanningDevices() {
		return scanningDevices;
	}

	public boolean scanDevices() {
		return (!scanningDevices && (scanningDevices = sendMessage(sentMessageCache.queryDevice(getBroadcastAddress()))));
	}

	@SecondaryThread
	void describeInterface_(IoTDevice device, int interfaceIndex) {
		sendMessage_(sentMessageCache.describeInterface_(device, interfaceIndex));
	}

	@SecondaryThread
	void describeEnum_(IoTProperty property) {
		sendMessage_(sentMessageCache.describeEnum_(property.ioTInterface, property.index));
	}

	boolean changePassword(IoTDevice device, String password, int userArg) {
		return sendMessage(sentMessageCache.changePassword(device, password, userArg));
	}

	boolean handshake(IoTDevice device, int userArg) {
		return sendMessage(sentMessageCache.handshake(device, userArg));
	}

	boolean ping(IoTDevice device, int userArg) {
		return sendMessage(sentMessageCache.ping(device, userArg));
	}

	boolean reset(IoTDevice device, int userArg) {
		return sendMessage(sentMessageCache.reset(device, userArg));
	}

	boolean goodBye(IoTDevice device, int userArg) {
		return sendMessage(sentMessageCache.goodBye(device, userArg));
	}

	boolean execute(IoTInterface ioTInterface, int command, int userArg) {
		return sendMessage(sentMessageCache.execute(ioTInterface, command, userArg));
	}

	boolean getProperty(IoTProperty property, int userArg) {
		return sendMessage(sentMessageCache.getProperty(property.ioTInterface, property.index, userArg));
	}

	boolean setProperty(IoTProperty property, IoTProperty.Buffer value, int userArg) {
		return sendMessage(sentMessageCache.setProperty(property.ioTInterface, property.index, value, userArg));
	}
}
