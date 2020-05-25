package de.toshsoft;

import java.io.IOException;

public enum COLORMODEL {
	C24bit, C256, C64, C8, C4, C2;

	public static final String COLORMODEL_24_BIT = "C24bit";
	public static final String COLORMODEL_256_COLORS = "C256";
	public static final String COLORMODEL_64_COLORS = "C64";
	public static final String COLORMODEL_8_COLORS = "C8";
	public static final String COLORMODEL_GREYSCALE = "C4";
	public static final String COLORMODEL_BLACK_AND_WHITE = "C2";

	public static final String COLORMODEL_24_BIT_STRING = "24-bit color (4 bpp)";
	public static final String COLORMODEL_256_COLORS_STRING = "256 colors (1 bpp)";
	public static final String COLORMODEL_64_COLORS_STRING = "64 colors (1 bpp)";
	public static final String COLORMODEL_8_COLORS_STRING = "8 colors (1 bpp)";
	public static final String COLORMODEL_GREYSCALE_STRING =  "Greyscale (1 bpp)";
	public static final String COLORMODEL_BLACK_AND_WHITE_STRING =  "Black & White (1 bpp)";

	public int bpp() {
		switch (this) {
		case C24bit:
			return 4;
		default:
			return 1;
		}
	}

	public int[] palette() {
		switch (this) {
		case C24bit:
			return null;
		case C256:
			return ColorModel256.colors;
		case C64:
			return ColorModel64.colors;
		case C8:
			return ColorModel8.colors;
		case C4:
			return ColorModel64.colors;
		case C2:
			return ColorModel8.colors;
		default:
			return ColorModel256.colors;
		}
	}
	
	public String nameString()
	{
		return super.toString();
	}

	public void setPixelFormat(RfbProto rfb) throws IOException {
		switch (this) {
		case C24bit:
			// 24-bit color
			rfb.writeSetPixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0, false);
			break;
		case C256:
			rfb.writeSetPixelFormat(8, 8, false, true, 7, 7, 3, 0, 3, 6, false);
			break;
		case C64:
			rfb.writeSetPixelFormat(8, 6, false, true, 3, 3, 3, 4, 2, 0, false);
			break;
		case C8:
			rfb.writeSetPixelFormat(8, 3, false, true, 1, 1, 1, 2, 1, 0, false);
			break;
		case C4:
			// Greyscale
			rfb.writeSetPixelFormat(8, 6, false, true, 3, 3, 3, 4, 2, 0, true);
			break;
		case C2:
			// B&W
			rfb.writeSetPixelFormat(8, 3, false, true, 1, 1, 1, 2, 1, 0, true);
			break;
		default:
			// Default is 256 colors
			rfb.writeSetPixelFormat(8, 8, false, true, 7, 7, 3, 0, 3, 6, false);
			break;
		}
	}

	static public COLORMODEL getModelForId(String id) {
		switch (id) {
			case COLORMODEL_24_BIT:
				return C24bit;
			case COLORMODEL_256_COLORS:
				return C256;
			case COLORMODEL_64_COLORS:
				return C64;
			case COLORMODEL_8_COLORS:
				return C8;
			case COLORMODEL_GREYSCALE:
				return C4;
			case COLORMODEL_BLACK_AND_WHITE:
				return C2;
			default:
				return C24bit;
		}
	}

	static public COLORMODEL getModelForDesc(String desc) {
		switch (desc) {
			case COLORMODEL_256_COLORS_STRING:
				return C256;
			case COLORMODEL_64_COLORS_STRING:
				return C64;
			case COLORMODEL_8_COLORS_STRING:
				return C8;
			case COLORMODEL_GREYSCALE_STRING:
				return C4;
			case COLORMODEL_BLACK_AND_WHITE_STRING:
				return C2;
			case COLORMODEL_24_BIT_STRING:
			default:
				return C24bit;
		}
	}

	public String getId() {
		switch (this) {
			case C256:
				return COLORMODEL_256_COLORS;
			case C64:
				return COLORMODEL_64_COLORS;
			case C8:
				return COLORMODEL_8_COLORS;
			case C4:
				return COLORMODEL_GREYSCALE;
			case C2:
				return COLORMODEL_BLACK_AND_WHITE;
			case C24bit:
			default:
				return COLORMODEL_24_BIT;
		}
	}

	public String toString() {
		switch (this) {
		case C256:
			return COLORMODEL_256_COLORS_STRING;
		case C64:
			return COLORMODEL_64_COLORS_STRING;
		case C8:
			return COLORMODEL_8_COLORS_STRING;
		case C4:
			return COLORMODEL_GREYSCALE_STRING;
		case C2:
			return COLORMODEL_BLACK_AND_WHITE_STRING;
		case C24bit:
		default:
			return COLORMODEL_24_BIT_STRING;
		}
	}
}
