package br.com.carlosrafaelgn.iotdcp;

public final class IoTInterfaceSensor extends IoTInterface {
	public static final int PropertyValue = 0x00; // Could have any data type and any unit

	public final IoTProperty value;

	@IoTClient.SecondaryThread
	static IoTInterfaceSensor create_(IoTDevice device, int index, String name, IoTProperty[] properties) {
		return ((properties.length < 1) ? null : new IoTInterfaceSensor(device, index, name, properties));
	}

	@IoTClient.SecondaryThread
	private IoTInterfaceSensor(IoTDevice device, int index, String name, IoTProperty[] properties) {
		super(device, index, name, TypeSensor, properties);

		value = properties[0];
	}
}
