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

@SuppressWarnings({"unused", "WeakerAccess"})
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
