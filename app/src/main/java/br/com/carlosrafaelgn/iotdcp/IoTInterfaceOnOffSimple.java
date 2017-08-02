package br.com.carlosrafaelgn.iotdcp;

public final class IoTInterfaceOnOffSimple extends IoTInterface {
	public static final int CommandOnOff = 0x00;

	public static final int PropertyState = 0x00; // Must be { "<Name>", IoTProperty.ModeReadOnly, IoTProperty.DataTypeU8, 1, IoTProperty.UnitEnum, IoTProperty.UnitOne, 0 }

	public static final int StateUnknown = 0x00;
	public static final int StateOff = 0x01;
	public static final int StateOn = 0x02;
	public static final int StateTurningOff = 0x03;
	public static final int StateTurningOn = 0x04;

	public final IoTProperty state;

	@IoTClient.SecondaryThread
	static IoTInterfaceOnOffSimple create_(IoTDevice device, int index, String name, IoTProperty[] properties) {
		if (properties.length < 1 || !properties[0].isReadableEnum8_())
			return null;
		properties[0].handleDescribeEnum_(new IoTProperty.Enum[] {
			new IoTProperty.Enum("Unknown", StateUnknown),
			new IoTProperty.Enum("Off", StateOff),
			new IoTProperty.Enum("On", StateOn),
			new IoTProperty.Enum("Turning Off", StateTurningOff),
			new IoTProperty.Enum("Turning On", StateTurningOn)
		});
		return new IoTInterfaceOnOffSimple(device, index, name, properties);
	}

	@IoTClient.SecondaryThread
	private IoTInterfaceOnOffSimple(IoTDevice device, int index, String name, IoTProperty[] properties) {
		super(device, index, name, TypeOnOffSimple, properties);

		state = properties[0];
	}

	@Override
	void handleExecute(int responseCode, int command, byte[] payload, int payloadLength) {
		if (command != CommandOnOff)
			return;

		handleGetProperty(responseCode, PropertyState, payload, payloadLength);
	}

	public boolean executeOnOff() {
		return execute(CommandOnOff);
	}
}
