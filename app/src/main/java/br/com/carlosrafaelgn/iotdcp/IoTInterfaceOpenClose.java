package br.com.carlosrafaelgn.iotdcp;

public final class IoTInterfaceOpenClose extends IoTInterface {
	public static final int CommandClose = 0x00;
	public static final int CommandOpen = 0x01;

	public static final int PropertyState = 0x00; // Must be { "<Name>", IoTProperty.ModeReadOnly, IoTProperty.DataTypeU8, 1, IoTProperty.UnitEnum, IoTProperty.UnitOne, 0 }

	public static final int StateUnknown = 0x00;
	public static final int StateClosed = 0x01;
	public static final int StateOpen = 0x02;
	public static final int StateClosing = 0x03;
	public static final int StateOpening = 0x04;

	public final IoTProperty state;

	@IoTClient.SecondaryThread
	static IoTInterfaceOpenClose create_(IoTDevice device, int index, String name, IoTProperty[] properties) {
		if (properties.length < 1 || !properties[0].isReadableEnum8_())
			return null;
		properties[0].handleDescribeEnum_(new IoTProperty.Enum[] {
			new IoTProperty.Enum("Unknown", StateUnknown),
			new IoTProperty.Enum("Closed", StateClosed),
			new IoTProperty.Enum("Open", StateOpen),
			new IoTProperty.Enum("Closing", StateClosing),
			new IoTProperty.Enum("Opening", StateOpening)
		});
		return new IoTInterfaceOpenClose(device, index, name, properties);
	}

	@IoTClient.SecondaryThread
	private IoTInterfaceOpenClose(IoTDevice device, int index, String name, IoTProperty[] properties) {
		super(device, index, name, TypeOpenClose, properties);

		state = properties[0];
	}

	@Override
	void handleExecute(int responseCode, int command, byte[] payload, int payloadLength) {
		if (command != CommandClose && command != CommandOpen)
			return;

		handleGetProperty(responseCode, PropertyState, payload, payloadLength);
	}

	public boolean executeClose() {
		return execute(CommandClose);
	}

	public boolean executeOpen() {
		return execute(CommandOpen);
	}
}
