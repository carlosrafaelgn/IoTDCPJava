package br.com.carlosrafaelgn.iotdcp;

public abstract class IoTInterface {
	public static final int TypeSensor = 0x00;
	public static final int TypeOnOff = 0x01;
	public static final int TypeOnOffSimple = 0x02;
	public static final int TypeOpenClose = 0x03;
	public static final int TypeOpenCloseStop = 0x04;

	public final IoTDevice device;
	public final int index;
	public final String name;
	public final int type;
	private final IoTProperty[] properties;

	public IoTInterface(IoTDevice device, int index, String name, int type, IoTProperty[] properties) {
		this.device = device;
		this.index = index;
		this.name = name;
		this.type = type;
		this.properties = properties;

		for (IoTProperty property : properties)
			property.ioTInterface = this;
	}

	@Override
	public String toString() {
		return name;
	}

	@IoTClient.SecondaryThread
	final boolean isComplete_() {
		for (IoTProperty ioTProperty : properties) {
			if (!ioTProperty.isComplete_())
				return false;
		}
		return true;
	}

	@IoTClient.SecondaryThread
	final void describePropertiesEnum_() {
		for (IoTProperty ioTProperty : properties)
			ioTProperty.describeEnum_();
	}

	@IoTClient.SecondaryThread
	final void handleDescribeEnum_(int responseCode, int propertyIndex, byte[] payload, int payloadLength) {
		if (propertyIndex >= 0 && propertyIndex < properties.length)
			properties[propertyIndex].handleDescribeEnum_(responseCode, payload, payloadLength);
	}

	final boolean execute(int command) {
		return device.client.execute(this, command);
	}

	void handleExecute(int responseCode, int command, byte[] payload, int payloadLength) {
	}

	void handleGetProperty(int responseCode, int propertyIndex, byte[] payload, int payloadLength) {
		if (propertyIndex >= 0 && propertyIndex < properties.length)
			properties[propertyIndex].handleGetProperty(responseCode, payload, payloadLength);
	}

	void handleSetProperty(int responseCode, int propertyIndex, byte[] payload, int payloadLength) {
		if (propertyIndex >= 0 && propertyIndex < properties.length)
			properties[propertyIndex].handleSetProperty(responseCode, payload, payloadLength);
	}

	public final int propertyCount() {
		return properties.length;
	}

	public IoTProperty property(int propertyIndex) {
		return properties[propertyIndex];
	}

	public final void updateAllProperties() {
		for (IoTProperty property : properties)
			property.updateValue();
	}
}
