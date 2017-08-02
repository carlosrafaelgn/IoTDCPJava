package br.com.carlosrafaelgn.iotdcp;

import android.app.Application;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;

public final class IoTClient {
	// 2570 = 0x0A0A (at the present date it is not assigned to any services)
	private static final int IoTPort = 2570;
	private static final int DefaultMaximumAttempts = 3;
	private static final int DefaultTimeoutBeforeNextAttempt = 2500; //999000; //2500;
	private static final int DefaultReceiveBufferSize = 100 * IoTMessage.MaxPayloadLengthEscaped;

	@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
	@Retention(RetentionPolicy.SOURCE)
	@interface SecondaryThread {
	}

	@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
	@Retention(RetentionPolicy.SOURCE)
	@interface MixedThreads {
	}

	public interface Observer {
		void onException(IoTClient client, Throwable ex);
		void onMessageSent(IoTClient client, IoTDevice device, int message);
		void onQueryDevice(IoTClient client, IoTDevice device);
		void onChangePassword(IoTClient client, IoTDevice device, int responseCode, String password);
		void onHandshake(IoTClient client, IoTDevice device, int responseCode);
		void onPing(IoTClient client, IoTDevice device, int responseCode);
		void onReset(IoTClient client, IoTDevice device, int responseCode);
		void onGoodBye(IoTClient client, IoTDevice device, int responseCode);
		void onExecute(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int command);
		void onGetProperty(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int propertyIndex);
		void onSetProperty(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int propertyIndex);
	}

	private final int maximumAttempts, timeoutBeforeNextAttempt;
	private Application context;
	private volatile boolean alive;
	private DatagramSocket socket;
	private Thread clientThread, senderThread;
	private Looper senderThreadLooper;
	private Handler mainThreadHandler, senderThreadHandler;
	private final IoTMessage.Cache messageCache;
	private final IoTSentMessage.Cache sentMessageCache;
	private Observer observer;

	public IoTClient(Application context) throws IOException {
		this(context, DefaultMaximumAttempts, DefaultTimeoutBeforeNextAttempt, DefaultReceiveBufferSize);
	}

