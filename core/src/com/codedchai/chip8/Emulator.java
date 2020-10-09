package com.codedchai.chip8;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Emulator implements Runnable {
	Logger logger = Logger.getLogger( Emulator.class.getName() );

	/* Speed of our CPU in Hz */
	private final int CPU_FREQUENCY = 500;
	/* Speed of our timers in Hz */
	private final int TIMER_FREQUENCY = 60;
	/* How many bytes of memory we have (0xFFF) */
	private final int MAX_MEMORY = 4096;
	/* The total number of pixels we can display (64 * 32) */
	private final int NUM_PIXELS = 2048;
	/* We have 16 total registers that we can write to, each one being one byte */
	private final int NUM_V_REGISTERS = 16;
	/* How deep our call stack will go. This is typically anywhere from 16-48, I'm going to be generous and make it 48 */
	private final int MAX_CALL_STACK_LEVEL = 48;
	/* How many different keyboard inputs we accept (0 - F) */
	private final int NUM_KEYS = 16;
	/* Where the program counter start in our memory (0x200) */
	private final short PROGRAM_COUNTER_START_LOCATION = 512;
	/* Index register's initial location */
	private final short INDEX_REGISTER_START_LOCATION = 0;
	/* Our initial opcode should be nothing */
	private final short OPCODE_START = 0;
	/* Where we begin in the stack */
	private final short STACK_POINTER_START = 0;
	/* Where we can begin to store the ROM into memory */
	private final int MEMORY_ROM_START_LOCATION = 512;
	/* What our delay timer should start at */
	private final int DELAY_TIMER_START = 0;
	/* What our sound timer should start at */
	private final int SOUND_TIMER_START = 0;

	int opcode;
	private short programCounter, indexRegister, stackPointer, delayTimer, soundTimer;
	volatile private int[] pixels;
	private byte[] vRegisters, memory, keys;

	/* Used to store address that should be returned when subroutine is finished */
	private short[] callStack;

	/* true if we are ready to draw a new frame */
	volatile private boolean drawFlag;

	Random random;

	/* Bytes defined to draw out the hexadecimal numbers as sprites */
	short hexadecimalFontSprites[] =
			{
					0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
					0x20, 0x60, 0x20, 0x20, 0x70, // 1
					0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
					0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
					0x90, 0x90, 0xF0, 0x10, 0x10, // 4
					0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
					0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
					0xF0, 0x10, 0x20, 0x40, 0x40, // 7
					0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
					0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
					0xF0, 0x90, 0xF0, 0x90, 0x90, // A
					0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
					0xF0, 0x80, 0x80, 0x80, 0xF0, // C
					0xE0, 0x90, 0x90, 0x90, 0xE0, // D
					0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
					0xF0, 0x80, 0xF0, 0x80, 0x80  // F
			};

	Instant timeOfLastCompute = null;

	public Emulator() {
		initialize();
	}

	public int[] getDisplayPixels() {
		return pixels;
	}

	public boolean drawFlag() {
		return drawFlag;
	}

	@Override
	public void run() {
		timeOfLastCompute = Instant.now();

		try {
			update();
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	/*
	Setup default values for everything, load ROM into memory, there are 16 vRegisters, 2048 pixels (64*32), 16 levels in the stack, and 4096 max memory, and program counter starts at 0x200
	 */
	private void initialize() {
		programCounter = PROGRAM_COUNTER_START_LOCATION;
		indexRegister = INDEX_REGISTER_START_LOCATION;
		stackPointer = STACK_POINTER_START;
		opcode = OPCODE_START;
		delayTimer = DELAY_TIMER_START;
		soundTimer = SOUND_TIMER_START;

		pixels = new int[NUM_PIXELS];

		vRegisters = new byte[NUM_V_REGISTERS];
		memory = new byte[MAX_MEMORY];
		keys = new byte[NUM_KEYS];
		callStack = new short[MAX_CALL_STACK_LEVEL];

		for ( int fontMemoryIndex = 0; fontMemoryIndex < hexadecimalFontSprites.length; fontMemoryIndex++ ) {
			memory[fontMemoryIndex] = (byte) hexadecimalFontSprites[fontMemoryIndex];
		}

		try {

			byte[] rom = Files.readAllBytes( Paths.get( "C:\\Users\\Guard\\Documents\\libGDXChip8\\core\\assets\\roms\\BC_test.ch8" ) );
			//List < Byte > rom = ROM.loadROM( Paths.get( "C:\\Users\\Guard\\Documents\\libGDXChip8\\core\\assets\\roms\\TETRIS" ) );

			for ( int memoryIndex = MEMORY_ROM_START_LOCATION; memoryIndex < MAX_MEMORY && (memoryIndex - MEMORY_ROM_START_LOCATION) < rom.length; memoryIndex++ ) {
				memory[memoryIndex] = rom[memoryIndex - MEMORY_ROM_START_LOCATION];
			}

			System.out.println( rom.length );
		} catch ( Exception e ) {
			e.printStackTrace();
			System.err.println( "Failed to load ROM" );
		}

		drawFlag = true;

		random = new Random();
	}

	/*
	Our main loop
	 */
	private void update() throws Exception {

		while ( true ) {
			Instant currentComputeTime = Instant.now();
			double deltaTime = (double) Duration.between( timeOfLastCompute, currentComputeTime ).toNanos() / 1000000000;

			//System.out.println( Math.round( deltaTime * CPU_FREQUENCY ) );

			timeOfLastCompute = Instant.now();

			for ( int i = 0; i < Math.round( deltaTime * CPU_FREQUENCY ); i++ ) {
				emulateCycle();
			}

			TimeUnit.MILLISECONDS.sleep( 10 );
		}
	}

	/*
	This is each step of our processor, basically each tick
	 */
	private void emulateCycle() throws Exception {
		opcode = (((memory[programCounter] & 0xFFFF) << 8) & 0xFFFF) | (memory[programCounter + 1] & 0xFF);
		System.out.println( "Executing opcode: " + Integer.toHexString( opcode ) );
		executeOpcode();
	}

	/*
	Properly select and execute our opcode
	 */
	private void executeOpcode() throws Exception {

		switch ( opcode & 0xF000 ) {
			/* Begin case 0x0000 */
			case 0x0000:
				switch ( opcode & 0x00FF ) {
					case 0x0000:
						programCounter += 2; // null operation, don't do anything
						break;

					case 0x00E0:
						cls(); /* 00E0 */
						break;
					case 0x00EE:
						ret(); /* 00EE */
						break;
					default:
						throw new Exception( "Unknown opcode: " + Integer.toHexString( opcode ) + " in [0x0000]" );
				}
				break;
			/* End case 0x0000 */

			/* Begin case 0x1000 */
			case 0x1000:
				jmp(); /* 0x1NNN */
				break;
			/* End case 0x1000 */

			/* Begin case 0x2000 */
			case 0x2000:
				call(); /* 0x2NNN */
				break;
			/* End case 0x2000 */

			/* Begin case 0x3000 */
			case 0x3000:
				SEVxIsKK(); /* 3xkk */
				break;
			/* End case 0x3000 */

			/* Begin case 0x4000 */
			case 0x4000:
				SNEVxIsNotKK(); /* 4xkk */
				break;
			/* End case 0x4000 */

			/* Begin case 0x5000 */
			case 0x5000:
				SEVxIsVy(); /* 5xy0 */
				break;
			/* End case 0x5000 */

			/* Begin case 0x6000 */
			case 0x6000:
				loadKKToVx(); /* 6xkk */
				break;
			/* End case 0x6000 */

			/* Begin case 0x7000 */
			case 0x7000:
				addVxKK(); /* 7xkk */
				break;
			/* End case 0x7000 */

			/* Begin case 0x6000 */
			case 0x8000:

				switch ( opcode & 0x000F ) {
					case 0x0000:
						loadVxVy(); /* 8xy0*/
						break;

					case 0x0001:
						orVxVy(); /* 8xy1 */
						break;

					case 0x0002:
						andVxVy(); /* 8xy2 */
						break;

					case 0x0003:
						xorVxVy(); /* 8xy3 */
						break;

					case 0x0004:
						addVxVy(); /* 8xy4 */
						break;

					case 0x0005:
						subVxVy(); /* 8xy5 */
						break;

					case 0x0006:
						shiftRightVx(); /* 8xy6 */
						break;

					case 0x0007:
						subnVxVy(); /* 8xy7 */
						break;

					case 0x000E:
						shiftLeftVx(); /* 8xyE */
						break;

					default:
						throw new Exception( "Unknown opcode: " + Integer.toHexString( opcode ) + " in [0x8000]" );
				}
				break;
			/* End case 0x8000 */

			/* Begin case 0x9000 */
			case 0x9000:
				SNEVxIsNotVy(); /* 9xy0 */
				break;
			/* End case 0x9000 */

			/* Begin case 0xA000 */
			case 0xA000:
				loadINNN(); /* Annn */
				break;
			/* End case 0xA000 */

			/* Begin case 0xB000 */
			case 0xB000:
				jumpNNNV0(); /* Bnnn */
				break;
			/* End case 0xB000 */

			/* Begin case 0xC000 */
			case 0xC000:
				randomVxKK(); /* Cxkk */
				break;
			/* End case 0xC000 */

			/* Begin case 0xD000 */
			case 0xD000:
				displayVxVyN(); /* Dxyn */
				break;
			/* End case 0xD000 */

			/* Begin case 0xE000 */
			case 0xE000:
				switch ( opcode & 0x00FF ) {
					case 0x009E:
						skipKeyPressed(); /* Ex9E */
						break;

					case 0x00A1:
						skipKeyReleased(); /* ExA1 */
						break;
					default:
						throw new Exception( "Unknown opcode: " + Integer.toHexString( opcode ) + " in [0xE000]" );
				}
				break;
			/* End case 0xE000 */

			/* Begin case 0xF000 */
			case 0xF000:
				switch ( opcode & 0x00FF ) {
					case 0x0007:
						loadVxDisplayTimer(); /* Fx07 */
						break;

					case 0x000A:
						loadKeyPress(); /* Fx0A */
						break;

					case 0x0015:
						loadDelayTimer(); /* Fx15 */
						break;

					case 0x0018:
						loadSoundTimer(); /* Fx18 */
						break;

					case 0x001E:
						addIVx(); /* Fx1E */
						break;

					case 0x0029:
						setFontLocationInI(); /* Fx29 */
						break;

					case 0x0033:
						setBCD(); /* Fx33 */
						break;

					case 0x0055:
						storeRegisters(); /* Fx55 */
						break;

					case 0x0065:
						loadRegisters(); /* Fx65 */
						break;

					default:
						throw new Exception( "Unknown opcode: " + Integer.toHexString( opcode ) + " in [0xF000]" );
				}
				break;
			/* End case 0xF000 */

			default:
				throw new Exception( "Unknown opcode: " + Integer.toHexString( opcode ) );
		}

	}

	short getNNN() {
		return (short) (opcode & 0x0FFF);
	}

	byte getKK() {
		return (byte) (opcode & 0x00FF);
	}

	byte getX() {
		return (byte) ((opcode & 0x0F00) >> 8);
	}

	byte getY() {
		return (byte) ((opcode & 0x00F0) >> 4);
	}

	byte getN() {
		return (byte) (opcode & 0x00F);
	}

	/*
	00E0 - Clear Screen
	Reset all pixels to 0, set drawFlag to true so we know that pixels were updated
	 */
	private void cls() {
		logger.log( Level.INFO, "Clear screen" );

		for ( int i = 0; i < pixels.length; i++ ) {
			pixels[i] = 0;
		}
		drawFlag = true;
		programCounter += 2;
	}

	/*
	00EE - Return from a subroutine
	We will set the program counter to the address at the top of the stack, then subtract 1 from the stack pointer
	 */
	private void ret() {
		logger.log( Level.INFO, "Return from subroutine" );


		programCounter = (short) (callStack[--stackPointer] & 0xFFFF);
	}

	/*
	1nnn - Jump to address
	We will set the program counter to address nnn, we will do this by masking the first bit in the opcode
	 */
	private void jmp() {
		logger.log( Level.INFO, "Jump to " + Integer.toHexString( getNNN() ) );

		programCounter = getNNN();
	}

	/*
	2nnn - Call address
	We will call the subroutine at address nnn. We will increment the stack pointer, put the current program counter on
	the top of the stack, and then set the program counter to nnn
	 */
	private void call() {
		logger.log( Level.INFO, "Call address " + Integer.toHexString( getNNN() ) );

		callStack[stackPointer++] = programCounter;
		programCounter = getNNN();
	}

	/*
	3xkk - SE Vx, kk
	We will skip the next opcode if Vx is equal to kk. If Vx == kk then increment the program counter by 4 (to skip the next
	opcode) otherwise we will increment the program counter by 2
	 */
	private void SEVxIsKK() {
		logger.log( Level.INFO, "Skip Vx is KK" );

		if ( vRegisters[(opcode & 0x0F00) >> 8] == (opcode & 0x00FF) ) {
			programCounter += 4;
		} else {
			programCounter += 2;
		}
	}

	/*
	4xkk - SNE Vx, kk
	We will skip the next opcode if Vx is not equal to kk. If Vx != kk then increment the program counter by 4 (to skip the next
	opcode) otherwise we will increment the program counter by 2
	 */
	private void SNEVxIsNotKK() {
		if ( vRegisters[getX()] != (getKK()) ) {
			programCounter += 4;
		} else {
			programCounter += 2;
		}
	}

	/*
	5xy0 - SE vx, vy
	We will skip the next opcode if Vx is equal to Vy. If Vx == Vy then increment the program counter by 4 (to skip the next
	opcode) otherwise we will increment the program counter by 2
	 */
	private void SEVxIsVy() {
		if ( vRegisters[getX()] == (vRegisters[getY()]) ) {
			programCounter += 4;
		} else {
			programCounter += 2;
		}
	}

	/*
	6xkk - LD Vx, kk
	Load the value kk into Vx
	 */
	private void loadKKToVx() {
		vRegisters[getX()] = getKK();
		programCounter += 2;
	}

	/*
	7xkk - Add Vx, kk
	Add kk to the value in Vx and store in Vx
	 */
	private void addVxKK() {
		// TODO: Figure out if we need to set the carry
		System.out.println( "Add Vx KK ----------------------------" );
		vRegisters[getX()] = (byte) (vRegisters[getX()] + getKK());
		programCounter += 2;
	}

	/*
	8xy0 - LD Vx, Vy
	Load the value Vy into the Vx register
	 */
	private void loadVxVy() {
		vRegisters[getX()] = vRegisters[getY()];
		programCounter += 2;
	}

	/*
	8xy1 - OR Vx, Vy
	Set Vx to the bitwise OR of the values of Vx and Vy
	 */
	private void orVxVy() {
		vRegisters[getX()] = (byte) (vRegisters[getX()] | vRegisters[getY()]);
		programCounter += 2;
	}

	/*
	8xy2 - AND Vx, Vy
	Set Vx to the bitwise AND of the values of Vx and Vy
	 */
	private void andVxVy() {
		vRegisters[getX()] = (byte) (vRegisters[getX()] & vRegisters[getY()]);
		programCounter += 2;
	}

	/*
	8xy3 - XOR Vx, Vy
	Set Vx to the bitwise XOR of the values of Vx and Vy
	 */
	private void xorVxVy() {
		vRegisters[getX()] = (byte) (vRegisters[getX()] ^ vRegisters[getY()]);
		programCounter += 2;
	}

	/*
	8xy4 - Add Vx, Vy
	Add Vx and Vy together. If the value is greater than a byte (>255) VF is set to 1, otherwise VF is set to 0. The lowest 8 bits are kept and stored in Vx.
	 */
	private void addVxVy() {
		int sum = vRegisters[getX()] + vRegisters[getY()];
		if ( sum > 255 ) {
			vRegisters[0x0F] = 1;
			sum -= 255;
		} else {
			vRegisters[0x0F] = 0;
		}
		vRegisters[getX()] = (byte) (sum);
		programCounter += 2;
	}

	/*
	8xy5 - SUB Vx, Vy
	If Vx > Vy then VF is set to 1, otherwise it is set to 0. Then Vy is subtracted from Vx and stored in Vx.
	*/
	private void subVxVy() {
		if ( vRegisters[getX()] > vRegisters[getY()] ) {
			vRegisters[0x0F] = 1;
		} else {
			vRegisters[0x0F] = 0;
		}
		vRegisters[getX()] = (byte) (vRegisters[getX()] - vRegisters[getY()]);
		programCounter += 2;
	}

	/*
	8xy6 - SHR Vx
	If the least significant bit is 1 then set VF to 1, otherwise set VF to 0. Then Vx is shifted right once (divided by two).
	*/
	private void shiftRightVx() {
		vRegisters[0xF] = (byte) (vRegisters[getX()] & 0x1); // Set based on LSB
		vRegisters[getX()] >>= 1;
		programCounter += 2;
	}

	/*
	8xy7 - SUBN Vx, Vy
	If Vy > Vx then set VF to 1, otherwise set to 0. Then Vx is subtracted from Vy and the results are stored in Vx.
	 */
	private void subnVxVy() {
		if ( vRegisters[getY()] > vRegisters[getX()] ) {
			vRegisters[0x0F] = 1;
		} else {
			vRegisters[0x0F] = 0;
		}
		vRegisters[getX()] = (byte) (vRegisters[getY()] - vRegisters[getX()]);
		programCounter += 2;
	}

	/*
	8xyE - SHL Vx
	If the most significant bit of Vx is 1 then set VF to 1, otherwise set to 0. Then shift Vx left once (multiply by two).
	 */
	private void shiftLeftVx() {
		vRegisters[0x0F] = (byte) (vRegisters[getX()] >> 7); // Set based on MSB
		vRegisters[getX()] <<= 1;
		programCounter += 2;
	}

	/*
	9xy0 - SNE Vx, Vy
	We will skip the next opcode if Vx is not equal to Vy. If Vx != Vy then increment the program counter by 4 (to skip the next
	opcode) otherwise we will increment the program counter by 2.
	 */
	private void SNEVxIsNotVy() {
		if ( vRegisters[getX()] != vRegisters[getY()] ) {
			programCounter += 4;
		} else {
			programCounter += 2;
		}
	}

	/*
	Annn - LD I, nnn
	Set register I to nnn
	 */
	private void loadINNN() {
		indexRegister = getNNN();
		programCounter += 2;
	}

	/*
	Bnnn - JP V0, nnn
	The program counter is set to nnn + V0
	 */
	private void jumpNNNV0() {
		programCounter = (short) (getNNN() + vRegisters[0]);
	}

	/*
	Cxkk - RND Vx, kk
	We will generate a random number between 0 and 255. We will then bitwise AND that random number with Vx and store that in Vx.
	 */
	private void randomVxKK() {
		vRegisters[getX()] = (byte) (random.nextInt( 256 ) & vRegisters[getX()]);
		programCounter += 2;
	}

	/*
	Dxyn - DRW Vx, Vy, nibble
	Display n-byte sprite starting at memory location I and screen coordinate (Vx, Vy), set VF if there is a collision.
	We will read in n bytes from memory, starting at the address stored in I. These bytes are then displayed as sprites on the screen
	at location (Vx, Vy). Sprites are XORed onto the existing screen. If this causes any pixels to disappear, VF is set to 1, otherwise
	it is set to 0. If the sprite is positioned so that some of it is outside of the display it wraps around to the other side of the screen.

	https://www.reddit.com/r/EmuDev/comments/5so1bo/chip8_emu_questions/
	We only set VF if any pixels go from 1 to 0.

	Remember that sprites are always 8 pixels wide
	 */
	private void displayVxVyN() {
		int x = getX();
		int y = getY();
		int spriteHeight = getN();

		vRegisters[0xF] = 0;

		for ( int yLine = 0; yLine < spriteHeight; yLine++ ) {
			int pixelValue = memory[indexRegister + yLine];
			for ( int xLine = 0; xLine < 8; xLine++ ) {
				if ( (pixelValue & (0x80 >> xLine)) != 0 ) {
					int pixelLocation = (x + xLine + ((y + yLine) * 64)) % 2048; // Ensure that we wrap around the screen

					vRegisters[0xF] |= pixels[pixelLocation] & 1;
					pixels[pixelLocation] ^= 1;
				}
			}
		}

		drawFlag = true;
		programCounter += 2;
	}

	/*
	Ex9E - SKP Vx
	Skip the next opcode if the key with the value of Vx is currently pressed.
	 */
	private void skipKeyPressed() {
		if ( keys[vRegisters[getX()]] != 0 ) {
			programCounter += 4;
		} else {
			programCounter += 2;
		}
	}

	/*
	ExA1 - SKNP Vx
	Skip the next opcode if the key with the value of Vx is currently NOT pressed.
	 */
	private void skipKeyReleased() {
		if ( keys[vRegisters[getX()]] == 0 ) {
			programCounter += 4;
		} else {
			programCounter += 2;
		}
	}

	/*
	Fx07 - LD Vx, Delay Timer
	The value of the delay timer is put into Vx
	 */
	private void loadVxDisplayTimer() {
		vRegisters[getX()] = (byte) delayTimer;
		programCounter += 2;
	}

	/*
	Fx0A - LD Vx, Key
	All execution STOPS until a key is pressed. Store the value of the key in Vx and then continue processing like normal.
	 */
	private void loadKeyPress() {
		for ( int i = 0; i < keys.length; i++ ) {
			if ( keys[i] != 0 ) {
				vRegisters[getX()] = keys[i];
				programCounter += 2;
			}
		}
	}

	/*
	Fx15 - LD Delay Timer, Vx
	Delay timer is set to the value of Vx
	 */
	private void loadDelayTimer() {
		delayTimer = vRegisters[getX()];
		programCounter += 2;
	}

	/*
	Fx18 - LD Sound Timer, Vx
	Sound timer is set to the value of Vx
	 */
	private void loadSoundTimer() {
		soundTimer = vRegisters[getX()];
		programCounter += 2;
	}

	/*
	Fx1E - Add I, Vx
	Add the values of I and Vx, store the results in I
	 */
	private void addIVx() {
		// TODO: Figure out if I need to set carry flag
		System.out.println( "Add I Vx ----------------------------" );

		indexRegister += vRegisters[getX()];
		programCounter += 2;
	}

	/*
	Fx29 - LD F, Vx
	The value of I is set to the location for the hexadecimal sprite corresponding to the value of Vx.
	 */
	private void setFontLocationInI() {
		indexRegister = (short) (vRegisters[getX()] * 5); // Multiply by 5 since we have 5 values for each font
		programCounter += 2;
	}

	/*
	Fx33 - LD B, Vx
	Store binary coded decimal  of Vx at address I, I + 1 and I + 2. I gets the hundreds digit, I + 1 gets the tens digit and I + 2 gets the ones digit.
	 */
	private void setBCD() {
		System.out.println( "Set binary coded decimal" );
		memory[indexRegister] = (byte) (vRegisters[getX()] / 100);
		memory[indexRegister + 1] = (byte) ((vRegisters[getX()] / 10) % 10);
		memory[indexRegister + 2] = (byte) (vRegisters[getX()] % 10);
		programCounter += 2;
	}

	/*
	Fx55 - LD I, Vx
	Store registers V0 through Vx in memory starting at location I
	 */
	private void storeRegisters() {
		for ( int registerIndex = 0; registerIndex <= getX(); registerIndex++ ) {
			memory[indexRegister + registerIndex] = vRegisters[registerIndex];
		}
		programCounter += 2;
	}

	/*
	Fx65 - LD Vx, I
	Read the memory values starting at I and load them into registers V0 through Vx
	 */
	private void loadRegisters() {
		for ( int registerIndex = 0; registerIndex <= getX(); registerIndex++ ) {
			vRegisters[registerIndex] = memory[indexRegister + registerIndex];
		}
		programCounter += 2;
	}

}
