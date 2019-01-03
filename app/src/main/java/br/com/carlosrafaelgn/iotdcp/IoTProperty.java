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

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class IoTProperty {
	static final class Buffer {
		final byte[] buffer;
		int length;

		Buffer(int maxLength) {
			buffer = new byte[maxLength];
		}
	}

	public static final class Enum {
		public final String name;
		public final int value;

		Enum(String name, int value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString() {
			return name;
		}

		@Override
		public int hashCode() {
			return value * 31;
		}

		@Override
		public boolean equals(Object obj) {
			return ((obj instanceof Enum) && ((Enum)obj).value == value);
		}
	}

	public interface Observer {
		void onPropertyChange(IoTInterface ioTInterface, IoTProperty property, int userArg);
	}

	public static final int ModeReadOnly = 0x00;
	public static final int ModeWriteOnly = 0x01;
	public static final int ModeReadWrite = 0x02;

	public static final int DataTypeS8 = 0x00; // Signed 8 bits integer (two's complement)
	public static final int DataTypeS16 = 0x01; // Signed 16 bits integer (two's complement / little endian)
	public static final int DataTypeS32 = 0x02; // Signed 32 bits integer (two's complement / little endian)
	public static final int DataTypeS64 = 0x03; // Signed 64 bits integer (two's complement / little endian)
	public static final int DataTypeU8 = 0x04; // Unsigned 8 bits integer
	public static final int DataTypeU16 = 0x05; // Unsigned 16 bits integer (little endian)
	public static final int DataTypeU32 = 0x06; // Unsigned 32 bits integer (little endian)
	public static final int DataTypeU64 = 0x07; // Unsigned 64 bits integer (little endian)
	public static final int DataTypeFloat32 = 0x08; // 32 bits float point (IEEE 754 / little endian)
	public static final int DataTypeFloat64 = 0x09; // 64 bits float point (IEEE 754 / little endian)
	public static final int DataTypeRGBTriplet = 0x0A; // Unsigned 24 bits integer (little endian / to be used only with UnitRGB)

	public static final int UnitOne = 0x00; // Used in composed units, or as the default unit when a unit is not necessary
	public static final int UnitBit = 0x01;
	public static final int UnitByte = 0x02;
	public static final int UnitRadian = 0x03;
	public static final int UnitDegree = 0x04;
	public static final int UnitSecond = 0x05;
	public static final int UnitSecond2 = 0x06; // s²
	public static final int UnitSecond3 = 0x07; // s³
	public static final int UnitMeter = 0x08;
	public static final int UnitMeter2 = 0x09; // m²
	public static final int UnitMeter3 = 0x0A; // m³
	public static final int UnitGram = 0x0B;
	public static final int UnitOhm = 0x0C;
	public static final int UnitSiemens = 0x0D;
	public static final int UnitVolt = 0x0E;
	public static final int UnitCoulomb = 0x0F;
	public static final int UnitAmpere = 0x10;
	public static final int UnitAmpere2 = 0x11;
	public static final int UnitWatt = 0x12;
	public static final int UnitFarad = 0x13;
	public static final int UnitHenry = 0x14;
	public static final int UnitWeber = 0x15;
	public static final int UnitTesla = 0x16;
	public static final int UnitNewton = 0x17;
	public static final int UnitPascal = 0x18;
	public static final int UnitJoule = 0x19;
	public static final int UnitKelvin = 0x1A;
	public static final int UnitDegreeCelsius = 0x1B;
	public static final int UnitBel = 0x1C;
	public static final int UnitMole = 0x1D;
	public static final int UnitCandela = 0x1E;
	public static final int UnitLumen = 0x1F;
	public static final int UnitLux = 0x20;
	public static final int UnitBecquerel = 0x21;
	public static final int UnitGray = 0x22;
	public static final int UnitSievert = 0x23;
	public static final int UnitKatal = 0x24;
	public static final int UnitBool = 0xFB; // dataType must be DataTypeU8, where false == 0, true != 0 (unitDen and exponent must be ignored)
	public static final int UnitRGB = 0xFC; // dataType must be DataTypeRGBTriplet, unitDen and exponent must be ignored (first byte must be R, followed by G, then B)
	public static final int UnitRGBA = 0xFD; // dataType must be DataTypeU32, unitDen and exponent must be ignored (first byte must be R, followed by G, then B and finally A)
	public static final int UnitUTF8Text = 0xFE; // dataType must be DataTypeU8, elementCount contains the maximum length (including the null char), unitDen and exponent must be ignored (text must be null terminated and must be encoded in UTF8)
	public static final int UnitEnum = 0xFF; // dataType must be DataTypeS8/DataTypeU8/DataTypeS16/DataTypeU16/DataTypeS32/DataTypeU32, unitDen and exponent must be ignored

	public static final int IECKibi = 0x7F; // 2^10
	public static final int IECMebi = 0x7E; // 2^20
	public static final int IECGibi = 0x7D; // 2^30
	public static final int IECTebi = 0x7C; // 2^40
	public static final int IECPebi = 0x7B; // 2^50
	public static final int IECExbi = 0x7A; // 2^60
	public static final int IECZebi = 0x79; // 2^70
	public static final int IECYobi = 0x78; // 2^80

	public final int index;
	public final String name;
	public final int mode;
	public final int dataType;
	public final int elementSize; // To support arrays
	public final int elementCount; // To support arrays
	public final int unitNum, unitDen; // Used to describe composite units, such as m/s^2
	// If -30 <= exponent <= 30, then the value must be multiplied by 10^exponent
	// If exponent is one of the _IECMultipliers values, then the value must be multiplied by the corresponding value (such as 2^10, 2^20 and so on)
	public final int exponent;

	public Object userTag;

	private final Buffer value;

	private SparseArray<Enum> enumsByValue;
	private List<Enum> enumsByOrder;

	private Observer observer;

	IoTInterface ioTInterface;

	IoTProperty(int index, String name, int mode, int dataType, int elementCount, int unitNum, int unitDen, int exponent) {
		if (elementCount <= 0)
			elementCount = 1;
		switch (unitNum) {
		case UnitBool:
			if (dataType != DataTypeU8)
				throw new IllegalArgumentException("unitNum == UnitBool && dataType != DataTypeU8");
			unitDen = UnitOne;
			exponent = 0;
			break;
		case UnitRGB:
			if (dataType != DataTypeRGBTriplet)
				throw new IllegalArgumentException("unitNum == UnitRGB && dataType != DataTypeRGBTriplet");
			unitDen = UnitOne;
			exponent = 0;
			break;
		case UnitRGBA:
			if (dataType != DataTypeU32)
				throw new IllegalArgumentException("unitNum == UnitRGBA && dataType != DataTypeU32");
			unitDen = UnitOne;
			exponent = 0;
			break;
		case UnitUTF8Text:
			if (dataType != DataTypeU8)
				throw new IllegalArgumentException("unitNum == UnitUTF8Text && dataType != DataTypeU8");
			unitDen = UnitOne;
			exponent = 0;
			break;
		case UnitEnum:
			switch (dataType) {
			case DataTypeS8:
			case DataTypeS16:
			case DataTypeS32:
			case DataTypeU8:
			case DataTypeU16:
			case DataTypeU32:
				break;
			default:
				throw new IllegalArgumentException("unitNum == UnitEnum && dataType != DataTypeS8/DataTypeU8/DataTypeS16/DataTypeU16/DataTypeS32/DataTypeU32");
			}
			unitDen = UnitOne;
			exponent = 0;
			break;
		}
		this.index = index;
		this.name = name;
		this.mode = mode;
		this.dataType = dataType;
		this.elementCount = elementCount;
		this.unitNum = unitNum;
		this.unitDen = unitDen;
		this.exponent = exponent;
		switch (dataType) {
		case DataTypeS16:
		case DataTypeU16:
			elementSize = 2;
			break;
		case DataTypeS32:
		case DataTypeU32:
		case DataTypeFloat32:
			elementSize = 4;
			break;
		case DataTypeS64:
		case DataTypeU64:
		case DataTypeFloat64:
			elementSize = 8;
			break;
		case DataTypeRGBTriplet:
			if (unitNum == UnitRGB)
				elementSize = 3;
			else
				throw new IllegalArgumentException("unitNum != UnitRGB && dataType == DataTypeRGBTriplet");
			break;
		default:
			elementSize = 1;
			break;
		}
		value = new Buffer(elementSize * elementCount);
	}

	@Override
	public String toString() {
		return name;
	}

	public void setObserver(Observer observer) {
		this.observer = observer;
	}

	@SecondaryThread
	boolean isReadableEnum8_() {
		return (mode != ModeWriteOnly &&
			dataType == DataTypeU8 &&
			elementCount == 1 &&
			unitNum == UnitEnum);
	}

	@SecondaryThread
	boolean isComplete_() {
		return (unitNum != UnitEnum || enumsByValue != null);
	}

	@SecondaryThread
	void describeEnum_() {
		if (unitNum == UnitEnum && enumsByValue == null)
			ioTInterface.device.client.describeEnum_(this);
	}

	// This method is only called before the device is published
	@SecondaryThread
	void handleDescribeEnum_(int responseCode, byte[] payload, int payloadLength) {
		if (responseCode != IoTMessage.ResponseOK ||
			payloadLength == 0 ||
			unitNum != UnitEnum)
			return;

		try {
			int srcOffset = 2; // Skip interfaceIndex and propertyIndex

			int enumCount = (payload[srcOffset++] & 0xFF);

			if (enumCount == 0) {
				enumsByValue = new SparseArray<>();
				enumsByOrder = Collections.unmodifiableList(new ArrayList<Enum>());
				return;
			}

			final SparseArray<Enum> sparseArray = new SparseArray<>(enumCount);
			final ArrayList<Enum> list = new ArrayList<>(enumCount);

			for (int i = 0; i < enumCount; i++) {
				final int enumNameLen = (payload[srcOffset++] & 0xFF);
				if ((enumNameLen + srcOffset) > payloadLength)
					return;
				final String name = new String(payload, srcOffset, enumNameLen);
				srcOffset += enumNameLen;

				final int value;
				switch (dataType) {
				case DataTypeS8:
					value = (int)payload[srcOffset++];
					break;
				case DataTypeU8:
					value = (payload[srcOffset++] & 0xFF);
					break;
				case DataTypeS16:
					value = (payload[srcOffset++] & 0xFF) | (payload[srcOffset++] << 8);
					break;
				case DataTypeU16:
					value = (payload[srcOffset++] & 0xFF) | ((payload[srcOffset++] & 0xFF) << 8);
					break;
				default:
					value = (payload[srcOffset++] & 0xFF) | ((payload[srcOffset++] & 0xFF) << 8) | ((payload[srcOffset++] & 0xFF) << 16) | (payload[srcOffset++] << 24);
					break;
				}

				final Enum e = new Enum(name, value);
				sparseArray.put(value, e);
				list.add(e);
			}

			enumsByValue = sparseArray;
			enumsByOrder = Collections.unmodifiableList(list);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	// This method is only called before the device is published
	@SecondaryThread
	void handleDescribeEnum_(Enum[] enums) {
		enumsByValue = new SparseArray<>(enums.length);

		final ArrayList<Enum> list = new ArrayList<>(enums.length);
		for (Enum e : enums) {
			list.add(e);
			enumsByValue.put(e.value, e);
		}

		enumsByOrder = Collections.unmodifiableList(list);
	}

	void handleProperty(byte[] payload, int payloadOffset, int payloadLength, int userArg) {
		if (payloadLength > value.buffer.length)
			payloadLength = value.buffer.length;

		if (payloadLength == 0) {
			if (unitNum == UnitUTF8Text) {
				synchronized (value) {
					value.buffer[0] = 0;
					value.length = 1;
				}
			} else {
				synchronized (value) {
					value.length = 0;
				}
			}
		} else {
			synchronized (value) {
				System.arraycopy(payload, payloadOffset, value.buffer, 0, payloadLength);
				value.length = payloadLength;
			}
		}

		if (observer != null)
			observer.onPropertyChange(ioTInterface, this, userArg);
	}

	public List<Enum> getEnums() {
		return enumsByOrder;
	}

	public boolean updateValue() {
		return updateValue(0);
	}

	public boolean updateValue(int userArg) {
		return (mode != ModeWriteOnly && ioTInterface.device.client.getProperty(this, userArg));
	}

	// Access to the value must be synchronized because it is read from a
	// secondary thread in order to build a message

	public boolean getValueBoolean() {
		synchronized (this.value) {
			return (value.buffer[0] != 0);
		}
	}

	public int getValueByte() {
		synchronized (this.value) {
			switch (dataType) {
			case DataTypeS8:
				return value.buffer[0];
			default:
				return (value.buffer[0] & 0xFF);
			}
		}
	}

	public int getValueShort() {
		final byte[] buffer = value.buffer;
		synchronized (value) {
			switch (dataType) {
			case DataTypeS16:
				return ((buffer[0] & 0xFF) | (buffer[1] << 8));
			default:
				return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
			}
		}
	}

	public int getValueInt() {
		final byte[] buffer = value.buffer;
		synchronized (value) {
			return (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8) | ((buffer[2] & 0xFF) << 16) | (buffer[3] << 24);
		}
	}

	public long getValueLong() {
		final byte[] buffer = value.buffer;
		synchronized (value) {
			return ((long)((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8) | ((buffer[2] & 0xFF) << 16) | (buffer[3] << 24)) & 0xFFFFFFFFL) |
				((long)((buffer[4] & 0xFF) | ((buffer[5] & 0xFF) << 8) | ((buffer[6] & 0xFF) << 16) | (buffer[7] << 24)) << 32);
		}
	}

	public float getValueFloat() {
		return Float.intBitsToFloat(getValueInt());
	}

	public double getValueDouble() {
		return Double.longBitsToDouble(getValueLong());
	}

	public Enum getValueEnum() {
		if (enumsByValue == null)
			return null;
		final byte[] buffer = this.value.buffer;
		final int value;
		synchronized (this.value) {
			switch (dataType) {
			case DataTypeS8:
				value = buffer[0];
				break;
			case DataTypeU8:
				value = (buffer[0] & 0xFF);
				break;
			case DataTypeS16:
				value = (buffer[0] & 0xFF) | (buffer[1] << 8);
				break;
			case DataTypeU16:
				value = (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8);
				break;
			default:
				value = (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8) | ((buffer[2] & 0xFF) << 16) | (buffer[3] << 24);
				break;
			}
		}
		return enumsByValue.get(value);
	}

	public int getValueRGBA() {
		final byte[] buffer = value.buffer;
		synchronized (value) {
			// Make sure alpha is 255 when dataType == DataTypeRGBTriplet
			return (buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8) | ((buffer[2] & 0xFF) << 16) |
				((dataType == DataTypeRGBTriplet) ? 0xFF000000 : (buffer[3] << 24));
		}
	}

	public boolean getArrayValueBoolean(int elementIndex) {
		synchronized (this.value) {
			return (value.buffer[elementIndex] != 0);
		}
	}

	public int getArrayValueByte(int elementIndex) {
		synchronized (this.value) {
			switch (dataType) {
			case DataTypeS8:
				return value.buffer[elementIndex];
			default:
				return (value.buffer[elementIndex] & 0xFF);
			}
		}
	}

	public int getArrayValueShort(int elementIndex) {
		elementIndex <<= 1;
		final byte[] buffer = value.buffer;
		synchronized (value) {
			switch (dataType) {
			case DataTypeS16:
				return ((buffer[elementIndex++] & 0xFF) | (buffer[elementIndex] << 8));
			default:
				return ((buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex] & 0xFF) << 8));
			}
		}
	}

	public int getArrayValueInt(int elementIndex) {
		elementIndex <<= 2;
		final byte[] buffer = value.buffer;
		synchronized (value) {
			return (buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex++] & 0xFF) << 8) | ((buffer[elementIndex++] & 0xFF) << 16) | (buffer[elementIndex] << 24);
		}
	}

	public long getArrayValueLong(int elementIndex) {
		elementIndex <<= 3;
		final byte[] buffer = value.buffer;
		synchronized (value) {
			return ((long)((buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex++] & 0xFF) << 8) | ((buffer[elementIndex++] & 0xFF) << 16) | (buffer[elementIndex++] << 24)) & 0xFFFFFFFFL) |
				((long)((buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex++] & 0xFF) << 8) | ((buffer[elementIndex++] & 0xFF) << 16) | (buffer[elementIndex] << 24)) << 32);
		}
	}

	public float getArrayValueFloat(int elementIndex) {
		return Float.intBitsToFloat(getArrayValueInt(elementIndex));
	}

	public double getArrayValueDouble(int elementIndex) {
		return Double.longBitsToDouble(getArrayValueLong(elementIndex));
	}

	public Enum getArrayValueEnum(int elementIndex) {
		if (enumsByValue == null)
			return null;
		final byte[] buffer = this.value.buffer;
		final int value;
		synchronized (this.value) {
			switch (dataType) {
			case DataTypeS8:
				value = buffer[elementIndex];
				break;
			case DataTypeU8:
				value = (buffer[elementIndex] & 0xFF);
				break;
			case DataTypeS16:
				elementIndex <<= 1;
				value = (buffer[elementIndex++] & 0xFF) | (buffer[elementIndex] << 8);
				break;
			case DataTypeU16:
				elementIndex <<= 1;
				value = (buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex] & 0xFF) << 8);
				break;
			default:
				elementIndex <<= 2;
				value = (buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex++] & 0xFF) << 8) | ((buffer[elementIndex++] & 0xFF) << 16) | (buffer[elementIndex] << 24);
				break;
			}
		}
		return enumsByValue.get(value);
	}

	public int getArrayValueRGBA(int elementIndex) {
		if (dataType == DataTypeRGBTriplet)
			elementIndex *= 3;
		else
			elementIndex <<= 2;
		final byte[] buffer = value.buffer;
		synchronized (value) {
			// Make sure alpha is 255 when dataType == DataTypeRGBTriplet
			return (buffer[elementIndex++] & 0xFF) | ((buffer[elementIndex++] & 0xFF) << 8) | ((buffer[elementIndex++] & 0xFF) << 16) |
				((dataType == DataTypeRGBTriplet) ? 0xFF000000 : (buffer[elementIndex] << 24));
		}
	}

	public int getValueBuffer(byte[] buffer) {
		return getValueBuffer(buffer, 0, buffer.length);
	}

	public int getValueBuffer(byte[] buffer, int offset, int length) {
		synchronized (value) {
			if (length > value.length)
				length = value.length;
			System.arraycopy(value.buffer, 0, buffer, offset, length);
			return length;
		}
	}

	public String getValueString() {
		synchronized (value) {
			if (value.length <= 1)
				return "";
			return new String(value.buffer, 0, value.length - 1);
		}
	}

	public boolean setValueBoolean(boolean value) {
		return setValueBoolean(value, 0);
	}

	public boolean setValueByte(int value) {
		return setValueByte(value, 0);
	}

	public boolean setValueShort(int value) {
		return setValueShort(value, 0);
	}

	public boolean setValueInt(int value) {
		return setValueInt(value, 0);
	}

	public boolean setValueLong(long value) {
		return setValueLong(value, 0);
	}

	public boolean setValueFloat(float value) {
		return setValueInt(Float.floatToRawIntBits(value), 0);
	}

	public boolean setValueDouble(double value) {
		return setValueLong(Double.doubleToRawLongBits(value), 0);
	}

	public boolean setValueEnum(Enum value) {
		return setValueEnum(value, 0);
	}

	public boolean setValueRGBA(int value) {
		return setValueRGBA(value, 0);
	}

	public boolean setValueBoolean(boolean value, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		synchronized (this.value) {
			this.value.buffer[0] = (byte)(value ? 1 : 0);
			this.value.length = 1;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueByte(int value, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		synchronized (this.value) {
			this.value.buffer[0] = (byte)value;
			this.value.length = 1;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueShort(int value, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[0] = (byte)value;
			buffer[1] = (byte)(value >>> 8);
			this.value.length = 2;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueInt(int value, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[0] = (byte)value;
			buffer[1] = (byte)(value >>> 8);
			buffer[2] = (byte)(value >>> 16);
			buffer[3] = (byte)(value >>> 24);
			this.value.length = 4;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueLong(long value, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[0] = (byte)value;
			buffer[1] = (byte)(value >>> 8);
			buffer[2] = (byte)(value >>> 16);
			buffer[3] = (byte)(value >>> 24);
			buffer[4] = (byte)(value >>> 32);
			buffer[5] = (byte)(value >>> 40);
			buffer[6] = (byte)(value >>> 48);
			buffer[7] = (byte)(value >>> 56);
			this.value.length = 8;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueFloat(float value, int userArg) {
		return setValueInt(Float.floatToRawIntBits(value), userArg);
	}

	public boolean setValueDouble(double value, int userArg) {
		return setValueLong(Double.doubleToRawLongBits(value), userArg);
	}

	public boolean setValueEnum(Enum value, int userArg) {
		if (enumsByValue == null || mode == ModeReadOnly || value == null)
			return false;
		switch (elementSize) {
		case 2:
			return setValueShort(value.value, userArg);
		case 4:
			return setValueInt(value.value, userArg);
		default:
			return setValueByte(value.value, userArg);
		}
	}

	public boolean setValueRGBA(int value, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[0] = (byte)value;
			buffer[1] = (byte)(value >>> 8);
			buffer[2] = (byte)(value >>> 16);
			if (dataType == DataTypeRGBTriplet) {
				this.value.length = 3;
			} else {
				buffer[3] = (byte)(value >>> 24);
				this.value.length = 4;
			}
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setArrayValueBoolean(int elementIndex, boolean value) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[elementIndex] = (byte)(value ? 1 : 0);
			this.value.length = buffer.length;
		}
		return true;
	}

	public boolean setArrayValueByte(int elementIndex, int value) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[elementIndex] = (byte)value;
			this.value.length = buffer.length;
		}
		return true;
	}

	public boolean setArrayValueShort(int elementIndex, int value) {
		if (mode == ModeReadOnly)
			return false;
		elementIndex <<= 1;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[elementIndex++] = (byte)value;
			buffer[elementIndex] = (byte)(value >>> 8);
			this.value.length = buffer.length;
		}
		return true;
	}

	public boolean setArrayValueInt(int elementIndex, int value) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		elementIndex <<= 2;
		synchronized (this.value) {
			buffer[elementIndex++] = (byte)value;
			buffer[elementIndex++] = (byte)(value >>> 8);
			buffer[elementIndex++] = (byte)(value >>> 16);
			buffer[elementIndex] = (byte)(value >>> 24);
			this.value.length = buffer.length;
		}
		return true;
	}

	public boolean setArrayValueLong(int elementIndex, long value) {
		if (mode == ModeReadOnly)
			return false;
		final byte[] buffer = this.value.buffer;
		elementIndex <<= 3;
		synchronized (this.value) {
			buffer[elementIndex++] = (byte)value;
			buffer[elementIndex++] = (byte)(value >>> 8);
			buffer[elementIndex++] = (byte)(value >>> 16);
			buffer[elementIndex++] = (byte)(value >>> 24);
			buffer[elementIndex++] = (byte)(value >>> 32);
			buffer[elementIndex++] = (byte)(value >>> 40);
			buffer[elementIndex++] = (byte)(value >>> 48);
			buffer[elementIndex] = (byte)(value >>> 56);
			this.value.length = buffer.length;
		}
		return true;
	}

	public boolean setArrayValueFloat(int elementIndex, float value) {
		return setArrayValueInt(elementIndex, Float.floatToRawIntBits(value));
	}

	public boolean setArrayValueDouble(int elementIndex, double value) {
		return setArrayValueLong(elementIndex, Double.doubleToRawLongBits(value));
	}

	public boolean setArrayValueEnum(int elementIndex, Enum value) {
		if (value == null)
			return false;
		switch (elementSize) {
		case 2:
			return setArrayValueShort(elementIndex, value.value);
		case 4:
			return setArrayValueInt(elementIndex, value.value);
		default:
			return setArrayValueByte(elementIndex, value.value);
		}
	}

	public boolean setArrayValueRGBA(int elementIndex, int value) {
		if (mode == ModeReadOnly)
			return false;
		if (dataType == DataTypeRGBTriplet)
			elementIndex *= 3;
		else
			elementIndex <<= 2;
		final byte[] buffer = this.value.buffer;
		synchronized (this.value) {
			buffer[elementIndex++] = (byte)value;
			buffer[elementIndex++] = (byte)(value >>> 8);
			buffer[elementIndex++] = (byte)(value >>> 16);
			if (dataType != DataTypeRGBTriplet)
				buffer[elementIndex] = (byte)(value >>> 24);
			this.value.length = buffer.length;
		}
		return true;
	}

	public boolean commitSetArrayValue() {
		return ioTInterface.device.client.setProperty(this, this.value, 0);
	}

	public boolean commitSetArrayValue(int userArg) {
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueBuffer(byte[] buffer) {
		return setValueBuffer(buffer, 0, buffer.length, 0);
	}

	public boolean setValueBuffer(byte[] buffer, int userArg) {
		return setValueBuffer(buffer, 0, buffer.length, userArg);
	}

	public boolean setValueBuffer(byte[] buffer, int offset, int length) {
		return setValueBuffer(buffer, offset, length, 0);
	}

	public boolean setValueBuffer(byte[] buffer, int offset, int length, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		synchronized (this.value) {
			System.arraycopy(buffer, offset, this.value.buffer, 0, length);
			this.value.length = length;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}

	public boolean setValueString(String string) {
		return setValueString(string, 0);
	}

	public boolean setValueString(String string, int userArg) {
		if (mode == ModeReadOnly)
			return false;
		if (string == null || string.length() == 0) {
			synchronized (this.value) {
				this.value.buffer[0] = 0;
				this.value.length = 1;
			}
			return ioTInterface.device.client.setProperty(this, this.value, userArg);
		}
		final byte[] buffer = string.getBytes();
		synchronized (this.value) {
			System.arraycopy(buffer, 0, this.value.buffer, 0, buffer.length);
			this.value.buffer[buffer.length] = 0;
			this.value.length = buffer.length + 1;
		}
		return ioTInterface.device.client.setProperty(this, this.value, userArg);
	}
}