	public IoTClient(Application context, int maximumAttempts, int timeoutBeforeNextAttempt, int receiveBufferSize) throws IOException {
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
			if (senderThreadHandler == null) {
				try {
					senderThreadSync.wait();
				} catch (Throwable ex) {
					// just ignore
				}
			}
		}
	}

	private InetSocketAddress getBroadcastAddress() {
		try {
			final WifiManager wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
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
				// just ignore
			}
			clientThread = null;
		}
		if (senderThread != null) {
			senderThread.interrupt();
			try {
				senderThread.join();
			} catch (Throwable ex) {
				// just ignore
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
		case IoTMessage.ClientMessageException:
			if (observer != null && (msg.obj instanceof Throwable))
				observer.onException(this, (Throwable)msg.obj);
			break;
		case IoTMessage.ClientMessageMessageSent:
			if (observer != null && ((msg.obj instanceof IoTDevice) || msg.obj == null))
				observer.onMessageSent(this, (IoTDevice)msg.obj, msg.arg1);
			break;
		case IoTMessage.MessageQueryDevice:
			if (observer != null && ((msg.obj instanceof IoTDevice) || msg.obj == null))
				observer.onQueryDevice(this, (IoTDevice)msg.obj);
			break;
		case IoTMessage.MessageChangePassword:
			if (observer != null) {
				if (msg.obj instanceof IoTMessage) {
					final IoTMessage message = (IoTMessage)msg.obj;
					final IoTDevice device = message.device;
					final int responseCode = message.responseCode;
					final byte[] password = message.password;
					messageCache.release_(message);
					observer.onChangePassword(this, device, responseCode, (password == null) ? null : new String(password));
				} else if (msg.obj instanceof IoTDevice) {
					observer.onChangePassword(this, (IoTDevice)msg.obj, msg.arg1, null);
				}
			} else if (msg.obj instanceof IoTMessage) {
				messageCache.release_((IoTMessage)msg.obj);
			}
			break;
		case IoTMessage.MessageHandshake:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onHandshake(this, (IoTDevice)msg.obj, msg.arg1);
			break;
		case IoTMessage.MessagePing:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onPing(this, (IoTDevice)msg.obj, msg.arg1);
			break;
		case IoTMessage.MessageReset:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onReset(this, (IoTDevice)msg.obj, msg.arg1);
			break;
		case IoTMessage.MessageGoodBye:
			if (observer != null && (msg.obj instanceof IoTDevice))
				observer.onGoodBye(this, (IoTDevice)msg.obj, msg.arg1);
			break;
		case IoTMessage.MessageExecute:
			if (msg.obj instanceof IoTMessage) {
				final IoTMessage message = (IoTMessage)msg.obj;
				final IoTDevice device = message.device;
				final int responseCode = message.responseCode;
				final int interfaceIndex = message.interfaceIndex;
				final int commandOrPropertyIndex = message.commandOrPropertyIndex;
				final byte[] payload = message.payload;
				final int payloadLength = message.payloadLength;
				messageCache.release_(message);
				device.handleExecute(responseCode, interfaceIndex, commandOrPropertyIndex, payload, payloadLength);
				if (observer != null)
					observer.onExecute(this, device, responseCode, interfaceIndex, commandOrPropertyIndex);
			}
			break;
		case IoTMessage.MessageGetProperty:
			if (msg.obj instanceof IoTMessage) {
				final IoTMessage message = (IoTMessage)msg.obj;
				final IoTDevice device = message.device;
				final int responseCode = message.responseCode;
				final int interfaceIndex = message.interfaceIndex;
				final int commandOrPropertyIndex = message.commandOrPropertyIndex;
				final byte[] payload = message.payload;
				final int payloadLength = message.payloadLength;
				messageCache.release_(message);
				device.handleGetProperty(responseCode, interfaceIndex, commandOrPropertyIndex, payload, payloadLength);
				if (observer != null)
					observer.onGetProperty(this, device, responseCode, interfaceIndex, commandOrPropertyIndex);
			}
			break;
		case IoTMessage.MessageSetProperty:
			if (msg.obj instanceof IoTMessage) {
				final IoTMessage message = (IoTMessage)msg.obj;
				final IoTDevice device = message.device;
				final int responseCode = message.responseCode;
				final int interfaceIndex = message.interfaceIndex;
				final int commandOrPropertyIndex = message.commandOrPropertyIndex;
				final byte[] payload = message.payload;
				final int payloadLength = message.payloadLength;
				messageCache.release_(message);
				device.handleSetProperty(responseCode, interfaceIndex, commandOrPropertyIndex, payload, payloadLength);
				if (observer != null)
					observer.onSetProperty(this, device, responseCode, interfaceIndex, commandOrPropertyIndex);
			}
			break;
		}
		return true;
	}

	@SecondaryThread
	private IoTSentMessage[] checkPendingAttempts_(HashMap<SocketAddress, IoTDevice> pendingDevices, IoTSentMessage[] sentMessagesLocalCopy) {
		// check if there are pending messages that should be resent, or just discarded
		sentMessagesLocalCopy = sentMessageCache.copySentMessages_(sentMessagesLocalCopy);

		final int now = (int)SystemClock.elapsedRealtime();
		IoTSentMessage sentMessage;
		int i;
		for (i = 0; i < sentMessagesLocalCopy.length && (sentMessage = sentMessagesLocalCopy[i]) != null && alive; i++) {
			if ((now - sentMessage.timestamp) >= timeoutBeforeNextAttempt) {
				final int maximumAttempts;

				switch (sentMessage.message) {
				case IoTMessage.MessageExecute:
				case IoTMessage.MessageGetProperty:
				case IoTMessage.MessageSetProperty:
					maximumAttempts = this.maximumAttempts;
					break;
				default:
					// special messages must be retried at least a few times
					maximumAttempts = (this.maximumAttempts <= DefaultMaximumAttempts ? DefaultMaximumAttempts : this.maximumAttempts);
					break;
				}

				if (sentMessage.attempts >= maximumAttempts) {
					// copy whatever could be useful, and release the message before proceeding
					final SocketAddress socketAddress = sentMessage.socketAddress;
					final IoTDevice device = sentMessage.device;
					final int message = sentMessage.message;
					final int payload0 = sentMessage.payload0;
					final int payload1 = sentMessage.payload1;
					sentMessageCache.unmarkAsSentMessageAndRelease_(sentMessage);

					// now that we are giving up on this message, try to send the next one
					final IoTSentMessage nextMessage;
					if (device != null && (nextMessage = device.nextPendingMessage_()) != null)
						sendMessage_(nextMessage);

					switch (message) {
					case IoTMessage.MessageQueryDevice:
						mainThreadHandler.sendEmptyMessage(IoTMessage.MessageQueryDevice);
						break;

					case IoTMessage.MessageDescribeInterface:
						pendingDevices.remove(socketAddress);
						break;

					case IoTMessage.MessageChangePassword:
					case IoTMessage.MessageHandshake:
					case IoTMessage.MessagePing:
					case IoTMessage.MessageReset:
					case IoTMessage.MessageGoodBye:
						mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, message, IoTMessage.ResponseTimeout, 0, device));
						break;

					case IoTMessage.MessageExecute:
					case IoTMessage.MessageGetProperty:
					case IoTMessage.MessageSetProperty:
						mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, message, messageCache.timeout_(device, payload0, payload1)));
						break;
					}
				} else {
					// try again...
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

			try {
				sentMessagesLocalCopy = checkPendingAttempts_(pendingDevices, sentMessagesLocalCopy);

				recvPacket.setData(buffer);
				socket.receive(recvPacket);

				message = messageCache.parseResponse_(recvPacket.getData(), recvPacket.getLength());

				if (!alive || message == null)
					continue;

				final SocketAddress socketAddress = recvPacket.getSocketAddress();
				final IoTSentMessage sentMessage;
				placeholder.fillPlaceholder_((message.clientId == IoTMessage.ClientIdQueryDevice) ? broadcastAddress : socketAddress, message.clientId, message.sequenceNumber, message.payload);

				sentMessage = sentMessageCache.getActualSentMessage_(placeholder);

				if (sentMessage == null)
					continue;

				IoTDevice device = null;
				Message messageToSendToMainThread = null;

				try {
					switch (sentMessage.message) {
					case IoTMessage.MessageQueryDevice:
						// a new device has arrived
						if (devices.containsKey(socketAddress) || pendingDevices.containsKey(socketAddress))
							break;
						device = message.parseQueryDevice_(this, socketAddress);
						if (device == null)
							break;
						pendingDevices.put(socketAddress, device);
						device.describeInterfaces_();
						break;

					default:
						device = sentMessage.device;
						if (device == null)
							break;

						switch (sentMessage.message) {
						case IoTMessage.MessageDescribeInterface:
							final IoTInterface ioTInterface = message.parseDescribeInterface_(device);
							if (ioTInterface == null ||
								ioTInterface.index < 0 ||
								ioTInterface.index >= device.ioTInterfaceCount())
								break;
							device.ioTInterfaceDiscovered_(ioTInterface);
							if (device.isComplete_()) {
								// this device is ready to be used!
								pendingDevices.remove(socketAddress);
								devices.put(socketAddress, device);
								messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageQueryDevice, device);
							}
							break;

						case IoTMessage.MessageDescribeEnum:
							device.handleDescribeEnum_(message.responseCode, message.payload, message.payloadLength);
							if (device.isComplete_()) {
								// this device is ready to be used!
								pendingDevices.remove(socketAddress);
								devices.put(socketAddress, device);
								messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageQueryDevice, device);
							}
							break;

						case IoTMessage.MessageChangePassword:
							message.device = device;
							message.password = sentMessage.password;
							messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageChangePassword, message);
							message = null; // do not release this message here
							break;

						case IoTMessage.MessageHandshake:
							device.sequenceNumber = 0;
							device.clientId = message.parseHandshake_();
							messageToSendToMainThread = Message.obtain(mainThreadHandler, IoTMessage.MessageHandshake, message.responseCode, 0, device);
							break;

						case IoTMessage.MessagePing:
						case IoTMessage.MessageReset:
						case IoTMessage.MessageGoodBye:
							messageToSendToMainThread = Message.obtain(mainThreadHandler, sentMessage.message, message.responseCode, 0, device);
							break;

						case IoTMessage.MessageExecute:
						case IoTMessage.MessageGetProperty:
						case IoTMessage.MessageSetProperty:
							message.device = device;
							message.interfaceIndex = sentMessage.payload0;
							message.commandOrPropertyIndex = sentMessage.payload1;
							messageToSendToMainThread = Message.obtain(mainThreadHandler, sentMessage.message, message);
							message = null; // do not release this message here
							break;
						}
					}
				} finally {
					// do not remove query device messages (let them timeout)
					// also, we must first release the old message/send the new one,
					// before sending the message to the main thread, to guarantee
					// that isWaitingForResponses() reflects the correct scenario!
					if (sentMessage.message != IoTMessage.MessageQueryDevice)
						sentMessageCache.unmarkAsSentMessageAndRelease_(sentMessage);
					final IoTSentMessage nextMessage;
					if (device != null && (nextMessage = device.nextPendingMessage_()) != null)
						sendMessage_(nextMessage);
					if (messageToSendToMainThread != null)
						mainThreadHandler.sendMessage(messageToSendToMainThread);
				}
			} catch (SocketTimeoutException ex) {
				// just ignore, we will try again later
			} catch (Throwable ex) {
				mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, IoTMessage.ClientMessageException, ex));
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
				try {
					final IoTDevice device = sentMessage.device;
					final int message = sentMessage.message;
					sentPacket.setSocketAddress(sentMessage.socketAddress);
					sentPacket.setData(buffer, 0, sentMessage.build_(buffer));
					sentMessageCache.markAsSentMessage_(sentMessage);
					socket.send(sentPacket);
					if (msg.what != 0)
						mainThreadHandler.sendMessage(Message.obtain(mainThreadHandler, IoTMessage.ClientMessageMessageSent, message, 0, device));
				} catch (Throwable ex) {
					sentMessageCache.unmarkAsSentMessageAndRelease_(sentMessage);
					final Handler h = mainThreadHandler;
					if (h != null)
						h.sendMessage(Message.obtain(h, IoTMessage.ClientMessageException, ex));
				}
				return true;
			}
		});
		synchronized (senderThreadSync) {
			senderThreadSync.notify();
		}
		Looper.loop();
	}

	@IoTClient.SecondaryThread
	private void doSendMessage_(IoTSentMessage sentMessage, boolean notifyObserver) {
		if (sentMessage.device == null ||
			sentMessage.device.canSendMessageNow_(sentMessageCache, sentMessage)) {
			sentMessage.attempts++;
			sentMessage.timestamp = (int)SystemClock.elapsedRealtime();
			senderThreadHandler.sendMessage(Message.obtain(senderThreadHandler, notifyObserver ? 1 : 0, sentMessage));
		}
	}

	@SecondaryThread
	private void sendMessage_(IoTSentMessage sentMessage) {
		// do not notify the observer when a message is being retried
		doSendMessage_(sentMessage, sentMessage.attempts == 0);
	}

	private boolean sendMessage(IoTSentMessage sentMessage) {
		if (!alive || senderThreadHandler == null)
			return false;
		doSendMessage_(sentMessage, true);
		return true;
	}

	public boolean scanDevices() {
		return sendMessage(sentMessageCache.queryDevice(getBroadcastAddress()));
	}

	@SecondaryThread
	void describeInterface_(IoTDevice device, int interfaceIndex) {
		sendMessage_(sentMessageCache.describeInterface_(device, interfaceIndex));
	}

	@SecondaryThread
	void describeEnum_(IoTProperty property) {
		sendMessage_(sentMessageCache.describeEnum_(property.ioTInterface, property.index));
	}

	boolean changePassword(IoTDevice device, String password) {
		return sendMessage(sentMessageCache.changePassword(device, password));
	}

	boolean handshake(IoTDevice device) {
		return sendMessage(sentMessageCache.handshake(device));
	}

	boolean ping(IoTDevice device) {
		return sendMessage(sentMessageCache.ping(device));
	}

	boolean reset(IoTDevice device) {
		return sendMessage(sentMessageCache.reset(device));
	}

	boolean goodBye(IoTDevice device) {
		return sendMessage(sentMessageCache.goodBye(device));
	}

	boolean execute(IoTInterface ioTInterface, int command) {
		return sendMessage(sentMessageCache.execute(ioTInterface, command));
	}

	boolean getProperty(IoTProperty property) {
		return sendMessage(sentMessageCache.getProperty(property.ioTInterface, property.index));
	}

	boolean setProperty(IoTProperty property, IoTProperty.Buffer value) {
		return sendMessage(sentMessageCache.setProperty(property.ioTInterface, property.index, value));
	}
}
