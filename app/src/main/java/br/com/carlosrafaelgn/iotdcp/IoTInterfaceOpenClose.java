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

	@SecondaryThread
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

	@SecondaryThread
	private IoTInterfaceOpenClose(IoTDevice device, int index, String name, IoTProperty[] properties) {
		super(device, index, name, TypeOpenClose, properties);

		state = properties[0];
	}

	@Override
	void handleExecute(int responseCode, int command, byte[] payload, int payloadLength, int userArg) {
		if (command != CommandClose && command != CommandOpen)
			return;

		if (responseCode == IoTMessage.ResponseOK)
			device.handleProperty(payload, payloadLength, userArg);
	}

	public boolean executeClose() {
		return execute(CommandClose);
	}

	public boolean executeOpen() {
		return execute(CommandOpen);
	}
}
