package com.codedchai.chip8;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ROM {

	private static final int MAXIMUM_ROM_SIZE = 3584;

	public static void main( String[] args ) throws Exception {
		ROM disassembler = new ROM();

		System.out.println( args[0] );

		Path tetrisRom = Paths.get( args[0] );

		disassembler.loadROM( tetrisRom );
	}

	public static List < Byte > loadROM( Path filePath ) throws Exception {
		List < Byte > bytes = new ArrayList <>();

		ByteBuffer byteBuffer = ByteBuffer.wrap( Files.readAllBytes( filePath ) );
		//byteBuffer.order( ByteOrder.BIG_ENDIAN );

		while ( byteBuffer.hasRemaining() ) {
			Byte currentByte = byteBuffer.get();
			bytes.add( currentByte );

			System.out.println( String.format( "%02X", (currentByte & 0xFF) ) );
		}

		if ( bytes.size() > MAXIMUM_ROM_SIZE ) {
			throw new Exception( "ROM '" + filePath.getFileName() + "' is too large to fit into Chip-8 RAM. It is " + bytes.size() + " bytes when the limit is " + MAXIMUM_ROM_SIZE );
		}

		return bytes;
	}

}
